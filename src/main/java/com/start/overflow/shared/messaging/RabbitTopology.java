package com.start.overflow.shared.messaging;

public final class RabbitTopology {
    public static final String ORDER_EVENTS_EXCHANGE = "order.events";
    public static final String ORDER_EVENTS_DLX = "order.events.dlx";

    public static final String NOTIFICATION_ORDER_CREATED_QUEUE = "notification.order-created";
    public static final String NOTIFICATION_ORDER_PAID_QUEUE = "notification.order-paid";
    public static final String AUDIT_QUEUE = "audit.queue";
    public static final String ORDER_EVENTS_DLQ = "order.events.dlq";

    public static final String ORDER_CREATED_ROUTING_KEY = "order.created";
    public static final String ORDER_PAID_ROUTING_KEY = "order.paid";
    public static final String ORDER_CANCELLED_ROUTING_KEY = "order.cancelled";
    public static final String ALL_ORDER_EVENTS_PATTERN = "order.#";
    public static final String DEAD_LETTER_ROUTING_KEY = "dead";

    private RabbitTopology() {
    }
}
