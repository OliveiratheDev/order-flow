package com.start.overflow.notification.adapter.in.messaging;

import com.start.overflow.notification.application.InvalidNotificationEventException;
import com.start.overflow.notification.application.PaymentNotificationCommand;
import com.start.overflow.notification.application.PaymentNotificationService;
import com.start.overflow.shared.messaging.EventEnvelope;
import com.start.overflow.shared.messaging.RabbitTopology;
import com.start.overflow.shared.observability.CorrelationIdContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "orderflow.messaging.rabbit.enabled", havingValue = "true")
public class OrderPaidNotificationListener {
    private static final Logger log = LoggerFactory.getLogger(OrderPaidNotificationListener.class);
    private static final String EVENT_TYPE = "OrderPaid";
    private static final int EVENT_VERSION = 1;

    private final PaymentNotificationService notificationService;

    public OrderPaidNotificationListener(PaymentNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = RabbitTopology.NOTIFICATION_ORDER_PAID_QUEUE)
    public void handle(EventEnvelope<OrderPaidMessage> envelope) {
        String previousCorrelationId = MDC.get(CorrelationIdContext.MDC_KEY);
        try {
            validateEnvelope(envelope);
            MDC.put(CorrelationIdContext.MDC_KEY, envelope.correlationId());
            OrderPaidMessage payload = envelope.payload();
            notificationService.notifyPayment(new PaymentNotificationCommand(
                    envelope.eventId(), envelope.correlationId(), payload.orderId(),
                    payload.customerId(), payload.total()));
        } catch (InvalidNotificationEventException exception) {
            log.atWarn()
                    .setCause(exception)
                    .addKeyValue("eventId", envelope == null ? null : envelope.eventId())
                    .addKeyValue("eventType", envelope == null ? null : envelope.eventType())
                    .addKeyValue("eventVersion", envelope == null ? null : envelope.eventVersion())
                    .log("Evento de pagamento inválido rejeitado sem retry");
            throw new AmqpRejectAndDontRequeueException(
                    "Evento de pagamento inválido", exception);
        } finally {
            restoreCorrelationId(previousCorrelationId);
        }
    }

    private void validateEnvelope(EventEnvelope<OrderPaidMessage> envelope) {
        if (envelope == null) {
            throw new InvalidNotificationEventException("Envelope é obrigatório");
        }
        if (!EVENT_TYPE.equals(envelope.eventType())) {
            throw new InvalidNotificationEventException("eventType não suportado");
        }
        if (envelope.eventVersion() != EVENT_VERSION) {
            throw new InvalidNotificationEventException("eventVersion não suportada");
        }
        if (envelope.payload() == null) {
            throw new InvalidNotificationEventException("payload é obrigatório");
        }
    }

    private void restoreCorrelationId(String previousCorrelationId) {
        if (previousCorrelationId == null) {
            MDC.remove(CorrelationIdContext.MDC_KEY);
        } else {
            MDC.put(CorrelationIdContext.MDC_KEY, previousCorrelationId);
        }
    }
}
