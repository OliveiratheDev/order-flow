package com.start.overflow.payment.adapters.out.gateway;

import com.start.overflow.payment.domain.ChargeRequest;
import com.start.overflow.payment.domain.PaymentAmount;
import com.start.overflow.payment.domain.PaymentMethod;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentGatewayAdapterTest {

    @Test
    void fakeAdapterApprovesDeterministically() {
        FakePaymentGatewayAdapter adapter = new FakePaymentGatewayAdapter();
        ChargeRequest request = new ChargeRequest(10L, new PaymentAmount(BigDecimal.TEN),
                PaymentMethod.PIX, "correlation-1");

        var first = adapter.createCharge(request);
        var second = adapter.createCharge(request);

        assertThat(first.approved()).isTrue();
        assertThat(first.externalId()).isEqualTo(second.externalId());
    }

    @Test
    void declinedAdapterCanReplaceTheGatewayWithoutChangingTheDomain() {
        DeclinedPaymentGatewayAdapter adapter = new DeclinedPaymentGatewayAdapter();
        ChargeRequest request = new ChargeRequest(10L, new PaymentAmount(BigDecimal.TEN),
                PaymentMethod.PIX, "correlation-1");

        var result = adapter.createCharge(request);

        assertThat(result.approved()).isFalse();
        assertThat(result.externalId()).isEqualTo("declined-10");
    }
}
