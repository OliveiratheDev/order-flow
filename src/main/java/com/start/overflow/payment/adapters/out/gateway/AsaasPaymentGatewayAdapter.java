package com.start.overflow.payment.adapters.out.gateway;

import com.start.overflow.payment.domain.ChargeRequest;
import com.start.overflow.payment.domain.GatewayChargeResult;
import com.start.overflow.payment.domain.GatewayChargeStatus;
import com.start.overflow.payment.domain.Payer;
import com.start.overflow.payment.domain.PaymentGatewayUnavailableException;
import com.start.overflow.payment.domain.PaymentMethod;
import com.start.overflow.payment.domain.PaymentRejectedException;
import com.start.overflow.payment.ports.out.PaymentGatewayPort;
import com.start.overflow.shared.observability.CorrelationIdContext;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Component
@Profile("payment-asaas & !payment-http & !payment-declined")
public class AsaasPaymentGatewayAdapter implements PaymentGatewayPort {
    private static final String ACCESS_TOKEN_HEADER = "access_token";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    private final RestClient restClient;
    private final int paymentDueDays;
    private final Clock clock;

    public AsaasPaymentGatewayAdapter(RestClient.Builder builder, AsaasProperties properties) {
        this(builder, properties, Clock.systemUTC());
    }

    AsaasPaymentGatewayAdapter(RestClient.Builder builder, AsaasProperties properties, Clock clock) {
        validate(properties);
        this.paymentDueDays = properties.paymentDueDays();
        this.clock = clock;
        this.restClient = builder
                .baseUrl(withoutTrailingSlash(properties.baseUrl()))
                .defaultHeader(ACCESS_TOKEN_HEADER, properties.apiKey())
                .defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public GatewayChargeResult createCharge(ChargeRequest request) {
        try {
            String customerId = findOrCreateCustomer(request.payer(), request.correlationId());
            AsaasPaymentResponse response = restClient.post()
                    .uri("/payments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(CORRELATION_ID_HEADER, request.correlationId())
                    .body(new AsaasPaymentRequest(
                            customerId,
                            request.method().name(),
                            request.amount().value(),
                            LocalDate.now(clock).plusDays(paymentDueDays),
                            "Pedido OrderFlow #" + request.orderId(),
                            paymentReference(request.orderId())))
                    .retrieve()
                    .body(AsaasPaymentResponse.class);
            return toDomain(response);
        } catch (RestClientException exception) {
            throw translate(exception);
        }
    }

    @Override
    public GatewayChargeResult getCharge(String externalId) {
        try {
            AsaasPaymentResponse response = restClient.get()
                    .uri("/payments/{id}", externalId)
                    .header(CORRELATION_ID_HEADER, CorrelationIdContext.currentOrCreate())
                    .retrieve()
                    .body(AsaasPaymentResponse.class);
            return toDomain(response);
        } catch (RestClientException exception) {
            throw translate(exception);
        }
    }

    @Override
    public void cancelCharge(String externalId) {
        try {
            restClient.delete()
                    .uri("/payments/{id}", externalId)
                    .header(CORRELATION_ID_HEADER, CorrelationIdContext.currentOrCreate())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw translate(exception);
        }
    }

    private String findOrCreateCustomer(Payer payer, String correlationId) {
        AsaasCustomerListResponse found = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/customers")
                        .queryParam("externalReference", payer.id())
                        .queryParam("limit", 1)
                        .build())
                .header(CORRELATION_ID_HEADER, correlationId)
                .retrieve()
                .body(AsaasCustomerListResponse.class);
        if (found == null || found.data() == null) {
            throw new PaymentGatewayUnavailableException(
                    "O Asaas retornou uma resposta inválida ao consultar o pagador");
        }
        if (!found.data().isEmpty()) {
            return requiredId(found.data().getFirst().id(), "pagador");
        }

        AsaasCustomerResponse created = restClient.post()
                .uri("/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .header(CORRELATION_ID_HEADER, correlationId)
                .body(new AsaasCustomerRequest(
                        payer.name(), payer.taxId(), payer.email(),
                        payer.id().toString(), true))
                .retrieve()
                .body(AsaasCustomerResponse.class);
        if (created == null) {
            throw new PaymentGatewayUnavailableException(
                    "O Asaas retornou uma resposta vazia ao criar o pagador");
        }
        return requiredId(created.id(), "pagador");
    }

    private GatewayChargeResult toDomain(AsaasPaymentResponse response) {
        if (response == null) {
            throw new PaymentGatewayUnavailableException("O Asaas retornou uma resposta vazia");
        }
        String externalId = requiredId(response.id(), "pagamento");
        GatewayChargeStatus status = mapStatus(response.status());
        return new GatewayChargeResult(externalId, status,
                status == GatewayChargeStatus.REJECTED
                        ? "A cobrança foi rejeitada pelo Asaas" : null,
                response.invoiceUrl());
    }

    private GatewayChargeStatus mapStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new PaymentGatewayUnavailableException(
                    "O Asaas não informou o estado da cobrança");
        }
        return switch (status) {
            case "CONFIRMED", "RECEIVED", "RECEIVED_IN_CASH" -> GatewayChargeStatus.APPROVED;
            case "PENDING", "OVERDUE", "AWAITING_RISK_ANALYSIS" -> GatewayChargeStatus.PENDING;
            case "REFUNDED", "REFUND_REQUESTED", "REFUND_IN_PROGRESS",
                    "CHARGEBACK_REQUESTED", "CHARGEBACK_DISPUTE",
                    "AWAITING_CHARGEBACK_REVERSAL", "DELETED" -> GatewayChargeStatus.REJECTED;
            default -> throw new PaymentGatewayUnavailableException(
                    "O Asaas retornou um estado de cobrança desconhecido");
        };
    }

