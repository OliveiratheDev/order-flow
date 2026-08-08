package com.start.overflow.payment.ports.out;

import com.start.overflow.payment.domain.ChargeRequest;
import com.start.overflow.payment.domain.GatewayChargeResult;

public interface PaymentGatewayPort {
    GatewayChargeResult createCharge(ChargeRequest request);

    GatewayChargeResult getCharge(String externalId);
}
