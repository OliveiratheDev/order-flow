package com.start.overflow.payment.ports.in;

import java.math.BigDecimal;

public interface PaymentWebhookUseCase {
    void confirm(WebhookPaymentCommand command);

    void refuse(WebhookPaymentCommand command);

    void refund(WebhookPaymentCommand command);

    record WebhookPaymentCommand(
            String externalId,
            BigDecimal amount,
            String externalReference
    ) {
    }
}