    private RuntimeException translate(RestClientException exception) {
        if (exception instanceof RestClientResponseException responseException) {
            int status = responseException.getStatusCode().value();
            if (status == 400 || status == 422) {
                return new PaymentRejectedException("O Asaas rejeitou os dados da cobrança");
            }
        }
        return new PaymentGatewayUnavailableException(
                "O Asaas está temporariamente indisponível", exception);
    }

    private String requiredId(String value, String resource) {
        if (value == null || value.isBlank()) {
            throw new PaymentGatewayUnavailableException(
                    "O Asaas não informou o identificador do " + resource);
        }
        return value.strip();
    }

    private String paymentReference(Long orderId) {
        return "orderflow-order-" + orderId;
    }

    private static String withoutTrailingSlash(String value) {
        String normalized = value.strip();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static void validate(AsaasProperties properties) {
        if (properties == null
                || properties.baseUrl() == null || properties.baseUrl().isBlank()
                || properties.apiKey() == null || properties.apiKey().isBlank()
                || properties.userAgent() == null || properties.userAgent().isBlank()) {
            throw new IllegalStateException(
                    "ASAAS_BASE_URL, ASAAS_API_KEY e ASAAS_USER_AGENT são obrigatórios");
        }
        if (!properties.baseUrl().startsWith("https://")
                && !properties.baseUrl().startsWith("http://localhost")) {
            throw new IllegalStateException("ASAAS_BASE_URL deve usar HTTPS");
        }
        if (properties.paymentDueDays() < 1 || properties.paymentDueDays() > 30) {
            throw new IllegalStateException(
                    "ASAAS_PAYMENT_DUE_DAYS deve estar entre 1 e 30");
        }
    }

    private record AsaasCustomerRequest(
            String name,
            String cpfCnpj,
            String email,
            String externalReference,
            boolean notificationDisabled
    ) {
    }

    private record AsaasCustomerResponse(String id) {
    }

    private record AsaasCustomerListResponse(List<AsaasCustomerResponse> data) {
    }

    private record AsaasPaymentRequest(
            String customer,
            String billingType,
            BigDecimal value,
            LocalDate dueDate,
            String description,
            String externalReference
    ) {
    }

    private record AsaasPaymentResponse(
            String id,
            String status,
            String invoiceUrl
    ) {
    }
}
