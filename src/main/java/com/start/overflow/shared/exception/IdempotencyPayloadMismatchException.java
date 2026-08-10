package com.start.overflow.shared.exception;

public class IdempotencyPayloadMismatchException extends DomainException {
    public IdempotencyPayloadMismatchException() {
        super("Idempotency-Key já utilizada com outro conteúdo");
    }
}
