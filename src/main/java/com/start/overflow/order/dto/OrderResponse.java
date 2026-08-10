package com.start.overflow.order.dto;

import com.start.overflow.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        Long customerId,
        String customerEmail,
        OrderStatus status,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal total,
        String shippingAddress,
        List<OrderItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {
}
