package com.start.overflow.shared.exception;

public class InvalidTransitionException extends DomainException {
    public InvalidTransitionException(String currentState, String targetState) {
        super("Não é possível alterar o pedido de " + currentState + " para " + targetState);
    }
}
