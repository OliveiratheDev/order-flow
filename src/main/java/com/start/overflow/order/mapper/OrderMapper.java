package com.start.overflow.order.mapper;

import com.start.overflow.order.dto.OrderItemResponse;
import com.start.overflow.order.dto.OrderResponse;
import com.start.overflow.order.entity.CustomerOrder;
import com.start.overflow.order.entity.OrderItem;
import org.springframework.stereotype.Component;

@Component
public class OrderMapper {
    public OrderResponse toResponse(CustomerOrder order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomer().getId(),
                order.getCustomer().getEmail(),
                order.getStatus(),
                order.getSubtotal(),
                order.getDiscount(),
                order.getTotal(),
                order.getShippingAddress(),
                order.getItems().stream().map(this::toItemResponse).toList(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    private OrderItemResponse toItemResponse(OrderItem item) {
        return new OrderItemResponse(
                item.getProduct().getId(),
                item.getProductName(),
                item.getSku(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getLineTotal()
        );
    }
}
