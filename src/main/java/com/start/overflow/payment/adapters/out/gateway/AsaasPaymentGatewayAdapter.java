package com.start.overflow.payment.adapters.out.gateway;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.start.overflow.payment.domain.ChargeRequest;
import com.start.overflow.payment.domain.GatewayChargeResult;
import com.start.overflow.payment.domain.GatewayChargeStatus;
import com.start.overflow.payment.domain.Payer;
import com.start.overflow.payment.domain.PaymentException;
import com.start.overflow.payment.domain.PaymentGatewayRequestException;
import com.start.overflow.payment.domain.PaymentGatewayUnavailableException;
import com.start.overflow.payment.domain.PaymentRejectedException;
import com.start.overflow.payment.ports.out.PaymentGatewayPort;
import com.start.overflow.shared.observability.CorrelationIdContext;
import com.start.overflow.shared.observability.ObservedPaymentGateway;
import com.start.overflow.shared.observability.PaymentGatewayName;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

@Component
@Profile("payment-asaas & !payment-http & !payment-declined")
@ObservedPaymentGateway(PaymentGatewayName.ASAAS)
public class AsaasPaymentGatewayAdapter implements PaymentGatewayPort {
    private static final Logger log = LoggerFactory.getLogger(AsaasPaymentGatewayAdapter.class);
    private static final String ACCESS_TOKEN_HEADER = "access_token";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String CREATE_INSTANCE = "asaasPaymentCreate";
    private static final String QUERY_INSTANCE = "asaasPaymentQuery";
    private static final String CANCEL_INSTANCE = "asaasPaymentCancel";

    private final RestClient restClient;
    private final int paymentDueDays;
    private final Clock clock;
    private final CircuitBreaker createCircuitBreaker;
    private final CircuitBreaker queryCircuitBreaker;
    private final CircuitBreaker cancelCircuitBreaker;
    private final Retry queryRetry;
    private final TimeLimiter createTimeLimiter;
    private final TimeLimiter queryTimeLimiter;
    private final TimeLimiter cancelTimeLimiter;
    private final ExecutorService executor;

    @Autowired
    public AsaasPaymentGatewayAdapter(
            RestClient.Builder builder,
            AsaasProperties properties,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry,
            TimeLimiterRegistry timeLimiterRegistry,
            @Qualifier(AsaasResilienceConfiguration.EXECUTOR) ExecutorService executor) {
        this(builder.requestFactory(requestFactory(properties)), properties, Clock.systemUTC(),
                circuitBreakerRegistry.circuitBreaker(CREATE_INSTANCE),
                circuitBreakerRegistry.circuitBreaker(QUERY_INSTANCE),
                circuitBreakerRegistry.circuitBreaker(CANCEL_INSTANCE),
                retryRegistry.retry(QUERY_INSTANCE),
                timeLimiterRegistry.timeLimiter(CREATE_INSTANCE),
                timeLimiterRegistry.timeLimiter(QUERY_INSTANCE),
                timeLimiterRegistry.timeLimiter(CANCEL_INSTANCE),
                executor);
    }

