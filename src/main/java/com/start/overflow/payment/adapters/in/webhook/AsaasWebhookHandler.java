package com.start.overflow.payment.adapters.in.webhook;

import com.start.overflow.payment.application.WebhookReconciliationRequiredException;
import com.start.overflow.shared.exception.InvalidTransitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.Set;

public abstract class AsaasWebhookHandler<T extends AsaasPaymentWebhookEvent> {
    private static final Logger log = LoggerFactory.getLogger(AsaasWebhookHandler.class);

    private final Set<String> supportedEvents;
    private final AsaasWebhookTokenValidator tokenValidator;
    private final WebhookEventLogStore eventLogStore;
    private final TransactionTemplate transactionTemplate;

    protected AsaasWebhookHandler(Set<String> supportedEvents,
                                  AsaasWebhookTokenValidator tokenValidator,
                                  WebhookEventLogStore eventLogStore,
                                  PlatformTransactionManager transactionManager) {
        this.supportedEvents = Set.copyOf(supportedEvents);
        this.tokenValidator = tokenValidator;
        this.eventLogStore = eventLogStore;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public final boolean supports(String eventType) {
        return supportedEvents.contains(eventType);
    }

    public final WebhookHandlingResult handle(RawWebhookRequest raw) {
        tokenValidator.validate(raw);
        T event = parse(raw);
        if (!supports(event.eventType())) {
            throw new UnsupportedWebhookEventException();
        }
        try {
            return Objects.requireNonNull(transactionTemplate.execute(status -> {
                int inserted = eventLogStore.tryRegister(
                        event.eventId(), event.eventType(), raw.payloadAsString());
                if (inserted == 0) {
                    WebhookEventStatus current = eventLogStore.lockStatus(event.eventId());
                    if (current != WebhookEventStatus.FAILED) {
                        eventLogStore.markDuplicate(event.eventId());
                        log.atInfo()
                                .addKeyValue("webhookEventId", event.eventId())
                                .addKeyValue("webhookEventType", event.eventType())
                                .log("Webhook duplicado ignorado");
                        return new WebhookHandlingResult(
                                event.eventId(), WebhookHandlingResult.Status.DUPLICATE);
                    }
                    eventLogStore.prepareRetry(
                            event.eventId(), event.eventType(), raw.payloadAsString());
                }
                process(event);
                eventLogStore.markProcessed(event.eventId());
                return new WebhookHandlingResult(
                        event.eventId(), WebhookHandlingResult.Status.PROCESSED);
            }));
        } catch (InvalidTransitionException | WebhookReconciliationRequiredException exception) {
            recordFailure(event, raw, exception.getMessage());
            log.atWarn()
                    .addKeyValue("webhookEventId", event.eventId())
                    .addKeyValue("webhookEventType", event.eventType())
                    .addKeyValue("reason", exception.getMessage())
                    .log("Webhook exige conciliação manual");
            return new WebhookHandlingResult(
                    event.eventId(), WebhookHandlingResult.Status.RECONCILIATION_REQUIRED);
        } catch (RuntimeException exception) {
            recordFailure(event, raw, "Falha interna durante o processamento");
            throw new WebhookProcessingException(exception);
        }
    }

    protected abstract T parse(RawWebhookRequest raw);

    protected abstract void process(T event);

    private void recordFailure(T event, RawWebhookRequest raw, String reason) {
        transactionTemplate.executeWithoutResult(status -> eventLogStore.recordFailure(
                event.eventId(), event.eventType(), raw.payloadAsString(), reason));
    }
}
