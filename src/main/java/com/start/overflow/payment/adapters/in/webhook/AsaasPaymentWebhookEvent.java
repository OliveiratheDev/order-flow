package com.start.overflow.payment.adapters.in.webhook;

import com.start.overflow.payment.ports.in.PaymentWebhookUseCase;

import java.math.BigDecimal;

public record AsaasPaymentWebhookEvent(
        String eventId,
        String eventType,
        String paymentExternalId,
        BigDecimal amount,
        String externalReference
) {
    public PaymentWebhookUseCase.WebhookPaymentCommand toCommand() {
        return new PaymentWebhookUseCase.WebhookPaymentCommand(
                paymentExternalId, amount, externalReference);
    }
}
