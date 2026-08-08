package com.start.overflow.payment.adapters.out.gateway;

import com.start.overflow.payment.domain.ChargeRequest;
import com.start.overflow.payment.domain.GatewayChargeResult;
import com.start.overflow.payment.ports.out.PaymentGatewayPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("payment-declined & !payment-http")
public class DeclinedPaymentGatewayAdapter implements PaymentGatewayPort {

    @Override
    public GatewayChargeResult createCharge(ChargeRequest request) {
        return new GatewayChargeResult("declined-" + request.orderId(), false,
                "Cobrança recusada pelo simulador");
    }

    @Override
    public GatewayChargeResult getCharge(String externalId) {
        return new GatewayChargeResult(externalId, false,
                "Cobrança recusada pelo simulador");
    }
}
