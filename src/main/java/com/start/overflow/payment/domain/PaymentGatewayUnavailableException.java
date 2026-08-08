package com.start.overflow.payment.domain;

public final class PaymentGatewayUnavailableException extends PaymentException {
    public PaymentGatewayUnavailableException(String message) {
        super(message);
    }

    public PaymentGatewayUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
