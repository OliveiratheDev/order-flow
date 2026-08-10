package com.start.overflow.order.state;

import com.start.overflow.order.entity.OrderStatus;

final class AwaitingPaymentState implements OrderState {
    static final AwaitingPaymentState INSTANCE = new AwaitingPaymentState();
    private AwaitingPaymentState() { }
    public OrderStatus status() { return OrderStatus.AWAITING_PAYMENT; }
    public OrderState pay() { return PaidState.INSTANCE; }
    public OrderState cancel() { return CancelledState.INSTANCE; }
}
