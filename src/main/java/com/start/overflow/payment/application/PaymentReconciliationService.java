package com.start.overflow.payment.application;

import com.start.overflow.payment.domain.GatewayChargeResult;
import com.start.overflow.payment.domain.Payment;
import com.start.overflow.payment.domain.PaymentStatus;
import com.start.overflow.payment.ports.out.PaymentGatewayPort;
import com.start.overflow.payment.ports.out.PaymentRepositoryPort;
import com.start.overflow.shared.observability.CorrelationIdContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class PaymentReconciliationService {
    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationService.class);
    private static final String METRIC_PREFIX = "orderflow.payment.reconciliation.";

    private final PaymentRepositoryPort paymentRepository;
    private final PaymentGatewayPort paymentGateway;
    private final PaymentReconciliationUpdater updater;
    private final PaymentReconciliationProperties properties;
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    private final Counter executions;
    private final Counter checked;
    private final Counter corrections;
    private final Counter divergences;
    private final Counter notFound;
    private final Counter errors;
    private final Timer duration;

    @Autowired
    public PaymentReconciliationService(
            PaymentRepositoryPort paymentRepository,
            PaymentGatewayPort paymentGateway,
            PaymentReconciliationUpdater updater,
            PaymentReconciliationProperties properties,
            MeterRegistry meterRegistry) {
        this(paymentRepository, paymentGateway, updater, properties,
                meterRegistry, Clock.systemUTC());
    }

    PaymentReconciliationService(
            PaymentRepositoryPort paymentRepository,
            PaymentGatewayPort paymentGateway,
            PaymentReconciliationUpdater updater,
            PaymentReconciliationProperties properties,
            MeterRegistry meterRegistry,
            Clock clock) {
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.updater = updater;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
        this.executions = meterRegistry.counter(METRIC_PREFIX + "executions");
        this.checked = meterRegistry.counter(METRIC_PREFIX + "checked");
        this.corrections = meterRegistry.counter(METRIC_PREFIX + "corrections");
        this.divergences = Counter.builder("orderflow.reconciliation.divergences")
                .tag("type", "charge_data")
                .register(meterRegistry);
        this.notFound = meterRegistry.counter(METRIC_PREFIX + "not_found");
        this.errors = meterRegistry.counter(METRIC_PREFIX + "errors");
        this.duration = meterRegistry.timer(METRIC_PREFIX + "duration");
    }

    public void reconcilePendingPayments() {
        executions.increment();
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            Instant now = clock.instant();
            List<Long> paymentIds = paymentRepository.findPendingIdsForReconciliation(
                    now.minus(properties.maxAge()),
                    now.minus(properties.minAge()),
                    properties.batchSize());
            log.atInfo()
                    .addKeyValue("candidateCount", paymentIds.size())
                    .addKeyValue("batchSize", properties.batchSize())
                    .addKeyValue("correlationId", CorrelationIdContext.currentOrCreate())
                    .log("Conciliação de pagamentos iniciada");
            paymentIds.forEach(this::reconcileOne);
        } finally {
            sample.stop(duration);
        }
    }

    private void reconcileOne(Long paymentId) {
        checked.increment();
        try {
            Optional<Payment> found = paymentRepository.findById(paymentId);
            if (found.isEmpty() || found.get().getStatus() != PaymentStatus.PENDING) {
                logResult(paymentId, found.map(Payment::getOrderId).orElse(null),
                        found.map(Payment::getStatus).orElse(null), null,
                        PaymentReconciliationAction.SKIPPED);
                return;
            }
            Payment payment = found.get();
            Optional<GatewayChargeResult> gatewayResult =
                    paymentGateway.findChargeForReconciliation(
                            payment.getOrderId(), payment.getExternalId());
            if (gatewayResult.isEmpty()) {
                notFound.increment();
                log.atWarn()
                        .addKeyValue("paymentId", payment.getId())
                        .addKeyValue("orderId", payment.getOrderId())
                        .addKeyValue("localStatus", payment.getStatus())
                        .addKeyValue("action", "NOT_FOUND")
                        .addKeyValue("correlationId", CorrelationIdContext.currentOrCreate())
                        .log("Cobrança não encontrada no gateway durante a conciliação");
                return;
            }

            GatewayChargeResult result = gatewayResult.get();
            PaymentReconciliationAction action = updater.apply(paymentId, result);
            if (action == PaymentReconciliationAction.APPROVED
                    || action == PaymentReconciliationAction.REJECTED) {
                corrections.increment();
            } else if (action == PaymentReconciliationAction.DIVERGENT) {
                divergences.increment();
            }
            logResult(payment.getId(), payment.getOrderId(), payment.getStatus(),
                    result.status(), action);
        } catch (RuntimeException exception) {
            errors.increment();
            log.atError()
                    .setCause(exception)
                    .addKeyValue("paymentId", paymentId)
                    .addKeyValue("action", "FAILED")
                    .addKeyValue("correlationId", CorrelationIdContext.currentOrCreate())
                    .log("Falha isolada ao conciliar pagamento");
        }
    }

    private void logResult(Long paymentId, Long orderId, PaymentStatus localStatus,
                           Object gatewayStatus, PaymentReconciliationAction action) {
        var event = action == PaymentReconciliationAction.DIVERGENT
                ? log.atWarn() : log.atInfo();
        event.addKeyValue("paymentId", paymentId)
                .addKeyValue("orderId", orderId)
                .addKeyValue("localStatus", localStatus)
                .addKeyValue("gatewayStatus", gatewayStatus)
                .addKeyValue("action", action)
                .addKeyValue("correlationId", CorrelationIdContext.currentOrCreate())
                .log("Resultado da conciliação de pagamento");
    }
}
