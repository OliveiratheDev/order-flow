package com.start.overflow.payment.adapters.in.webhook;

public record WebhookHandlingResult(
        String eventId,
        Status status
) {
    public enum Status {
        PROCESSED,
        DUPLICATE,
        RECONCILIATION_REQUIRED
    }
}
