package com.start.overflow.notification.application;

import com.start.overflow.notification.adapter.out.persistence.NotificationDeliveryStore;
import com.start.overflow.notification.adapter.out.persistence.ProcessedEventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentNotificationService {
    public static final String CONSUMER = "payment-notification";

    private static final Logger log = LoggerFactory.getLogger(PaymentNotificationService.class);

    private final ProcessedEventStore processedEvents;
    private final NotificationDeliveryStore deliveries;

    public PaymentNotificationService(
            ProcessedEventStore processedEvents,
            NotificationDeliveryStore deliveries
    ) {
        this.processedEvents = processedEvents;
        this.deliveries = deliveries;
    }

    @Transactional
    public NotificationProcessingResult notifyPayment(PaymentNotificationCommand command) {
        validate(command);
        if (!processedEvents.tryRegister(command.eventId(), CONSUMER)) {
            log.atInfo()
                    .addKeyValue("eventId", command.eventId())
                    .addKeyValue("orderId", command.orderId())
                    .addKeyValue("consumer", CONSUMER)
                    .log("Evento de pagamento já processado; notificação ignorada");
            return NotificationProcessingResult.DUPLICATE;
        }

        deliveries.record(command, CONSUMER);
        log.atInfo()
                .addKeyValue("eventId", command.eventId())
                .addKeyValue("orderId", command.orderId())
                .addKeyValue("customerId", command.customerId())
                .addKeyValue("amount", command.amount())
                .addKeyValue("channel", "EMAIL")
                .addKeyValue("consumer", CONSUMER)
                .log("Notificação de pagamento enviada em modo simulado");
        return NotificationProcessingResult.SENT;
    }

    private void validate(PaymentNotificationCommand command) {
        if (command == null || command.eventId() == null) {
            throw new InvalidNotificationEventException("eventId é obrigatório");
        }
        if (command.correlationId() == null || command.correlationId().isBlank()) {
            throw new InvalidNotificationEventException("correlationId é obrigatório");
        }
        if (command.orderId() == null || command.orderId() < 1) {
            throw new InvalidNotificationEventException("orderId deve ser positivo");
        }
        if (command.customerId() == null || command.customerId() < 1) {
            throw new InvalidNotificationEventException("customerId deve ser positivo");
        }
        if (command.amount() == null || command.amount().signum() <= 0) {
            throw new InvalidNotificationEventException("amount deve ser positivo");
        }
    }
}
