package com.start.overflow.payment.adapters.in.webhook;

public final class WebhookProcessingException extends RuntimeException {
    public WebhookProcessingException(Throwable cause) {
        super("Falha interna ao processar o webhook", cause);
    }
}