    AsaasPaymentGatewayAdapter(
            RestClient.Builder builder,
            AsaasProperties properties,
            Clock clock,
            CircuitBreaker createCircuitBreaker,
            CircuitBreaker queryCircuitBreaker,
            CircuitBreaker cancelCircuitBreaker,
            Retry queryRetry,
            TimeLimiter createTimeLimiter,
            TimeLimiter queryTimeLimiter,
            TimeLimiter cancelTimeLimiter,
            ExecutorService executor) {
        validate(properties);
        this.paymentDueDays = properties.paymentDueDays();
        this.clock = clock;
        this.createCircuitBreaker = createCircuitBreaker;
        this.queryCircuitBreaker = queryCircuitBreaker;
        this.cancelCircuitBreaker = cancelCircuitBreaker;
        this.queryRetry = queryRetry;
        this.createTimeLimiter = createTimeLimiter;
        this.queryTimeLimiter = queryTimeLimiter;
        this.cancelTimeLimiter = cancelTimeLimiter;
        this.executor = executor;
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
            return execute(createTimeLimiter, createCircuitBreaker, null,
                    () -> createChargeOnce(request));
        } catch (PaymentGatewayUnavailableException createFailure) {
            if (causedByOpenCircuit(createFailure)) {
                throw createFailure;
            }
            Optional<GatewayChargeResult> reconciled = reconcileAfterInconclusiveCreate(request);
            if (reconciled.isPresent()) {
                return reconciled.get();
            }
            throw createFailure;
        }
    }

    @Override
    public GatewayChargeResult getCharge(String externalId) {
        return execute(queryTimeLimiter, queryCircuitBreaker, queryRetry,
                () -> getChargeOnce(externalId));
    }

    @Override
    public Optional<GatewayChargeResult> findChargeForReconciliation(
            Long orderId, String externalId) {
        String correlationId = CorrelationIdContext.currentOrCreate();
        return execute(queryTimeLimiter, queryCircuitBreaker, queryRetry,
                () -> externalId == null
                        ? findChargeByReferenceOnce(paymentReference(orderId), correlationId)
                        : findChargeByExternalIdOnce(externalId, correlationId));
    }

    @Override
    public void cancelCharge(String externalId) {
        execute(cancelTimeLimiter, cancelCircuitBreaker, null, () -> {
            cancelChargeOnce(externalId);
            return null;
        });
    }

    private GatewayChargeResult createChargeOnce(ChargeRequest request) {
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

    private GatewayChargeResult getChargeOnce(String externalId) {
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

    private Optional<GatewayChargeResult> findChargeByExternalIdOnce(
            String externalId, String correlationId) {
        try {
            AsaasPaymentResponse response = restClient.get()
                    .uri("/payments/{id}", externalId)
                    .header(CORRELATION_ID_HEADER, correlationId)
                    .retrieve()
                    .body(AsaasPaymentResponse.class);
            return Optional.of(toDomain(response));
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            throw translate(exception);
        } catch (RestClientException exception) {
            throw translate(exception);
        }
    }

    private void cancelChargeOnce(String externalId) {
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

    private Optional<GatewayChargeResult> reconcileAfterInconclusiveCreate(ChargeRequest request) {
        try {
            Optional<GatewayChargeResult> result = execute(
                    queryTimeLimiter,
                    queryCircuitBreaker,
                    queryRetry,
                    () -> findChargeByReferenceOnce(
                            paymentReference(request.orderId()), request.correlationId()));
            if (result.isPresent()) {
                log.atInfo()
                        .addKeyValue("orderId", request.orderId())
                        .addKeyValue("gatewayExternalId", result.get().externalId())
                        .log("Cobrança recuperada no Asaas após resultado inconclusivo");
            }
            return result;
        } catch (PaymentException reconciliationFailure) {
            log.atWarn()
                    .addKeyValue("orderId", request.orderId())
                    .addKeyValue("correlationId", request.correlationId())
                    .log("Cobrança não pôde ser reconciliada imediatamente no Asaas");
            return Optional.empty();
        }
    }

    private Optional<GatewayChargeResult> findChargeByReferenceOnce(
            String externalReference, String correlationId) {
        try {
            AsaasPaymentListResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/payments")
                            .queryParam("externalReference", externalReference)
                            .queryParam("limit", 2)
                            .build())
                    .header(CORRELATION_ID_HEADER, correlationId)
                    .retrieve()
                    .body(AsaasPaymentListResponse.class);
            if (response == null || response.data() == null) {
                throw new PaymentGatewayUnavailableException(
                        "O Asaas retornou uma resposta inválida durante a reconciliação");
            }
            if (response.data().size() > 1) {
                throw new PaymentGatewayUnavailableException(
                        "O Asaas retornou cobranças duplicadas para o mesmo pedido");
            }
            return response.data().stream().findFirst().map(this::toDomain);
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
                response.invoiceUrl(), response.value());
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
            if (status >= 400 && status < 500) {
                return new PaymentGatewayRequestException(
                        "O Asaas recusou permanentemente a requisição", exception);
            }
        }
        return new PaymentGatewayUnavailableException(
                "O Asaas está temporariamente indisponível", exception);
    }

    private <T> T execute(TimeLimiter timeLimiter, CircuitBreaker circuitBreaker, Retry retry,
                          Supplier<T> call) {
        Supplier<T> decorated = call;
        if (retry != null) {
            decorated = Retry.decorateSupplier(retry, decorated);
        }
        decorated = CircuitBreaker.decorateSupplier(circuitBreaker, decorated);
        Supplier<T> protectedCall = decorated;
        try {
            return timeLimiter.executeFutureSupplier(
                    () -> executor.submit(protectedCall::get));
        } catch (Exception exception) {
            throw translateResilienceFailure(exception);
        }
    }

    private RuntimeException translateResilienceFailure(Throwable failure) {
        Throwable cause = unwrap(failure);
        if (cause instanceof PaymentException paymentException) {
            return paymentException;
        }
        if (cause instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            return new PaymentGatewayUnavailableException(
                    "A chamada ao Asaas foi interrompida", cause);
        }
        if (cause instanceof TimeoutException) {
            return new PaymentGatewayUnavailableException(
                    "O Asaas excedeu o tempo limite de resposta", cause);
        }
        if (cause instanceof CallNotPermittedException) {
            return new PaymentGatewayUnavailableException(
                    "O circuito do Asaas está temporariamente aberto", cause);
        }
        if (cause instanceof RejectedExecutionException) {
            return new PaymentGatewayUnavailableException(
                    "A capacidade de chamadas ao Asaas está temporariamente esgotada", cause);
        }
        return new PaymentGatewayUnavailableException(
                "O Asaas está temporariamente indisponível", cause);
    }

    private Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof ExecutionException || current instanceof CompletionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private boolean causedByOpenCircuit(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof CallNotPermittedException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
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
        validatePositiveDuration(properties.connectTimeout(), "ASAAS_CONNECT_TIMEOUT");
        validatePositiveDuration(properties.readTimeout(), "ASAAS_READ_TIMEOUT");
    }

    private static JdkClientHttpRequestFactory requestFactory(AsaasProperties properties) {
        validate(properties);
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.readTimeout());
        return factory;
    }

    private static void validatePositiveDuration(Duration duration, String property) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalStateException(property + " deve ser maior que zero");
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
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate dueDate,
            String description,
            String externalReference
    ) {
    }

    private record AsaasPaymentResponse(
            String id,
            String status,
            String invoiceUrl,
            BigDecimal value
    ) {
    }

    private record AsaasPaymentListResponse(List<AsaasPaymentResponse> data) {
    }
}
