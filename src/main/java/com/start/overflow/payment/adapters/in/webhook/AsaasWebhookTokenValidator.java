package com.start.overflow.payment.adapters.in.webhook;

import com.start.overflow.payment.adapters.out.gateway.AsaasProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
@Profile("payment-asaas")
public class AsaasWebhookTokenValidator {
    private static final Logger log = LoggerFactory.getLogger(AsaasWebhookTokenValidator.class);
    private final byte[] expectedToken;

    public AsaasWebhookTokenValidator(AsaasProperties properties) {
        String token = properties.webhookToken();
        if (token == null || token.length() < 32 || token.length() > 255) {
            throw new IllegalStateException(
                    "ASAAS_WEBHOOK_TOKEN deve possuir entre 32 e 255 caracteres");
        }
        this.expectedToken = token.getBytes(StandardCharsets.UTF_8);
    }

    public void validate(RawWebhookRequest request) {
        byte[] provided = request.authenticationToken() == null
                ? new byte[0]
                : request.authenticationToken().getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedToken, provided)) {
            log.atWarn()
                    .addKeyValue("remoteAddress", request.remoteAddress())
                    .log("Tentativa de webhook Asaas com token inválido");
            throw new InvalidWebhookTokenException();
        }
    }
}
