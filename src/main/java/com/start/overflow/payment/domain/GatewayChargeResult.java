package com.start.overflow.payment.domain;

import java.math.BigDecimal;

public record GatewayChargeResult(
        String externalId,
        GatewayChargeStatus status,
        String rejectionReason,
        String paymentUrl,
        BigDecimal amount
) {
    public GatewayChargeResult {
        if (externalId == null || externalId.isBlank()) {
            throw new PaymentException("O gateway deve informar o identificador externo");
        }
        if (status == null) {
            throw new PaymentException("O gateway deve informar o estado da cobrança");
        }
        externalId = externalId.strip();
        rejectionReason = rejectionReason == null ? null : rejectionReason.strip();
        paymentUrl = paymentUrl == null || paymentUrl.isBlank() ? null : paymentUrl.strip();
        if (paymentUrl != null && paymentUrl.length() > 500) {
            throw new PaymentException("A URL de pagamento deve ter no máximo 500 caracteres");
        }
        amount = amount == null ? null : new PaymentAmount(amount).value();
    }

    public GatewayChargeResult(String externalId, GatewayChargeStatus status,
                               String rejectionReason, String paymentUrl) {
        this(externalId, status, rejectionReason, paymentUrl, null);
    }

    public static GatewayChargeResult approved(String externalId) {
        return new GatewayChargeResult(externalId, GatewayChargeStatus.APPROVED, null, null);
    }

    public static GatewayChargeResult rejected(String externalId, String reason) {
        return new GatewayChargeResult(externalId, GatewayChargeStatus.REJECTED, reason, null);
    }

    public static GatewayChargeResult pending(String externalId, String paymentUrl) {
        return new GatewayChargeResult(externalId, GatewayChargeStatus.PENDING, null, paymentUrl);
    }
}
