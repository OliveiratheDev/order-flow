package com.start.overflow.order.event;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderDomainEventTest {

    @Test
    void createdEventCopiesItsItemSnapshotsAndHasStablePastTenseType() {
        List<OrderItemSnapshot> mutableItems = new ArrayList<>();
        mutableItems.add(item());

        OrderCreatedEvent event = new OrderCreatedEvent(
                UUID.randomUUID(), 10L, 7L, new BigDecimal("49.90"),
                mutableItems, Instant.parse("2026-08-09T12:00:00Z"));
        mutableItems.clear();

        assertThat(event.eventType()).isEqualTo("OrderCreated");
        assertThat(event.items()).containsExactly(item());
        assertThatThrownBy(() -> event.items().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void paidAndCancelledEventsUseVersionableStableTypes() {
        Instant occurredAt = Instant.parse("2026-08-09T12:00:00Z");

        OrderPaidEvent paid = new OrderPaidEvent(
                UUID.randomUUID(), 10L, 7L, BigDecimal.TEN, occurredAt);
        OrderCancelledEvent cancelled = new OrderCancelledEvent(
                UUID.randomUUID(), 10L, 7L, BigDecimal.TEN, occurredAt);

        assertThat(paid.eventType()).isEqualTo("OrderPaid");
        assertThat(cancelled.eventType()).isEqualTo("OrderCancelled");
    }

    private OrderItemSnapshot item() {
        return new OrderItemSnapshot(
                1L, "Produto", "SKU-1", 1, new BigDecimal("49.90"),
                new BigDecimal("49.90"));
    }
}
