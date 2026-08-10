package com.start.overflow.payment.ports.out;

import com.start.overflow.payment.domain.ChargeRequest;
import com.start.overflow.payment.domain.GatewayChargeResult;

import java.util.Optional;

public interface PaymentGatewayPort {
    GatewayChargeResult createCharge(ChargeRequest request);

    GatewayChargeResult getCharge(String externalId);

    default Optional<GatewayChargeResult> findChargeForReconciliation(
            Long orderId, String externalId) {
        return externalId == null ? Optional.empty() : Optional.of(getCharge(externalId));
    }

    void cancelCharge(String externalId);
}
