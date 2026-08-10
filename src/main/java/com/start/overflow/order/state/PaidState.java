package com.start.overflow.order.state;

import com.start.overflow.order.entity.OrderStatus;

final class PaidState implements OrderState {
    static final PaidState INSTANCE = new PaidState();
    private PaidState() { }
    public OrderStatus status() { return OrderStatus.PAID; }
    public OrderState ship() { return ShippedState.INSTANCE; }
    public OrderState cancel() { return CancelledState.INSTANCE; }
}
