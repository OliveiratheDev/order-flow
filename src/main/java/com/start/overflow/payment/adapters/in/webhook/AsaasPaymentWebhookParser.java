package com.start.overflow.payment.adapters.in.webhook;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

@Component
@Profile("payment-asaas")
public class AsaasPaymentWebhookParser {
    private final ObjectMapper objectMapper;

    public AsaasPaymentWebhookParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String readEventType(RawWebhookRequest request) {
        return requiredText(readRoot(request), "event", 60);
    }

    public AsaasPaymentWebhookEvent parse(RawWebhookRequest request) {
        JsonNode root = readRoot(request);
        JsonNode payment = root.get("payment");
        if (payment == null || !payment.isObject()) {
            throw new InvalidWebhookPayloadException(
                    "O objeto payment é obrigatório no webhook");
        }
        JsonNode valueNode = payment.get("value");
        if (valueNode == null || !valueNode.isNumber()) {
            throw new InvalidWebhookPayloadException(
                    "O valor do pagamento é obrigatório no webhook");
        }
        BigDecimal amount = valueNode.decimalValue();
        if (amount.signum() <= 0) {
            throw new InvalidWebhookPayloadException(
                    "O valor do pagamento deve ser positivo");
        }
        return new AsaasPaymentWebhookEvent(
                requiredText(root, "id", 120),
                requiredText(root, "event", 60),
                requiredText(payment, "id", 100),
                amount,
                optionalText(payment, "externalReference", 120));
    }

    private JsonNode readRoot(RawWebhookRequest request) {
        try {
            JsonNode root = objectMapper.readTree(request.payload());
            if (root == null || !root.isObject()) {
                throw new InvalidWebhookPayloadException(
                        "O webhook deve conter um objeto JSON");
            }
            return root;
        } catch (InvalidWebhookPayloadException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InvalidWebhookPayloadException("O JSON do webhook é inválido", exception);
        }
    }

    private String requiredText(JsonNode node, String field, int maxLength) {
        String value = optionalText(node, field, maxLength);
        if (value == null) {
            throw new InvalidWebhookPayloadException(
                    "O campo " + field + " é obrigatório no webhook");
        }
        return value;
    }

    private String optionalText(JsonNode node, String field, int maxLength) {
        JsonNode valueNode = node.get(field);
        if (valueNode == null || valueNode.isNull()) {
            return null;
        }
        if (!valueNode.isTextual() || valueNode.textValue().isBlank()) {
            throw new InvalidWebhookPayloadException(
                    "O campo " + field + " deve ser um texto válido");
        }
        String value = valueNode.textValue().strip();
        if (value.length() > maxLength) {
            throw new InvalidWebhookPayloadException(
                    "O campo " + field + " excede o limite permitido");
        }
        return value;
    }
}
