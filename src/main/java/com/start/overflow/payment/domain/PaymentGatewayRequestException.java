package com.start.overflow.payment.domain;

public final class PaymentGatewayRequestException extends PaymentException {
    public PaymentGatewayRequestException(String message) {
        super(message);
    }

    public PaymentGatewayRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
