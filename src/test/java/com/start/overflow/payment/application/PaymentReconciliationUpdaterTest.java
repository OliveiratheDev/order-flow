package com.start.overflow.payment.application;

import com.start.overflow.payment.domain.GatewayChargeResult;
import com.start.overflow.payment.domain.GatewayChargeStatus;
import com.start.overflow.payment.domain.Payment;
import com.start.overflow.payment.domain.PaymentMethod;
import com.start.overflow.payment.domain.PaymentStatus;
import com.start.overflow.payment.ports.out.OrderPaymentPort;
import com.start.overflow.payment.ports.out.PaymentRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentReconciliationUpdaterTest {
    @Mock PaymentRepositoryPort paymentRepository;
    @Mock OrderPaymentPort orderPaymentPort;
    private PaymentReconciliationUpdater updater;

    @BeforeEach
    void setUp() {
        updater = new PaymentReconciliationUpdater(paymentRepository, orderPaymentPort);
    }

    @Test
    void approvesPaymentAndOrderWhenAmountsMatch() {
        Payment payment = pending("pay_123");
        when(paymentRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(payment));

        PaymentReconciliationAction action = updater.apply(30L,
                gateway(GatewayChargeStatus.APPROVED, "100.00", "pay_123"));

        assertThat(action).isEqualTo(PaymentReconciliationAction.APPROVED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        verify(orderPaymentPort).markOrderPaid(10L);
        verify(paymentRepository).save(payment);
    }

    @Test
    void rejectsPaymentCancelsOrderAndRestoresStock() {
        Payment payment = pending("pay_123");
        when(paymentRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(payment));

        PaymentReconciliationAction action = updater.apply(30L,
                gateway(GatewayChargeStatus.REJECTED, "100.00", "pay_123"));

        assertThat(action).isEqualTo(PaymentReconciliationAction.REJECTED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REJECTED);
        verify(orderPaymentPort).cancelOrderAndRestoreStock(10L);
        verify(paymentRepository).save(payment);
    }

    @Test
    void attachesExternalIdButKeepsPendingState() {
        Payment payment = pending(null);
        when(paymentRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(payment));

        PaymentReconciliationAction action = updater.apply(30L,
                gateway(GatewayChargeStatus.PENDING, "100.00", "pay_found"));

        assertThat(action).isEqualTo(PaymentReconciliationAction.PENDING_UPDATED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getExternalId()).isEqualTo("pay_found");
        verify(orderPaymentPort, never()).markOrderPaid(any());
        verify(orderPaymentPort, never()).cancelOrderAndRestoreStock(any());
    }

    @Test
    void marksAmountDivergenceWithoutChangingOrder() {
        Payment payment = pending("pay_123");
        when(paymentRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(payment));

        PaymentReconciliationAction action = updater.apply(30L,
                gateway(GatewayChargeStatus.APPROVED, "999.00", "pay_123"));

        assertThat(action).isEqualTo(PaymentReconciliationAction.DIVERGENT);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DIVERGENT);
        verify(orderPaymentPort, never()).markOrderPaid(any());
        verify(orderPaymentPort, never()).cancelOrderAndRestoreStock(any());
        verify(paymentRepository).save(payment);
    }

    @Test
    void skipsPaymentAlreadyProcessedByConcurrentWebhook() {
        Payment payment = restored(PaymentStatus.APPROVED, "pay_123");
        when(paymentRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(payment));

        PaymentReconciliationAction action = updater.apply(30L,
                gateway(GatewayChargeStatus.APPROVED, "100.00", "pay_123"));

        assertThat(action).isEqualTo(PaymentReconciliationAction.SKIPPED);
        verify(paymentRepository, never()).save(any());
        verify(orderPaymentPort, never()).markOrderPaid(any());
    }

    private Payment pending(String externalId) {
        return restored(PaymentStatus.PENDING, externalId);
    }

    private Payment restored(PaymentStatus status, String externalId) {
        Instant instant = Instant.parse("2026-08-09T12:00:00Z");
        return Payment.restore(30L, 10L, 7L, new BigDecimal("100.00"),
                PaymentMethod.PIX, externalId, null, status, instant, instant);
    }

    private GatewayChargeResult gateway(
            GatewayChargeStatus status, String amount, String externalId) {
        return new GatewayChargeResult(
                externalId, status, null, "https://sandbox.asaas.com/i/test",
                new BigDecimal(amount));
    }
}
