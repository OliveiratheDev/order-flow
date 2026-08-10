package com.start.overflow.shared.observability;

import com.start.overflow.payment.domain.GatewayChargeResult;
import com.start.overflow.payment.domain.GatewayChargeStatus;
import com.start.overflow.payment.domain.PaymentGatewayRequestException;
import com.start.overflow.payment.domain.PaymentGatewayUnavailableException;
import com.start.overflow.payment.domain.PaymentRejectedException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Aspect
@Component
public class PaymentGatewayMetricsAspect {
    private static final String FAILURE_METRIC = "orderflow.payments.failed";
    private static final String DURATION_METRIC = "orderflow.payment.gateway.duration";

    private final MeterRegistry registry;
    private final Map<FailureKey, Counter> failures;
    private final Map<DurationKey, Timer> durations;

    public PaymentGatewayMetricsAspect(MeterRegistry registry) {
        this.registry = registry;
        this.failures = registerFailures(registry);
        this.durations = registerDurations(registry);
    }

    @Around("@within(observedGateway)")
    public Object measure(ProceedingJoinPoint invocation, ObservedPaymentGateway observedGateway)
            throws Throwable {
        PaymentGatewayName gateway = observedGateway.value();
        GatewayOperation operation = GatewayOperation.from(invocation.getSignature().getName());
        Timer.Sample sample = Timer.start(registry);
        try {
            Object result = invocation.proceed();
            if (result instanceof GatewayChargeResult charge
                    && charge.status() == GatewayChargeStatus.REJECTED) {
                incrementFailure(gateway, FailureReason.REJECTED);
            }
            return result;
        } catch (Throwable failure) {
            incrementFailure(gateway, FailureReason.from(failure));
            throw failure;
        } finally {
            sample.stop(durations.get(new DurationKey(gateway, operation)));
        }
    }

    private void incrementFailure(PaymentGatewayName gateway, FailureReason reason) {
        failures.get(new FailureKey(gateway, reason)).increment();
    }

    private Map<FailureKey, Counter> registerFailures(MeterRegistry meterRegistry) {
        Map<FailureKey, Counter> registered = new LinkedHashMap<>();
        for (PaymentGatewayName gateway : PaymentGatewayName.values()) {
            for (FailureReason reason : FailureReason.values()) {
                FailureKey key = new FailureKey(gateway, reason);
                registered.put(key, Counter.builder(FAILURE_METRIC)
                        .tag("gateway", gateway.metricValue())
                        .tag("reason", reason.metricValue)
                        .register(meterRegistry));
            }
        }
        return Map.copyOf(registered);
    }

    private Map<DurationKey, Timer> registerDurations(MeterRegistry meterRegistry) {
        Map<DurationKey, Timer> registered = new LinkedHashMap<>();
        for (PaymentGatewayName gateway : PaymentGatewayName.values()) {
            for (GatewayOperation operation : GatewayOperation.values()) {
                DurationKey key = new DurationKey(gateway, operation);
                registered.put(key, Timer.builder(DURATION_METRIC)
                        .tag("gateway", gateway.metricValue())
                        .tag("operation", operation.metricValue)
                        .publishPercentileHistogram()
                        .serviceLevelObjectives(
                                Duration.ofMillis(100),
                                Duration.ofMillis(300),
                                Duration.ofSeconds(1),
                                Duration.ofSeconds(3))
                        .minimumExpectedValue(Duration.ofMillis(1))
                        .maximumExpectedValue(Duration.ofSeconds(30))
                        .register(meterRegistry));
            }
        }
        return Map.copyOf(registered);
    }

    private enum GatewayOperation {
        CREATE("create"),
        QUERY("query"),
        CANCEL("cancel");

        private final String metricValue;

        GatewayOperation(String metricValue) {
            this.metricValue = metricValue;
        }

        private static GatewayOperation from(String method) {
            return switch (method) {
                case "createCharge" -> CREATE;
                case "getCharge", "findChargeForReconciliation" -> QUERY;
                case "cancelCharge" -> CANCEL;
                default -> throw new IllegalArgumentException(
                        "Operação de gateway não observável: " + method);
            };
        }
    }

    private enum FailureReason {
        REJECTED("rejected"),
        INVALID_REQUEST("invalid_request"),
        TIMEOUT("timeout"),
        CIRCUIT_OPEN("circuit_open"),
        UNAVAILABLE("unavailable"),
        UNEXPECTED("unexpected");

        private final String metricValue;

        FailureReason(String metricValue) {
            this.metricValue = metricValue;
        }

        private static FailureReason from(Throwable failure) {
            Throwable current = failure;
            while (current != null) {
                if (current instanceof PaymentRejectedException) {
                    return REJECTED;
                }
                if (current instanceof PaymentGatewayRequestException) {
                    return INVALID_REQUEST;
                }
                if (current instanceof TimeoutException) {
                    return TIMEOUT;
                }
                if (current instanceof CallNotPermittedException) {
                    return CIRCUIT_OPEN;
                }
                current = current.getCause();
            }
            return failure instanceof PaymentGatewayUnavailableException
                    ? UNAVAILABLE : UNEXPECTED;
        }
    }

    private record FailureKey(PaymentGatewayName gateway, FailureReason reason) {
    }

    private record DurationKey(
            PaymentGatewayName gateway,
            GatewayOperation operation
    ) {
    }
}
