package com.start.overflow.order.event;

import java.time.Instant;
import java.util.UUID;

public sealed interface OrderDomainEvent
        permits OrderCreatedEvent, OrderPaidEvent, OrderCancelledEvent {
    UUID eventId();

    Long orderId();

    Instant occurredAt();

    String eventType();
}
