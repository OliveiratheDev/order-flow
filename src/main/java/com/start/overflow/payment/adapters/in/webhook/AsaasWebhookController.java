package com.start.overflow.payment.adapters.in.webhook;

import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks/asaas")
@Profile("payment-asaas")
@Tag(name = "Asaas Webhook")
@SecurityRequirement(name = "asaasWebhookToken")
public class AsaasWebhookController {
    private static final String TOKEN_HEADER = "asaas-access-token";
    private final AsaasWebhookDispatcher dispatcher;

    public AsaasWebhookController(AsaasWebhookDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Recebe um evento financeiro autenticado do Asaas")
    public WebhookHandlingResult receive(
            @RequestHeader(name = TOKEN_HEADER, required = false) String token,
            @RequestBody byte[] payload,
            HttpServletRequest request) {
        return dispatcher.dispatch(new RawWebhookRequest(
                token, payload, request.getRemoteAddr()));
    }
}
