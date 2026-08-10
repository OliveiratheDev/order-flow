package com.start.overflow.order.adapter.out.messaging;

import java.math.BigDecimal;
import java.util.List;

public record OrderCreatedPayload(
        Long orderId,
        Long customerId,
        BigDecimal total,
        List<OrderItemPayload> items
) {
    public OrderCreatedPayload {
        items = List.copyOf(items);
    }
}
