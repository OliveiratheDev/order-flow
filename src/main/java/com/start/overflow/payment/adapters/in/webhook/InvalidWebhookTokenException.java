package com.start.overflow.payment.adapters.in.webhook;

public final class InvalidWebhookTokenException extends RuntimeException {
    public InvalidWebhookTokenException() {
        super("Token de autenticação do webhook inválido");
    }
}
