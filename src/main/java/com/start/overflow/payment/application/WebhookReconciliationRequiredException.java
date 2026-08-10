package com.start.overflow.payment.application;

public final class WebhookReconciliationRequiredException extends RuntimeException {
    public WebhookReconciliationRequiredException(String message) {
        super(message);
    }
}
