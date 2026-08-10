package com.start.overflow.order.state;

import com.start.overflow.order.entity.OrderStatus;
import com.start.overflow.shared.exception.InvalidTransitionException;

public interface OrderState {
    OrderStatus status();

    default OrderState awaitPayment() {
        throw invalid(OrderStatus.AWAITING_PAYMENT);
    }

    default OrderState pay() {
        throw invalid(OrderStatus.PAID);
    }

    default OrderState ship() {
        throw invalid(OrderStatus.SHIPPED);
    }

    default OrderState deliver() {
        throw invalid(OrderStatus.DELIVERED);
    }

    default OrderState cancel() {
        throw invalid(OrderStatus.CANCELLED);
    }

    private InvalidTransitionException invalid(OrderStatus target) {
        return new InvalidTransitionException(status().name(), target.name());
    }

    static OrderState from(OrderStatus status) {
        return switch (status) {
            case CREATED -> CreatedState.INSTANCE;
            case AWAITING_PAYMENT -> AwaitingPaymentState.INSTANCE;
            case PAID -> PaidState.INSTANCE;
            case SHIPPED -> ShippedState.INSTANCE;
            case DELIVERED -> DeliveredState.INSTANCE;
            case CANCELLED -> CancelledState.INSTANCE;
        };
    }
}
