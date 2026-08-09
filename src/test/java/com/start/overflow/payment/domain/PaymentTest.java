package com.start.overflow.payment.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    @Test
    void startsPendingWithAmountNormalizedByTheDomain() {
        Payment payment = Payment.create(10L, 20L, new BigDecimal("149.999"),
                PaymentMethod.CREDIT_CARD);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getAmount()).isEqualByComparingTo("150.00");
        assertThat(payment.getExternalId()).isNull();
    }

    @Test
    void approvesUsingTheExternalGatewayResult() {
        Payment payment = Payment.create(10L, 20L, BigDecimal.TEN, PaymentMethod.PIX);

        payment.completeCharge(GatewayChargeResult.approved("gateway-123"));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getExternalId()).isEqualTo("gateway-123");
    }

    @Test
    void rejectsUsingTheExternalGatewayResult() {
        Payment payment = Payment.create(10L, 20L, BigDecimal.TEN, PaymentMethod.BOLETO);

        payment.completeCharge(GatewayChargeResult.rejected("gateway-456", "Sem limite"));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REJECTED);
    }

    @Test
    void finalPaymentCannotReceiveAnotherGatewayResult() {
        Payment payment = Payment.create(10L, 20L, BigDecimal.TEN, PaymentMethod.PIX);
        payment.completeCharge(GatewayChargeResult.approved("gateway-123"));

        assertThatThrownBy(() -> payment.completeCharge(
                GatewayChargeResult.rejected("gateway-456", "Recusado")))
                .isInstanceOf(PaymentException.class);
    }

    @Test
    void pendingGatewayResultKeepsPaymentPendingWithPaymentUrl() {
        Payment payment = Payment.create(10L, 20L, BigDecimal.TEN, PaymentMethod.PIX);

        payment.completeCharge(GatewayChargeResult.pending(
                "pay_123", "https://sandbox.asaas.com/i/123"));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getPaymentUrl()).isEqualTo("https://sandbox.asaas.com/i/123");
    }

    @Test
    void approvedPaymentCanBeRefunded() {
        Payment payment = Payment.create(10L, 20L, BigDecimal.TEN, PaymentMethod.PIX);
        payment.approve();

        payment.refund();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void rejectsNonPositiveAmount() {
        assertThatThrownBy(() -> Payment.create(10L, 20L, BigDecimal.ZERO,
                PaymentMethod.PIX)).isInstanceOf(PaymentException.class);
    }
}
