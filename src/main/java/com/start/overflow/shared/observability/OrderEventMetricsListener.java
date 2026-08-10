package com.start.overflow.shared.observability;

import com.start.overflow.order.event.OrderDomainEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class OrderEventMetricsListener {
    private static final Logger log = LoggerFactory.getLogger(OrderEventMetricsListener.class);
    private static final String EVENTS_METRIC = "orderflow.order.events";
    private static final String FAILURES_METRIC = "orderflow.order.event_listener.failures";
    private static final String ORDERS_CREATED_METRIC = "orderflow.orders.created";
    private static final String[] EVENT_TYPES = {
            "OrderCreated", "OrderPaid", "OrderCancelled"
    };

    private final Map<String, Counter> eventCounters;
    private final Counter failureCounter;
    private final Counter ordersCreatedCounter;

    @Autowired
    public OrderEventMetricsListener(MeterRegistry meterRegistry) {
        Map<String, Counter> counters = new LinkedHashMap<>();
        for (String eventType : EVENT_TYPES) {
            counters.put(eventType, Counter.builder(EVENTS_METRIC)
                    .tag("event.type", eventType)
                    .register(meterRegistry));
        }
        this.eventCounters = Map.copyOf(counters);
        this.failureCounter = meterRegistry.counter(FAILURES_METRIC);
        this.ordersCreatedCounter = Counter.builder(ORDERS_CREATED_METRIC)
                .tag("status", "created")
                .register(meterRegistry);
    }

    OrderEventMetricsListener(
            Map<String, Counter> eventCounters,
            Counter failureCounter,
            Counter ordersCreatedCounter
    ) {
        this.eventCounters = Map.copyOf(eventCounters);
        this.failureCounter = failureCounter;
        this.ordersCreatedCounter = ordersCreatedCounter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void observe(OrderDomainEvent event) {
        try {
            Counter counter = eventCounters.get(event.eventType());
            if (counter == null) {
                throw new IllegalArgumentException(
                        "Tipo de evento de pedido não registrado: " + event.eventType());
            }
            counter.increment();
            if ("OrderCreated".equals(event.eventType())) {
                ordersCreatedCounter.increment();
            }
            log.atInfo()
                    .addKeyValue("eventId", event.eventId())
                    .addKeyValue("eventType", event.eventType())
                    .addKeyValue("orderId", event.orderId())
                    .addKeyValue("correlationId", CorrelationIdContext.currentOrCreate())
                    .log("Evento de pedido observado após o commit");
        } catch (RuntimeException exception) {
            recordFailure(event, exception);
        }
    }

    private void recordFailure(OrderDomainEvent event, RuntimeException exception) {
        try {
            failureCounter.increment();
        } catch (RuntimeException metricFailure) {
            exception.addSuppressed(metricFailure);
        }
        log.atError()
                .setCause(exception)
                .addKeyValue("eventId", event.eventId())
                .addKeyValue("eventType", event.eventType())
                .addKeyValue("orderId", event.orderId())
                .addKeyValue("correlationId", CorrelationIdContext.currentOrCreate())
                .log("Falha visível em listener pós-commit de pedido");
    }
}
