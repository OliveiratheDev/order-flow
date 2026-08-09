package com.start.overflow.payment.adapters.in.webhook;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("payment-asaas")
public class AsaasWebhookDispatcher {
    private final AsaasWebhookTokenValidator tokenValidator;
    private final AsaasPaymentWebhookParser parser;
    private final List<AsaasWebhookHandler<?>> handlers;

    public AsaasWebhookDispatcher(AsaasWebhookTokenValidator tokenValidator,
                                  AsaasPaymentWebhookParser parser,
                                  List<AsaasWebhookHandler<?>> handlers) {
        this.tokenValidator = tokenValidator;
        this.parser = parser;
        this.handlers = List.copyOf(handlers);
    }

    public WebhookHandlingResult dispatch(RawWebhookRequest raw) {
        tokenValidator.validate(raw);
        String eventType = parser.readEventType(raw);
        AsaasWebhookHandler<?> handler = handlers.stream()
                .filter(candidate -> candidate.supports(eventType))
                .findFirst()
                .orElseThrow(UnsupportedWebhookEventException::new);
        return handler.handle(raw);
    }
}
