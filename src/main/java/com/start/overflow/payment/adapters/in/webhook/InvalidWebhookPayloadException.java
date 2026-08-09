package com.start.overflow.payment.adapters.in.webhook;

public final class InvalidWebhookPayloadException extends RuntimeException {
    public InvalidWebhookPayloadException(String message) {
        super(message);
    }

    public InvalidWebhookPayloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
