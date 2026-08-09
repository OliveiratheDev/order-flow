package com.start.overflow.shared.observability;

import com.start.overflow.order.event.OrderPaidEvent;
import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OrderEventMetricsListenerTest {

    @Test
    void postCommitObserverMakesFailureVisibleWithoutPropagatingIt() {
        Counter paidCounter = mock(Counter.class);
        Counter failureCounter = mock(Counter.class);
        doThrow(new IllegalStateException("metrics unavailable"))
                .when(paidCounter).increment();
        OrderEventMetricsListener listener = new OrderEventMetricsListener(
                Map.of("OrderPaid", paidCounter), failureCounter);
        OrderPaidEvent event = new OrderPaidEvent(
                UUID.randomUUID(), 10L, 7L, BigDecimal.TEN,
                Instant.parse("2026-08-09T12:00:00Z"));

        assertThatCode(() -> listener.observe(event)).doesNotThrowAnyException();

        verify(failureCounter).increment();
    }
}
