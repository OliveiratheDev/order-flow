package com.start.overflow.shared.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.SmartInitializingSingleton;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RabbitMessagingConfigTest {
    private final RabbitMessagingConfig config = new RabbitMessagingConfig();

    @Test
    void declaresDurableTopicBindingsAndDeadLetterTopology() {
        TopicExchange exchange = config.orderEventsExchange();
        Queue created = config.notificationOrderCreatedQueue();
        Queue paid = config.notificationOrderPaidQueue();
        Queue audit = config.auditQueue();
        Queue dead = config.orderEventsDeadLetterQueue();

        assertThat(exchange.getName()).isEqualTo("order.events");
        assertThat(exchange.isDurable()).isTrue();
        assertThat(exchange.isAutoDelete()).isFalse();
        assertWorkQueue(created, "notification.order-created");
        assertWorkQueue(paid, "notification.order-paid");
        assertWorkQueue(audit, "audit.queue");
        assertThat(dead.isDurable()).isTrue();
        assertThat(dead.getArguments()).doesNotContainKeys(
                "x-dead-letter-exchange", "x-dead-letter-routing-key");

        assertBinding(config.notificationOrderCreatedBinding(exchange, created),
                "notification.order-created", "order.created");
        assertBinding(config.notificationOrderPaidBinding(exchange, paid),
                "notification.order-paid", "order.paid");
        assertBinding(config.auditBinding(exchange, audit), "audit.queue", "order.#");
        assertBinding(config.deadLetterBinding(
                        config.orderEventsDeadLetterExchange(), dead),
                "order.events.dlq", "dead");
    }

    @Test
    void serializesEnvelopeAsInspectableJsonWithJacksonThree() throws Exception {
        MessageConverter converter = config.rabbitJsonMessageConverter();
        EventEnvelope<Map<String, Long>> envelope = new EventEnvelope<>(
                UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                "OrderCreated", 1, Instant.parse("2026-08-09T20:00:00Z"),
                "correlation-123", Map.of("orderId", 42L));

        var message = converter.toMessage(
                envelope, new org.springframework.amqp.core.MessageProperties());
        JsonNode json = JsonMapper.builder().findAndAddModules().build()
                .readTree(message.getBody());

        assertThat(converter).isInstanceOf(JacksonJsonMessageConverter.class);
        assertThat(message.getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(json.get("eventId").asString())
                .isEqualTo("550e8400-e29b-41d4-a716-446655440000");
        assertThat(json.get("eventVersion").asInt()).isEqualTo(1);
        assertThat(json.get("payload").get("orderId").asLong()).isEqualTo(42L);
    }

    @Test
    void configuresConfirmAndReturnCallbacks() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);

        SmartInitializingSingleton callbacks = config.rabbitPublisherCallbacks(rabbitTemplate);
        callbacks.afterSingletonsInstantiated();

        verify(rabbitTemplate).setConfirmCallback(org.mockito.ArgumentMatchers.any());
        verify(rabbitTemplate).setReturnsCallback(org.mockito.ArgumentMatchers.any());
    }

    private void assertWorkQueue(Queue queue, String expectedName) {
        assertThat(queue.getName()).isEqualTo(expectedName);
        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.isExclusive()).isFalse();
        assertThat(queue.isAutoDelete()).isFalse();
        assertThat(queue.getArguments())
                .containsEntry("x-dead-letter-exchange", "order.events.dlx")
                .containsEntry("x-dead-letter-routing-key", "dead");
    }

    private void assertBinding(Binding binding, String queue, String routingKey) {
        assertThat(binding.getDestination()).isEqualTo(queue);
        assertThat(binding.getRoutingKey()).isEqualTo(routingKey);
    }
}
