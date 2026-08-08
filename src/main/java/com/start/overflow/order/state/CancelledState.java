package com.start.overflow.order.state;

import com.start.overflow.order.entity.OrderStatus;

final class CancelledState implements OrderState {
    static final CancelledState INSTANCE = new CancelledState();
    private CancelledState() { }
    public OrderStatus status() { return OrderStatus.CANCELLED; }
}
