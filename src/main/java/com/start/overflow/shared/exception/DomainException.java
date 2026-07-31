package com.start.overflow.shared.exception;

public class DomainException extends RuntimeException {
    public DomainException(String message) {
       super(message);

    }
    public DomainException(String message, boolean detailMessage, Throwable cause) {
        super(message, cause);
    }
}
