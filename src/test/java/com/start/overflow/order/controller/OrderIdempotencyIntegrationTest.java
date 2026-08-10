package com.start.overflow.order.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "orderflow.security.jwt.secret=test-secret-with-at-least-thirty-two-bytes",
        "orderflow.bootstrap.admin.enabled=false",
        "spring.cache.type=none"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class OrderIdempotencyIntegrationTest {
    private static final String REQUEST = """
            {
              "shippingAddress": "Rua A, 123",
              "items": [{"productId": 20, "quantity": 2}]
            }
            """;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:16-alpine"));

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;

    @BeforeEach
    void seedDatabase() {
        jdbc.execute("TRUNCATE TABLE payment, order_item, customer_order, product, category, app_user "
                + "RESTART IDENTITY CASCADE");
        jdbc.update("""
                INSERT INTO app_user
                    (id, name, email, password_hash, role, active, created_at, updated_at, version)
                VALUES (7, 'Cliente Teste', 'cliente@example.com', '$2a$12$hash',
                        'CUSTOMER', TRUE, NOW(), NOW(), 0)
                """);
        jdbc.update("""
                INSERT INTO category
                    (id, name, slug, active, created_at, updated_at, version)
                VALUES (10, 'Categoria', 'categoria', TRUE, NOW(), NOW(), 0)
                """);
        jdbc.update("""
                INSERT INTO product
                    (id, category_id, name, sku, price, stock, active, created_at, updated_at, version)
                VALUES (20, 10, 'Produto', 'SKU-20', 49.90, 10, TRUE, NOW(), NOW(), 0)
                """);
        jdbc.update("""
          INSERT INTO product
              (id, category_id, name, sku, price, stock, active, created_at, updated_at, version)
          VALUES (21, 10, 'Produto B', 'SKU-21', 29.90, 1, TRUE, NOW(), NOW(), 0)
          """);
        Set<String> keys = redis.keys("idem:*");
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    @Test
    void identicalRetryReturnsSameOrderWithoutDuplicatingDatabaseOrStock() throws Exception {
        String firstBody = mockMvc.perform(authenticatedPost("retry-order-1", REQUEST)
                        .header("X-Correlation-Id", "integration-order-1"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/api/v1/orders/1")))
                .andExpect(header().string("X-Correlation-Id", "integration-order-1"))
                .andExpect(jsonPath("$.id").value(1))
                .andReturn().getResponse().getContentAsString();

        String replayBody = mockMvc.perform(authenticatedPost("retry-order-1", REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andReturn().getResponse().getContentAsString();

        assertThat(replayBody).isEqualTo(firstBody);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_order", Long.class))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT stock FROM product WHERE id = 20", Integer.class))
                .isEqualTo(8);
        Long ttlSeconds = redis.getExpire("idem:7:retry-order-1", TimeUnit.SECONDS);
        assertThat(ttlSeconds).isBetween(86_300L, 86_400L);
    }

    @Test
    void reusedKeyWithDifferentPayloadReturnsUnprocessableEntity() throws Exception {
        mockMvc.perform(authenticatedPost("retry-order-2", REQUEST))
                .andExpect(status().isCreated());

        mockMvc.perform(authenticatedPost("retry-order-2", REQUEST.replace("\"quantity\": 2",
                        "\"quantity\": 3")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type")
                        .value("https://orderflow.dev/errors/idempotency-payload-mismatch"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_order", Long.class))
                .isEqualTo(1L);
    }

    @Test
    void missingKeyReturnsStandardBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .with(jwt().jwt(token -> token.subject("7"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType("application/json")
                        .content(REQUEST))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type")
                        .value("https://orderflow.dev/errors/idempotency-key-required"));
    }

    @Test
    void concurrentRequestsNeverCreateTwoOrders() throws Exception {
        CompletableFuture<Integer> first = CompletableFuture.supplyAsync(
                () -> performStatus("concurrent-order-1"));
        CompletableFuture<Integer> second = CompletableFuture.supplyAsync(
                () -> performStatus("concurrent-order-1"));

        int firstStatus = first.get(20, TimeUnit.SECONDS);
        int secondStatus = second.get(20, TimeUnit.SECONDS);

        assertThat(Set.of(firstStatus, secondStatus)).contains(201);
        assertThat(firstStatus == 200 || firstStatus == 201 || firstStatus == 409).isTrue();
        assertThat(secondStatus == 200 || secondStatus == 201 || secondStatus == 409).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_order", Long.class))
                .isEqualTo(1L);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            authenticatedPost(String key, String body) {
        return post("/api/v1/orders")
                .with(jwt().jwt(token -> token.subject("7"))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .header("Idempotency-Key", key)
                .contentType("application/json")
                .content(body);
    }

    private int performStatus(String key) {
        try {
            return mockMvc.perform(authenticatedPost(key, REQUEST))
                    .andReturn().getResponse().getStatus();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void rejectedOrderDoesNotChangeStockOfEarlierProduct() throws Exception {
        String request = """
              {
                "shippingAddress": "Rua B, 456",
                "items": [
                  {"productId": 20, "quantity": 2},
                  {"productId": 21, "quantity": 2}
                ]
              }
              """;

        mockMvc.perform(authenticatedPost("stock-rollback-1", request))
                .andExpect(status().isConflict());

        assertThat(jdbc.queryForObject(
                "SELECT stock FROM product WHERE id = 20", Integer.class))
                .isEqualTo(10);

        assertThat(jdbc.queryForObject(
                "SELECT stock FROM product WHERE id = 21", Integer.class))
                .isEqualTo(1);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM customer_order", Long.class))
                .isEqualTo(0L);
    }
}
