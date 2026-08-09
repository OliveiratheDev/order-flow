package com.start.overflow.payment.adapters.in.webhook;

import com.start.overflow.payment.ports.in.PaymentWebhookUseCase;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Set;

@Component
@Profile("payment-asaas")
public class PaymentRefusedWebhookHandler
        extends AsaasWebhookHandler<AsaasPaymentWebhookEvent> {
    private final AsaasPaymentWebhookParser parser;
    private final PaymentWebhookUseCase paymentWebhookUseCase;

    public PaymentRefusedWebhookHandler(
            AsaasWebhookTokenValidator tokenValidator,
            WebhookEventLogStore eventLogStore,
            PlatformTransactionManager transactionManager,
            AsaasPaymentWebhookParser parser,
            PaymentWebhookUseCase paymentWebhookUseCase) {
        super(Set.of("PAYMENT_CREDIT_CARD_CAPTURE_REFUSED",
                        "PAYMENT_REPROVED_BY_RISK_ANALYSIS"),
                tokenValidator, eventLogStore, transactionManager);
        this.parser = parser;
        this.paymentWebhookUseCase = paymentWebhookUseCase;
    }

    @Override
    protected AsaasPaymentWebhookEvent parse(RawWebhookRequest raw) {
        return parser.parse(raw);
    }

    @Override
    protected void process(AsaasPaymentWebhookEvent event) {
        paymentWebhookUseCase.refuse(event.toCommand());
    }
}
