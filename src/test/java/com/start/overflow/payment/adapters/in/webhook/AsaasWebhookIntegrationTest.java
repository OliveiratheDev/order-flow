package com.start.overflow.payment.adapters.in.webhook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "orderflow.security.jwt.secret=test-secret-with-at-least-thirty-two-bytes",
        "orderflow.bootstrap.admin.enabled=false",
        "orderflow.payment.asaas.api-key=sandbox-test-key",
        "orderflow.payment.asaas.webhook-token=webhook-token-with-at-least-32-characters"
})
@ActiveProfiles("payment-asaas")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AsaasWebhookIntegrationTest {
    private static final String TOKEN = "webhook-token-with-at-least-32-characters";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:16-alpine"));

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;

    @BeforeEach
    void resetState() {
        jdbc.execute("TRUNCATE TABLE webhook_event_log, payment, order_item, customer_order, "
                + "product, category, app_user RESTART IDENTITY CASCADE");
        Set<String> keys = redis.keys("*");
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    @Test
    void rejectsInvalidTokenBeforeRegisteringPayload() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/asaas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("asaas-access-token", "invalid")
                        .content(payload("evt_invalid", "PAYMENT_RECEIVED", "pay_123", "100.00")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type")
                        .value("https://orderflow.dev/errors/invalid-webhook-token"));

        assertThat(countEvents()).isZero();
    }

    @Test
    void rejectsMalformedAndUnsupportedPayloads() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/asaas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("asaas-access-token", TOKEN)
                        .content("{invalid"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/webhooks/asaas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("asaas-access-token", TOKEN)
                        .content(payload("evt_unknown", "PAYMENT_CREATED",
                                "pay_unknown", "100.00")))
                .andExpect(status().isBadRequest());

        assertThat(countEvents()).isZero();
    }

    @Test
    void confirmsPendingPaymentAndIgnoresDuplicateWithoutJwt() throws Exception {
        insertFixture("AWAITING_PAYMENT", "PENDING", "pay_123");

        send("evt_confirmed", "PAYMENT_RECEIVED", "pay_123", "100.00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        assertThat(paymentStatus()).isEqualTo("APPROVED");
        assertThat(orderStatus()).isEqualTo("PAID");
        Long paymentVersion = paymentVersion();

        send("evt_confirmed", "PAYMENT_RECEIVED", "pay_123", "100.00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DUPLICATE"));

        assertThat(eventStatus()).isEqualTo("DUPLICATE");
        assertThat(eventDuplicateCount()).isEqualTo(1L);
        assertThat(paymentVersion()).isEqualTo(paymentVersion);
    }

    @Test
    void acceptsConfirmedThenReceivedAsDistinctIdempotentEvents() throws Exception {
        insertFixture("AWAITING_PAYMENT", "PENDING", "pay_sequence");

        send("evt_confirm", "PAYMENT_CONFIRMED", "pay_sequence", "100.00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"));
        send("evt_received", "PAYMENT_RECEIVED", "pay_sequence", "100.00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        assertThat(paymentStatus()).isEqualTo("APPROVED");
        assertThat(orderStatus()).isEqualTo("PAID");
        assertThat(countEvents()).isEqualTo(2L);
    }

    @Test
    void concurrentDuplicateIsProcessedOnlyOnce() throws Exception {
        insertFixture("AWAITING_PAYMENT", "PENDING", "pay_concurrent");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> first = executor.submit(() -> perform(
                    "evt_concurrent", "PAYMENT_RECEIVED", "pay_concurrent", "100.00"));
            Future<MvcResult> second = executor.submit(() -> perform(
                    "evt_concurrent", "PAYMENT_RECEIVED", "pay_concurrent", "100.00"));

            List<String> responses = List.of(
                    first.get(10, TimeUnit.SECONDS).getResponse().getContentAsString(),
                    second.get(10, TimeUnit.SECONDS).getResponse().getContentAsString());

            assertThat(responses).anyMatch(body -> body.contains("PROCESSED"));
            assertThat(responses).anyMatch(body -> body.contains("DUPLICATE"));
            assertThat(countEvents()).isEqualTo(1L);
            assertThat(paymentStatus()).isEqualTo("APPROVED");
            assertThat(paymentVersion()).isEqualTo(1L);
            assertThat(eventDuplicateCount()).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void refusesPaymentAndRestoresReservedStock() throws Exception {
        insertFixture("AWAITING_PAYMENT", "PENDING", "pay_refused");

        send("evt_refused", "PAYMENT_CREDIT_CARD_CAPTURE_REFUSED",
                "pay_refused", "100.00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        assertThat(paymentStatus()).isEqualTo("REJECTED");
        assertThat(orderStatus()).isEqualTo("CANCELLED");
        assertThat(productStock()).isEqualTo(10L);
    }

    @Test
    void refundsApprovedPaymentAndCancelsPaidOrder() throws Exception {
        insertFixture("PAID", "APPROVED", "pay_refunded");

        send("evt_refunded", "PAYMENT_REFUNDED", "pay_refunded", "100.00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        assertThat(paymentStatus()).isEqualTo("REFUNDED");
        assertThat(orderStatus()).isEqualTo("CANCELLED");
        assertThat(productStock()).isEqualTo(10L);
    }

    @Test
    void recordsReconciliationWithoutChangingStateWhenRefundCannotCancelShippedOrder()
            throws Exception {
        insertFixture("SHIPPED", "APPROVED", "pay_shipped");

        send("evt_shipped_refund", "PAYMENT_REFUNDED", "pay_shipped", "100.00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECONCILIATION_REQUIRED"));

        assertThat(paymentStatus()).isEqualTo("APPROVED");
        assertThat(orderStatus()).isEqualTo("SHIPPED");
        assertThat(eventStatus()).isEqualTo("FAILED");
    }

    @Test
    void refusesDivergentAmountAndPreservesFinancialState() throws Exception {
        insertFixture("AWAITING_PAYMENT", "PENDING", "pay_divergent");

        send("evt_divergent", "PAYMENT_RECEIVED", "pay_divergent", "999.00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECONCILIATION_REQUIRED"));

        assertThat(paymentStatus()).isEqualTo("PENDING");
        assertThat(orderStatus()).isEqualTo("AWAITING_PAYMENT");
        assertThat(eventStatus()).isEqualTo("FAILED");
    }

    @Test
    void returnsServerErrorForOutOfOrderEventSoAsaasRetries() throws Exception {
        send("evt_early", "PAYMENT_RECEIVED", "pay_not_registered", "100.00")
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.type")
                        .value("https://orderflow.dev/errors/webhook-processing-failure"));

        assertThat(eventStatus()).isEqualTo("FAILED");

        insertFixture("AWAITING_PAYMENT", "PENDING", "pay_not_registered");
        send("evt_early", "PAYMENT_RECEIVED", "pay_not_registered", "100.00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        assertThat(countEvents()).isEqualTo(1L);
        assertThat(eventStatus()).isEqualTo("PROCESSED");
        assertThat(paymentStatus()).isEqualTo("APPROVED");
    }

    private ResultActions send(String eventId, String eventType,
                               String paymentId, String value) throws Exception {
        return mockMvc.perform(post("/api/v1/webhooks/asaas")
                .contentType(MediaType.APPLICATION_JSON)
                .header("asaas-access-token", TOKEN)
                .content(payload(eventId, eventType, paymentId, value)));
    }

    private MvcResult perform(String eventId, String eventType,
                              String paymentId, String value) throws Exception {
        return send(eventId, eventType, paymentId, value).andReturn();
    }

    private String payload(String eventId, String eventType, String paymentId, String value) {
        return """
                {"id":"%s","event":"%s","dateCreated":"2026-08-09 17:00:00",
                 "payment":{"object":"payment","id":"%s","value":%s,
                 "externalReference":"orderflow-order-1",
                 "futureField":{"ignored":true}}}
                """.formatted(eventId, eventType, paymentId, value);
    }

    private void insertFixture(String orderStatus, String paymentStatus, String externalId) {
        jdbc.update("""
                INSERT INTO app_user
                    (name, email, document, password_hash, role, active)
                VALUES ('Cliente', 'cliente@example.com', '52998224725', 'hash', 'CUSTOMER', true)
                """);
        jdbc.update("INSERT INTO category (name, slug, active) "
                + "VALUES ('Categoria', 'categoria', true)");
        jdbc.update("""
                INSERT INTO product (category_id, name, sku, price, stock, active)
                VALUES (1, 'Produto', 'SKU-1', 50.00, 8, true)
                """);
        jdbc.update("""
                INSERT INTO customer_order
                    (customer_id, status, subtotal, discount, total, shipping_address)
                VALUES (1, ?, 100.00, 0, 100.00, 'Rua de Teste, 100')
                """, orderStatus);
        jdbc.update("""
                INSERT INTO order_item
                    (order_id, product_id, product_name, sku, quantity, unit_price, line_total)
                VALUES (1, 1, 'Produto', 'SKU-1', 2, 50.00, 100.00)
                """);
        jdbc.update("""
                INSERT INTO payment
                    (order_id, customer_id, external_id, payment_url, amount, method, status)
                VALUES (1, 1, ?, 'https://sandbox.asaas.com/i/test', 100.00, 'PIX', ?)
                """, externalId, paymentStatus);
    }

    private long countEvents() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM webhook_event_log", Long.class);
    }

    private String paymentStatus() {
        return jdbc.queryForObject("SELECT status FROM payment LIMIT 1", String.class);
    }

    private Long paymentVersion() {
        return jdbc.queryForObject("SELECT version FROM payment LIMIT 1", Long.class);
    }

    private String orderStatus() {
        return jdbc.queryForObject("SELECT status FROM customer_order LIMIT 1", String.class);
    }

    private Long productStock() {
        return jdbc.queryForObject("SELECT stock FROM product LIMIT 1", Long.class);
    }

    private String eventStatus() {
        return jdbc.queryForObject("SELECT status FROM webhook_event_log LIMIT 1", String.class);
    }

    private Long eventDuplicateCount() {
        return jdbc.queryForObject(
                "SELECT duplicate_count FROM webhook_event_log LIMIT 1", Long.class);
    }
}
