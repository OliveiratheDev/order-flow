package com.start.overflow.shared.exception;

public class IdempotencyUnavailableException extends DomainException {
    public IdempotencyUnavailableException(String message) {
        super(message);
    }

    public IdempotencyUnavailableException(String message, Throwable cause) {
        super(message, true, cause);
    }
}
