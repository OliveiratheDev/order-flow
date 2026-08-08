package com.start.overflow.order.state;

import com.start.overflow.order.entity.OrderStatus;

final class DeliveredState implements OrderState {
    static final DeliveredState INSTANCE = new DeliveredState();
    private DeliveredState() { }
    public OrderStatus status() { return OrderStatus.DELIVERED; }
}
