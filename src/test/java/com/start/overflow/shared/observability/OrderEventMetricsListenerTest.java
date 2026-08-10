package com.start.overflow.shared.observability;

import com.start.overflow.order.event.OrderCreatedEvent;
import com.start.overflow.order.event.OrderItemSnapshot;
import com.start.overflow.order.event.OrderPaidEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OrderEventMetricsListenerTest {

    @Test
    void postCommitObserverMakesFailureVisibleWithoutPropagatingIt() {
        Counter paidCounter = mock(Counter.class);
        Counter failureCounter = mock(Counter.class);
        Counter ordersCreatedCounter = mock(Counter.class);
        doThrow(new IllegalStateException("metrics unavailable"))
                .when(paidCounter).increment();
        OrderEventMetricsListener listener = new OrderEventMetricsListener(
                Map.of("OrderPaid", paidCounter), failureCounter, ordersCreatedCounter);
        OrderPaidEvent event = new OrderPaidEvent(
                UUID.randomUUID(), 10L, 7L, BigDecimal.TEN,
                Instant.parse("2026-08-09T12:00:00Z"));

        assertThatCode(() -> listener.observe(event)).doesNotThrowAnyException();

        verify(failureCounter).increment();
    }

    @Test
    void createdOrderIncrementsBusinessMetricWithBoundedStatusTag() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OrderEventMetricsListener listener = new OrderEventMetricsListener(registry);
        OrderCreatedEvent event = new OrderCreatedEvent(
                UUID.randomUUID(), 10L, 7L, BigDecimal.TEN,
                List.of(new OrderItemSnapshot(
                        2L, "Produto", "SKU-1", 1, BigDecimal.TEN, BigDecimal.TEN)),
                Instant.parse("2026-08-09T12:00:00Z"));

        listener.observe(event);

        assertThatCode(() -> registry.get("orderflow.orders.created")
                .tag("status", "created")
                .counter())
                .doesNotThrowAnyException();
        assertThat(registry
                .get("orderflow.orders.created")
                .tag("status", "created")
                .counter()
                .count()).isEqualTo(1);
    }
}
