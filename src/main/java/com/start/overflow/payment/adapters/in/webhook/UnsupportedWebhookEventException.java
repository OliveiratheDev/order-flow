package com.start.overflow.payment.adapters.in.webhook;

public final class UnsupportedWebhookEventException extends RuntimeException {
    public UnsupportedWebhookEventException() {
        super("Tipo de evento não suportado por este webhook");
    }
}
