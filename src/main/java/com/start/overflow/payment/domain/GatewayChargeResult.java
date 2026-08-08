package com.start.overflow.payment.domain;

public record GatewayChargeResult(
        String externalId,
        boolean approved,
        String rejectionReason
) {
    public GatewayChargeResult {
        if (externalId == null || externalId.isBlank()) {
            throw new PaymentException("O gateway deve informar o identificador externo");
        }
        externalId = externalId.strip();
        rejectionReason = rejectionReason == null ? null : rejectionReason.strip();
    }
}
