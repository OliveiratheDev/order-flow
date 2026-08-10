package com.start.overflow.payment.domain;

public final class PaymentRejectedException extends PaymentException {
    public PaymentRejectedException(String message) {
        super(message);
    }
}
