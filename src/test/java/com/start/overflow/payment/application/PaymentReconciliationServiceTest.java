package com.start.overflow.payment.application;

import com.start.overflow.payment.domain.GatewayChargeResult;
import com.start.overflow.payment.domain.GatewayChargeStatus;
import com.start.overflow.payment.domain.Payment;
import com.start.overflow.payment.domain.PaymentGatewayUnavailableException;
import com.start.overflow.payment.domain.PaymentMethod;
import com.start.overflow.payment.domain.PaymentStatus;
import com.start.overflow.payment.ports.out.PaymentGatewayPort;
import com.start.overflow.payment.ports.out.PaymentRepositoryPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentReconciliationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");
    @Mock PaymentRepositoryPort paymentRepository;
    @Mock PaymentGatewayPort paymentGateway;
    @Mock PaymentReconciliationUpdater updater;
    private SimpleMeterRegistry meterRegistry;
    private PaymentReconciliationService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        PaymentReconciliationProperties properties = new PaymentReconciliationProperties(
                "0 */15 * * * *", Duration.ofMinutes(10), Duration.ofDays(7),
                200, Duration.ofMinutes(15), Duration.ofMinutes(1));
        service = new PaymentReconciliationService(
                paymentRepository, paymentGateway, updater, properties, meterRegistry,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void selectsOnlyConfiguredAgeWindowAndBatchSize() {
        when(paymentRepository.findPendingIdsForReconciliation(
                NOW.minus(Duration.ofDays(7)), NOW.minus(Duration.ofMinutes(10)), 200))
                .thenReturn(List.of());

        service.reconcilePendingPayments();

        verify(paymentRepository).findPendingIdsForReconciliation(
                NOW.minus(Duration.ofDays(7)), NOW.minus(Duration.ofMinutes(10)), 200);
        assertThat(metric("executions")).isEqualTo(1);
        assertThat(metric("checked")).isZero();
    }

    @Test
    void missingGatewayChargeRaisesMetricAndKeepsLocalPaymentPending() {
        Payment payment = pending(30L, 10L);
        when(paymentRepository.findPendingIdsForReconciliation(
                NOW.minus(Duration.ofDays(7)), NOW.minus(Duration.ofMinutes(10)), 200))
                .thenReturn(List.of(30L));
        when(paymentRepository.findById(30L)).thenReturn(Optional.of(payment));
        when(paymentGateway.findChargeForReconciliation(10L, "pay_30"))
                .thenReturn(Optional.empty());

        service.reconcilePendingPayments();

        verify(updater, never()).apply(30L, null);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(metric("not_found")).isEqualTo(1);
    }

    @Test
    void oneGatewayFailureDoesNotInterruptRemainingBatch() {
        Payment first = pending(30L, 10L);
        Payment second = pending(31L, 11L);
        GatewayChargeResult approved = new GatewayChargeResult(
                "pay_31", GatewayChargeStatus.APPROVED, null, null,
                new BigDecimal("100.00"));
        when(paymentRepository.findPendingIdsForReconciliation(
                NOW.minus(Duration.ofDays(7)), NOW.minus(Duration.ofMinutes(10)), 200))
                .thenReturn(List.of(30L, 31L));
        when(paymentRepository.findById(30L)).thenReturn(Optional.of(first));
        when(paymentRepository.findById(31L)).thenReturn(Optional.of(second));
        when(paymentGateway.findChargeForReconciliation(10L, "pay_30"))
                .thenThrow(new PaymentGatewayUnavailableException("indisponível"));
        when(paymentGateway.findChargeForReconciliation(11L, "pay_31"))
                .thenReturn(Optional.of(approved));
        when(updater.apply(31L, approved)).thenReturn(PaymentReconciliationAction.APPROVED);

        service.reconcilePendingPayments();

        verify(paymentGateway).findChargeForReconciliation(11L, "pay_31");
        verify(updater).apply(31L, approved);
        assertThat(metric("errors")).isEqualTo(1);
        assertThat(metric("corrections")).isEqualTo(1);
        assertThat(metric("checked")).isEqualTo(2);
    }

    @Test
    void recordsDivergenceMetricReturnedByTransactionalUpdater() {
        Payment payment = pending(30L, 10L);
        GatewayChargeResult divergent = new GatewayChargeResult(
                "pay_30", GatewayChargeStatus.APPROVED, null, null,
                new BigDecimal("999.00"));
        when(paymentRepository.findPendingIdsForReconciliation(
                NOW.minus(Duration.ofDays(7)), NOW.minus(Duration.ofMinutes(10)), 200))
                .thenReturn(List.of(30L));
        when(paymentRepository.findById(30L)).thenReturn(Optional.of(payment));
        when(paymentGateway.findChargeForReconciliation(10L, "pay_30"))
                .thenReturn(Optional.of(divergent));
        when(updater.apply(30L, divergent))
                .thenReturn(PaymentReconciliationAction.DIVERGENT);

        service.reconcilePendingPayments();

        assertThat(meterRegistry.counter(
                "orderflow.reconciliation.divergences", "type", "charge_data").count())
                .isEqualTo(1);
        assertThat(metric("corrections")).isZero();
    }

    private Payment pending(Long paymentId, Long orderId) {
        return Payment.restore(paymentId, orderId, 7L, new BigDecimal("100.00"),
                PaymentMethod.PIX, "pay_" + paymentId, null, PaymentStatus.PENDING,
                NOW.minus(Duration.ofHours(1)), NOW.minus(Duration.ofHours(1)));
    }

    private double metric(String suffix) {
        return meterRegistry.counter("orderflow.payment.reconciliation." + suffix).count();
    }
}
