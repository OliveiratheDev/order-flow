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

        payment.completeCharge(new GatewayChargeResult("gateway-123", true, null));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getExternalId()).isEqualTo("gateway-123");
    }

    @Test
    void rejectsUsingTheExternalGatewayResult() {
        Payment payment = Payment.create(10L, 20L, BigDecimal.TEN, PaymentMethod.BOLETO);

        payment.completeCharge(new GatewayChargeResult("gateway-456", false, "Sem limite"));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REJECTED);
    }

    @Test
    void finalPaymentCannotReceiveAnotherGatewayResult() {
        Payment payment = Payment.create(10L, 20L, BigDecimal.TEN, PaymentMethod.PIX);
        payment.completeCharge(new GatewayChargeResult("gateway-123", true, null));

        assertThatThrownBy(() -> payment.completeCharge(
                new GatewayChargeResult("gateway-456", false, "Recusado")))
                .isInstanceOf(PaymentException.class);
    }

    @Test
    void rejectsNonPositiveAmount() {
        assertThatThrownBy(() -> Payment.create(10L, 20L, BigDecimal.ZERO,
                PaymentMethod.PIX)).isInstanceOf(PaymentException.class);
    }
}
