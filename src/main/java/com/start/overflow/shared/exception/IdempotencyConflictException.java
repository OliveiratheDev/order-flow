package com.start.overflow.shared.exception;

public class IdempotencyConflictException extends DomainException {
    public IdempotencyConflictException() {
        super("Requisição em processamento");
    }
}
