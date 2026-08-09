package com.start.overflow.shared.exception;

public class IdempotencyKeyRequiredException extends DomainException {
    public IdempotencyKeyRequiredException() {
        this("O header Idempotency-Key é obrigatório");
    }

    public IdempotencyKeyRequiredException(String message) {
        super(message);
    }
}
