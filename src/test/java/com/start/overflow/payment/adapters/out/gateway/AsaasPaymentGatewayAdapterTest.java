package com.start.overflow.payment.adapters.out.gateway;

import com.start.overflow.payment.domain.ChargeRequest;
import com.start.overflow.payment.domain.GatewayChargeStatus;
import com.start.overflow.payment.domain.Payer;
import com.start.overflow.payment.domain.PaymentAmount;
import com.start.overflow.payment.domain.PaymentGatewayRequestException;
import com.start.overflow.payment.domain.PaymentGatewayUnavailableException;
import com.start.overflow.payment.domain.PaymentMethod;
import com.start.overflow.payment.domain.PaymentRejectedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AsaasPaymentGatewayAdapterTest {
    private static final String BASE_URL = "https://api-sandbox.asaas.com/v3";
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-09T12:00:00Z"), ZoneOffset.UTC);

    private MockRestServiceServer server;
    private AsaasPaymentGatewayAdapter adapter;
    private ExecutorService executor;
    private RestClient.Builder builder;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        executor = Executors.newSingleThreadExecutor();
        adapter = newAdapter(builder, properties("sandbox-key"));
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void createsPayerAndPendingChargeWithSafeHeadersAndInternalReferences() {
        expectCustomerSearch("{\"data\":[]}");
        server.expect(requestTo(BASE_URL + "/customers"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("access_token", "sandbox-key"))
                .andExpect(header(HttpHeaders.USER_AGENT, "OrderFlow/1.0 (test)"))
                .andExpect(header("X-Correlation-Id", "correlation-123"))
                .andExpect(content().json("""
                        {
                          "name":"Maria da Silva",
                          "cpfCnpj":"52998224725",
                          "email":"maria@example.com",
                          "externalReference":"7",
                          "notificationDisabled":true
                        }
                        """))
                .andRespond(withSuccess("{\"id\":\"cus_123\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/payments"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("access_token", "sandbox-key"))
                .andExpect(header("X-Correlation-Id", "correlation-123"))
                .andExpect(content().json("""
                        {
                          "customer":"cus_123",
                          "billingType":"PIX",
                          "value":149.90,
                          "dueDate":"2026-08-12",
                          "description":"Pedido OrderFlow #10",
                          "externalReference":"orderflow-order-10"
                        }
                        """))
                .andRespond(withSuccess("""
                        {"id":"pay_123","status":"PENDING",
                         "invoiceUrl":"https://sandbox.asaas.com/i/123"}
                        """, MediaType.APPLICATION_JSON));

        var result = adapter.createCharge(request());

        assertThat(result.status()).isEqualTo(GatewayChargeStatus.PENDING);
        assertThat(result.externalId()).isEqualTo("pay_123");
        assertThat(result.paymentUrl()).isEqualTo("https://sandbox.asaas.com/i/123");
        server.verify();
    }

    @Test
    void reusesExistingPayerAndMapsConfirmedChargeAsApproved() {
        expectCustomerSearch("{\"data\":[{\"id\":\"cus_existing\"}]}");
        server.expect(requestTo(BASE_URL + "/payments"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"customer\":\"cus_existing\"}"))
                .andRespond(withSuccess(
                        "{\"id\":\"pay_approved\",\"status\":\"CONFIRMED\"}",
                        MediaType.APPLICATION_JSON));

        var result = adapter.createCharge(request());

        assertThat(result.status()).isEqualTo(GatewayChargeStatus.APPROVED);
        server.verify();
    }

    @Test
    void retrievesChargeAndMapsReceivedStatus() {
        server.expect(requestTo(BASE_URL + "/payments/pay_123"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("access_token", "sandbox-key"))
                .andRespond(withSuccess(
                        "{\"id\":\"pay_123\",\"status\":\"RECEIVED\"}",
                        MediaType.APPLICATION_JSON));

        var result = adapter.getCharge("pay_123");

        assertThat(result.status()).isEqualTo(GatewayChargeStatus.APPROVED);
        server.verify();
    }

    @Test
    void cancelsChargeInAsaas() {
        server.expect(requestTo(BASE_URL + "/payments/pay_123"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("access_token", "sandbox-key"))
                .andRespond(withSuccess());

        adapter.cancelCharge("pay_123");

        server.verify();
    }

    @Test
    void translatesInvalidChargeIntoDomainRejectionWithoutLeakingProviderBody() {
        expectCustomerSearch("{\"data\":[{\"id\":\"cus_existing\"}]}");
        server.expect(requestTo(BASE_URL + "/payments"))
                .andRespond(withBadRequest().body(
                        "{\"errors\":[{\"description\":\"internal provider detail\"}]}"));

        assertThatThrownBy(() -> adapter.createCharge(request()))
                .isInstanceOf(PaymentRejectedException.class)
                .hasMessage("O Asaas rejeitou os dados da cobrança")
                .hasMessageNotContaining("internal provider detail");
        server.verify();
    }

    @Test
    void retriesOnlySafeReconciliationAfterTransientCreateFailure() {
        server.expect(requestTo(BASE_URL + "/customers?externalReference=7&limit=1"))
                .andRespond(withServerError());
        expectFailedReconciliation();
        expectFailedReconciliation();
        expectFailedReconciliation();

        assertThatThrownBy(() -> adapter.createCharge(request()))
                .isInstanceOf(PaymentGatewayUnavailableException.class)
                .hasMessage("O Asaas está temporariamente indisponível");
        server.verify();
    }

    @Test
    void reconcilesByExternalReferenceWithoutRepeatingPaymentCreation() {
        expectCustomerSearch("{\"data\":[{\"id\":\"cus_existing\"}]}");
        server.expect(requestTo(BASE_URL + "/payments"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());
        server.expect(requestTo(BASE_URL
                        + "/payments?externalReference=orderflow-order-10&limit=2"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"data":[{"id":"pay_recovered","status":"PENDING",
                         "invoiceUrl":"https://sandbox.asaas.com/i/recovered"}]}
                        """, MediaType.APPLICATION_JSON));

        var result = adapter.createCharge(request());

        assertThat(result.externalId()).isEqualTo("pay_recovered");
        assertThat(result.status()).isEqualTo(GatewayChargeStatus.PENDING);
        server.verify();
    }

    @Test
    void doesNotRetryBusiness4xxResponse() {
        server.expect(requestTo(BASE_URL + "/payments/pay_123"))
                .andRespond(withBadRequest());

        assertThatThrownBy(() -> adapter.getCharge("pay_123"))
                .isInstanceOf(PaymentRejectedException.class);

        server.verify();
    }

    @Test
    void doesNotRetryAuthentication4xxResponse() {
        server.expect(requestTo(BASE_URL + "/payments/pay_123"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> adapter.getCharge("pay_123"))
                .isInstanceOf(PaymentGatewayRequestException.class);

        server.verify();
    }

    @Test
    void opensCircuitFailsFastAndClosesAfterGatewayRecovers() {
        CircuitBreakerConfig aggressiveConfig = CircuitBreakerConfig.custom()
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50)
                .permittedNumberOfCallsInHalfOpenState(1)
                .recordExceptions(PaymentGatewayUnavailableException.class)
                .build();
        CircuitBreaker createCircuit = CircuitBreaker.of("create-aggressive", aggressiveConfig);
        adapter = adapterWith(createCircuit, executor, Duration.ofSeconds(2));

        expectFailedCreateAndEmptyReconciliation();
        expectFailedCreateAndEmptyReconciliation();
        expectCustomerSearch("{\"data\":[{\"id\":\"cus_recovered\"}]}");
        server.expect(requestTo(BASE_URL + "/payments"))
                .andRespond(withSuccess(
                        "{\"id\":\"pay_recovered\",\"status\":\"PENDING\"}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.createCharge(request()))
                .isInstanceOf(PaymentGatewayUnavailableException.class);
        assertThatThrownBy(() -> adapter.createCharge(request()))
                .isInstanceOf(PaymentGatewayUnavailableException.class);

        assertThat(createCircuit.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        assertThatThrownBy(() -> adapter.createCharge(request()))
                .isInstanceOf(PaymentGatewayUnavailableException.class)
                .hasMessageContaining("circuito");

        createCircuit.transitionToHalfOpenState();
        assertThat(adapter.createCharge(request()).externalId()).isEqualTo("pay_recovered");
        assertThat(createCircuit.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        server.verify();
    }

    @Test
    void timeLimiterStopsAnOperationThatDoesNotComplete() {
        NeverCompletingExecutor neverCompletingExecutor = new NeverCompletingExecutor();
        adapter = adapterWith(
                CircuitBreaker.ofDefaults("create-timeout"),
                neverCompletingExecutor,
                Duration.ofMillis(20));

        assertThatThrownBy(() -> adapter.createCharge(request()))
                .isInstanceOf(PaymentGatewayUnavailableException.class)
                .hasMessageContaining("tempo limite");

        neverCompletingExecutor.shutdownNow();
        server.verify();
    }

    @Test
    void refusesToStartProfileWithoutApiKey() {
        RestClient.Builder builder = RestClient.builder();

        assertThatThrownBy(() -> newAdapter(builder, properties(" ")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ASAAS_API_KEY");
    }

    private void expectCustomerSearch(String response) {
        server.expect(requestTo(BASE_URL + "/customers?externalReference=7&limit=1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("access_token", "sandbox-key"))
                .andExpect(header(HttpHeaders.USER_AGENT, "OrderFlow/1.0 (test)"))
                .andExpect(header("X-Correlation-Id", "correlation-123"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    }

    private void expectFailedReconciliation() {
        server.expect(requestTo(BASE_URL
                        + "/payments?externalReference=orderflow-order-10&limit=2"))
                .andRespond(withServerError());
    }

    private void expectFailedCreateAndEmptyReconciliation() {
        server.expect(requestTo(BASE_URL + "/customers?externalReference=7&limit=1"))
                .andRespond(withServerError());
        expectEmptyReconciliation();
    }

    private void expectEmptyReconciliation() {
        server.expect(requestTo(BASE_URL
                        + "/payments?externalReference=orderflow-order-10&limit=2"))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));
    }

    private ChargeRequest request() {
        return new ChargeRequest(10L,
                new Payer(7L, "Maria da Silva", "maria@example.com", "52998224725"),
                new PaymentAmount(new BigDecimal("149.90")),
                PaymentMethod.PIX,
                "correlation-123");
    }

    private AsaasProperties properties(String apiKey) {
        return new AsaasProperties(BASE_URL, apiKey,
                "OrderFlow/1.0 (test)", 3, "", Duration.ofSeconds(3),
                Duration.ofSeconds(3));
    }

    private AsaasPaymentGatewayAdapter newAdapter(
            RestClient.Builder builder, AsaasProperties properties) {
        CircuitBreakerConfig circuitConfig = CircuitBreakerConfig.custom()
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50)
                .recordExceptions(PaymentGatewayUnavailableException.class)
                .build();
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ZERO)
                .retryExceptions(PaymentGatewayUnavailableException.class)
                .build();
        TimeLimiterConfig timeLimiterConfig = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(2))
                .cancelRunningFuture(true)
                .build();
        return new AsaasPaymentGatewayAdapter(
                builder,
                properties,
                CLOCK,
                CircuitBreaker.of("create", circuitConfig),
                CircuitBreaker.of("query", circuitConfig),
                CircuitBreaker.of("cancel", circuitConfig),
                Retry.of("query", retryConfig),
                TimeLimiter.of(timeLimiterConfig),
                TimeLimiter.of(timeLimiterConfig),
                TimeLimiter.of(timeLimiterConfig),
                executor);
    }

    private AsaasPaymentGatewayAdapter adapterWith(
            CircuitBreaker createCircuit, ExecutorService selectedExecutor,
            Duration timeout) {
        RetryConfig noRetry = RetryConfig.custom().maxAttempts(1).build();
        TimeLimiterConfig timeLimiterConfig = TimeLimiterConfig.custom()
                .timeoutDuration(timeout)
                .cancelRunningFuture(true)
                .build();
        return new AsaasPaymentGatewayAdapter(
                builder,
                properties("sandbox-key"),
                CLOCK,
                createCircuit,
                CircuitBreaker.ofDefaults("query-special"),
                CircuitBreaker.ofDefaults("cancel-special"),
                Retry.of("query-special", noRetry),
                TimeLimiter.of(timeLimiterConfig),
                TimeLimiter.of(timeLimiterConfig),
                TimeLimiter.of(timeLimiterConfig),
                selectedExecutor);
    }

    private static final class NeverCompletingExecutor extends AbstractExecutorService {
        private boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public java.util.List<Runnable> shutdownNow() {
            shutdown = true;
            return java.util.List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown;
        }

        @Override
        public void execute(Runnable command) {
            // Simula uma tarefa aceita que nunca recebe tempo de CPU.
        }
    }
}
