package com.start.overflow.order.adapter.out.messaging;

import com.start.overflow.order.event.OrderCancelledEvent;
import com.start.overflow.order.event.OrderCreatedEvent;
import com.start.overflow.order.event.OrderItemSnapshot;
import com.start.overflow.order.event.OrderPaidEvent;
import com.start.overflow.shared.messaging.EventEnvelope;
import com.start.overflow.shared.observability.CorrelationIdContext;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RabbitOrderEventPublisherTest {
    private RabbitTemplate rabbitTemplate;
    private SimpleMeterRegistry meterRegistry;
    private RabbitOrderEventPublisher publisher;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        meterRegistry = new SimpleMeterRegistry();
        publisher = new RabbitOrderEventPublisher(
                rabbitTemplate, meterRegistry, Duration.ofSeconds(1));
        MDC.put(CorrelationIdContext.MDC_KEY, "correlation-123");
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void publishesAllOrderEventsWithVersionedEnvelopeAndPersistentHeaders() {
        acknowledgePublications();
        Instant occurredAt = Instant.parse("2026-08-09T20:00:00Z");
        OrderCreatedEvent created = new OrderCreatedEvent(
                UUID.randomUUID(), 42L, 7L, new BigDecimal("249.90"),
                List.of(new OrderItemSnapshot(
                        10L, "Produto", "SKU-10", 2,
                        new BigDecimal("124.95"), new BigDecimal("249.90"))),
                occurredAt);
        OrderPaidEvent paid = new OrderPaidEvent(
                UUID.randomUUID(), 42L, 7L, new BigDecimal("249.90"), occurredAt);
        OrderCancelledEvent cancelled = new OrderCancelledEvent(
                UUID.randomUUID(), 43L, 8L, new BigDecimal("10.00"), occurredAt);

        publisher.onCreated(created);
        publisher.onPaid(paid);
        publisher.onCancelled(cancelled);

        var routingKeys = org.mockito.ArgumentCaptor.forClass(String.class);
        var envelopes = org.mockito.ArgumentCaptor.forClass(Object.class);
        var processors = org.mockito.ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate, times(3)).convertAndSend(
                eq("order.events"), routingKeys.capture(), envelopes.capture(),
                processors.capture(), any(CorrelationData.class));

        assertThat(routingKeys.getAllValues())
                .containsExactly("order.created", "order.paid", "order.cancelled");
        EventEnvelope<?> createdEnvelope = (EventEnvelope<?>) envelopes.getAllValues().getFirst();
        assertThat(createdEnvelope.eventId()).isEqualTo(created.eventId());
        assertThat(createdEnvelope.eventType()).isEqualTo("OrderCreated");
        assertThat(createdEnvelope.eventVersion()).isEqualTo(1);
        assertThat(createdEnvelope.correlationId()).isEqualTo("correlation-123");
        assertThat(createdEnvelope.payload()).isInstanceOf(OrderCreatedPayload.class);

        Message message = processors.getAllValues().getFirst().postProcessMessage(
                new Message(new byte[0], new MessageProperties()));
        assertThat(message.getMessageProperties().getMessageId())
                .isEqualTo(created.eventId().toString());
        assertThat(message.getMessageProperties().getDeliveryMode())
                .isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat((Object) message.getMessageProperties().getHeader("eventVersion"))
                .isEqualTo(1);
        assertThat(metric("OrderCreated", "success")).isEqualTo(1);
        assertThat(metric("OrderPaid", "success")).isEqualTo(1);
        assertThat(metric("OrderCancelled", "success")).isEqualTo(1);
    }

    @Test
    void brokerFailureIsVisibleWithoutPropagatingAfterCommit() {
        doThrow(new AmqpException("broker offline"))
                .when(rabbitTemplate).convertAndSend(
                        anyString(), anyString(), any(), any(MessagePostProcessor.class),
                        any(CorrelationData.class));
        OrderPaidEvent event = new OrderPaidEvent(
                UUID.randomUUID(), 42L, 7L, new BigDecimal("249.90"), Instant.now());

        assertThatCode(() -> publisher.onPaid(event)).doesNotThrowAnyException();

        assertThat(metric("OrderPaid", "failure")).isEqualTo(1);
        assertThat(metric("OrderPaid", "success")).isZero();
    }

    @Test
    void negativePublisherConfirmIsRecordedAsFailure() {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(4);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "nack"));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                anyString(), anyString(), any(), any(MessagePostProcessor.class),
                any(CorrelationData.class));
        OrderCancelledEvent event = new OrderCancelledEvent(
                UUID.randomUUID(), 42L, 7L, BigDecimal.TEN, Instant.now());

        assertThatCode(() -> publisher.onCancelled(event)).doesNotThrowAnyException();

        assertThat(metric("OrderCancelled", "failure")).isEqualTo(1);
    }

    private void acknowledgePublications() {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(4);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                anyString(), anyString(), any(), any(MessagePostProcessor.class),
                any(CorrelationData.class));
    }

    private double metric(String eventType, String result) {
        return meterRegistry.find("orderflow.messaging.order_event.publications")
                .tag("event.type", eventType)
                .tag("result", result)
                .counter()
                .count();
    }
}
