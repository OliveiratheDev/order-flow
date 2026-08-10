package com.start.overflow.payment.adapters.out.gateway;

import com.start.overflow.payment.domain.ChargeRequest;
import com.start.overflow.payment.domain.GatewayChargeResult;
import com.start.overflow.payment.ports.out.PaymentGatewayPort;
import com.start.overflow.shared.observability.ObservedPaymentGateway;
import com.start.overflow.shared.observability.PaymentGatewayName;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("payment-declined & !payment-http & !payment-asaas")
@ObservedPaymentGateway(PaymentGatewayName.DECLINED)
public class DeclinedPaymentGatewayAdapter implements PaymentGatewayPort {

    @Override
    public GatewayChargeResult createCharge(ChargeRequest request) {
        return GatewayChargeResult.rejected("declined-" + request.orderId(),
                "Cobrança recusada pelo simulador");
    }

    @Override
    public GatewayChargeResult getCharge(String externalId) {
        return GatewayChargeResult.rejected(externalId,
                "Cobrança recusada pelo simulador");
    }

    @Override
    public void cancelCharge(String externalId) {
    }
}
