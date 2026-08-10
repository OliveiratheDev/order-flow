package com.start.overflow.order.state;

import com.start.overflow.order.entity.OrderStatus;

final class ShippedState implements OrderState {
    static final ShippedState INSTANCE = new ShippedState();
    private ShippedState() { }
    public OrderStatus status() { return OrderStatus.SHIPPED; }
    public OrderState deliver() { return DeliveredState.INSTANCE; }
}
