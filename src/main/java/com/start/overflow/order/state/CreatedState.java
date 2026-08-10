package com.start.overflow.order.state;

import com.start.overflow.order.entity.OrderStatus;

final class CreatedState implements OrderState {
    static final CreatedState INSTANCE = new CreatedState();
    private CreatedState() { }
    public OrderStatus status() { return OrderStatus.CREATED; }
    public OrderState awaitPayment() { return AwaitingPaymentState.INSTANCE; }
    public OrderState cancel() { return CancelledState.INSTANCE; }
}
