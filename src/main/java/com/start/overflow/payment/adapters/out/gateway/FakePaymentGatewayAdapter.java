package com.start.overflow.payment.adapters.out.gateway;

import com.start.overflow.payment.domain.ChargeRequest;
import com.start.overflow.payment.domain.GatewayChargeResult;
import com.start.overflow.payment.ports.out.PaymentGatewayPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
@Profile("!payment-http & !payment-declined & !payment-asaas")
public class FakePaymentGatewayAdapter implements PaymentGatewayPort {

    @Override
    public GatewayChargeResult createCharge(ChargeRequest request) {
        String source = request.orderId() + ":" + request.method();
        String externalId = "fake-" + UUID.nameUUIDFromBytes(
                source.getBytes(StandardCharsets.UTF_8));
        return GatewayChargeResult.approved(externalId);
    }

    @Override
    public GatewayChargeResult getCharge(String externalId) {
        return GatewayChargeResult.approved(externalId);
    }

    @Override
    public void cancelCharge(String externalId) {
        // O adapter fake não mantém estado externo.
    }
}
