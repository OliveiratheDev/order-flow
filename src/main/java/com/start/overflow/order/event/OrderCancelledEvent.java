package com.start.overflow.order.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OrderCancelledEvent(
        UUID eventId,
        Long orderId,
        Long customerId,
        BigDecimal total,
        Instant occurredAt
) implements OrderDomainEvent {
    public OrderCancelledEvent {
        Objects.requireNonNull(eventId, "O identificador do evento é obrigatório");
        Objects.requireNonNull(orderId, "O pedido do evento é obrigatório");
        Objects.requireNonNull(customerId, "O cliente do evento é obrigatório");
        Objects.requireNonNull(total, "O total do evento é obrigatório");
        Objects.requireNonNull(occurredAt, "O instante do evento é obrigatório");
    }

    @Override
    public String eventType() {
        return "OrderCancelled";
    }
}
