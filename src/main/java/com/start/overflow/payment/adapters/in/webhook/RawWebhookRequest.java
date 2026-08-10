package com.start.overflow.payment.adapters.in.webhook;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public record RawWebhookRequest(
        String authenticationToken,
        byte[] payload,
        String remoteAddress
) {
    private static final int MAX_PAYLOAD_BYTES = 256 * 1024;

    public RawWebhookRequest {
        if (payload == null || payload.length == 0) {
            throw new InvalidWebhookPayloadException("O corpo do webhook é obrigatório");
        }
        if (payload.length > MAX_PAYLOAD_BYTES) {
            throw new InvalidWebhookPayloadException("O corpo do webhook excede o limite permitido");
        }
        payload = Arrays.copyOf(payload, payload.length);
        remoteAddress = remoteAddress == null || remoteAddress.isBlank()
                ? "unknown" : remoteAddress.strip();
    }

    @Override
    public byte[] payload() {
        return Arrays.copyOf(payload, payload.length);
    }

    public String payloadAsString() {
        return new String(payload, StandardCharsets.UTF_8);
    }
}
