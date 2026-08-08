package com.start.overflow.payment.adapters.out.gateway;

import com.start.overflow.payment.domain.ChargeRequest;
import com.start.overflow.payment.domain.GatewayChargeResult;
import com.start.overflow.payment.domain.PaymentGatewayUnavailableException;
import com.start.overflow.payment.domain.PaymentRejectedException;
import com.start.overflow.payment.ports.out.PaymentGatewayPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;

@Component
@Profile("payment-http")
public class HttpPaymentGatewayAdapter implements PaymentGatewayPort {
    private final RestClient restClient;

    public HttpPaymentGatewayAdapter(RestClient.Builder builder,
                                     @Value("${PAYMENT_GATEWAY_BASE_URL}") String baseUrl,
                                     @Value("${PAYMENT_GATEWAY_API_KEY}") String apiKey) {
        this.restClient = builder.baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }

    @Override
    public GatewayChargeResult createCharge(ChargeRequest request) {
        GatewayRequest body = new GatewayRequest(request.orderId(), request.amount().value(),
                request.method().name());
        try {
            GatewayResponse response = restClient.post()
                    .uri("/charges")
                    .header("X-Correlation-Id", request.correlationId())
                    .body(body)
                    .retrieve()
                    .body(GatewayResponse.class);
            return toDomain(response);
        } catch (RestClientException exception) {
            throw translate(exception);
        }
    }

    @Override
    public GatewayChargeResult getCharge(String externalId) {
        try {
            GatewayResponse response = restClient.get()
                    .uri("/charges/{externalId}", externalId)
                    .retrieve()
                    .body(GatewayResponse.class);
            return toDomain(response);
        } catch (RestClientException exception) {
            throw translate(exception);
        }
    }

    private GatewayChargeResult toDomain(GatewayResponse response) {
        if (response == null) {
            throw new PaymentGatewayUnavailableException(
                    "O gateway retornou uma resposta vazia");
        }
        return new GatewayChargeResult(response.externalId(), response.approved(),
                response.rejectionReason());
    }

    private RuntimeException translate(RestClientException exception) {
        if (exception instanceof RestClientResponseException responseException
                && responseException.getStatusCode().is4xxClientError()) {
            return new PaymentRejectedException("O gateway rejeitou a solicitação de cobrança");
        }
        return new PaymentGatewayUnavailableException(
                "O gateway de pagamento está temporariamente indisponível", exception);
    }

    private record GatewayRequest(Long orderId, BigDecimal amount, String method) {
    }

    private record GatewayResponse(String externalId, boolean approved, String rejectionReason) {
    }
}
