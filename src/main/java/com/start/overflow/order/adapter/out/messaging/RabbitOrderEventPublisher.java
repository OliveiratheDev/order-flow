package com.start.overflow.order.adapter.out.messaging;

import com.start.overflow.order.event.OrderCancelledEvent;
import com.start.overflow.order.event.OrderCreatedEvent;
import com.start.overflow.order.event.OrderDomainEvent;
import com.start.overflow.order.event.OrderPaidEvent;
import com.start.overflow.shared.messaging.EventEnvelope;
import com.start.overflow.shared.messaging.RabbitTopology;
import com.start.overflow.shared.observability.CorrelationIdContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@ConditionalOnProperty(name = "orderflow.messaging.rabbit.enabled", havingValue = "true")
public class RabbitOrderEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(RabbitOrderEventPublisher.class);
    private static final int EVENT_VERSION = 1;
    private static final String PUBLICATION_METRIC = "orderflow.messaging.order_event.publications";
    private static final String[] EVENT_TYPES = {
            "OrderCreated", "OrderPaid", "OrderCancelled"
    };

    private final RabbitTemplate rabbitTemplate;
    private final Duration confirmTimeout;
    private final Map<String, PublicationCounters> counters;

    public RabbitOrderEventPublisher(
            RabbitTemplate rabbitTemplate,
            MeterRegistry meterRegistry,
            @Value("${orderflow.messaging.rabbit.publisher-confirm-timeout:5s}")
            Duration confirmTimeout
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.confirmTimeout = confirmTimeout;
        Map<String, PublicationCounters> registeredCounters = new LinkedHashMap<>();
        for (String eventType : EVENT_TYPES) {
            registeredCounters.put(eventType, new PublicationCounters(
                    counter(meterRegistry, eventType, "success"),
                    counter(meterRegistry, eventType, "failure")));
        }
        this.counters = Map.copyOf(registeredCounters);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCreated(OrderCreatedEvent event) {
        OrderCreatedPayload payload = new OrderCreatedPayload(
                event.orderId(), event.customerId(), event.total(),
                event.items().stream().map(item -> new OrderItemPayload(
                        item.productId(), item.productName(), item.sku(), item.quantity(),
                        item.unitPrice(), item.lineTotal())).toList());
        publish(event, RabbitTopology.ORDER_CREATED_ROUTING_KEY, payload);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaid(OrderPaidEvent event) {
        publish(event, RabbitTopology.ORDER_PAID_ROUTING_KEY,
                new OrderStatusChangedPayload(
                        event.orderId(), event.customerId(), event.total()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCancelled(OrderCancelledEvent event) {
        publish(event, RabbitTopology.ORDER_CANCELLED_ROUTING_KEY,
                new OrderStatusChangedPayload(
                        event.orderId(), event.customerId(), event.total()));
    }

    private void publish(OrderDomainEvent event, String routingKey, Object payload) {
        EventEnvelope<Object> envelope = new EventEnvelope<>(
                event.eventId(), event.eventType(), EVENT_VERSION, event.occurredAt(),
                CorrelationIdContext.currentOrCreate(), payload);
        CorrelationData correlation = new CorrelationData(event.eventId().toString());
        try {
            rabbitTemplate.convertAndSend(
                    RabbitTopology.ORDER_EVENTS_EXCHANGE,
                    routingKey,
                    envelope,
                    message -> {
                        message.getMessageProperties().setMessageId(event.eventId().toString());
                        message.getMessageProperties().setCorrelationId(envelope.correlationId());
                        message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        message.getMessageProperties().setHeader("eventType", event.eventType());
                        message.getMessageProperties().setHeader("eventVersion", EVENT_VERSION);
                        return message;
                    },
                    correlation);

            CorrelationData.Confirm confirm = correlation.getFuture().get(
                    confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.ack()) {
                throw new IllegalStateException("Publicação não confirmada: " + confirm.reason());
            }
            if (correlation.getReturned() != null) {
                throw new IllegalStateException("Mensagem não roteada pelo broker");
            }
            counters.get(event.eventType()).success().increment();
            log.atInfo()
                    .addKeyValue("eventId", event.eventId())
                    .addKeyValue("eventType", event.eventType())
                    .addKeyValue("eventVersion", EVENT_VERSION)
                    .addKeyValue("routingKey", routingKey)
                    .addKeyValue("correlationId", envelope.correlationId())
                    .log("Evento de pedido publicado no RabbitMQ");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            recordFailure(envelope, routingKey, exception);
        } catch (ExecutionException | TimeoutException | RuntimeException exception) {
            recordFailure(envelope, routingKey, exception);
        }
    }

    private void recordFailure(EventEnvelope<?> envelope, String routingKey, Exception exception) {
        counters.get(envelope.eventType()).failure().increment();
        log.atError()
                .setCause(exception)
                .addKeyValue("eventId", envelope.eventId())
                .addKeyValue("eventType", envelope.eventType())
                .addKeyValue("eventVersion", envelope.eventVersion())
                .addKeyValue("routingKey", routingKey)
                .addKeyValue("correlationId", envelope.correlationId())
                .log("Falha ao publicar evento de pedido; reprocessamento manual necessário");
    }

    private Counter counter(MeterRegistry registry, String eventType, String result) {
        return Counter.builder(PUBLICATION_METRIC)
                .tag("event.type", eventType)
                .tag("result", result)
                .register(registry);
    }

    private record PublicationCounters(Counter success, Counter failure) {
    }
}
