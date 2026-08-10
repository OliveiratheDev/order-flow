package com.start.overflow;

import com.start.overflow.identity.service.UserService;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "orderflow.security.jwt.secret=test-secret-with-at-least-thirty-two-bytes",
        "orderflow.bootstrap.admin.enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ApplicationHttpIntegrationTest {
    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String ADMIN_PASSWORD = "AdminPass123!";

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
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @Autowired UserService userService;
    @Autowired MeterRegistry meterRegistry;

    @BeforeEach
    void resetState() {
        jdbc.execute("TRUNCATE TABLE payment, order_item, customer_order, product, category, app_user "
                + "RESTART IDENTITY CASCADE");
        Set<String> keys = redis.keys("*");
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
        userService.createAdminIfMissing("Administrador", ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    @Test
    void completeAuthenticatedBusinessFlowWorksThroughHttp() throws Exception {
        String customerToken = token(registerCustomer("cliente@example.com"));
        String adminToken = token(login(ADMIN_EMAIL, ADMIN_PASSWORD));

        mockMvc.perform(get("/actuator/circuitbreakers")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.circuitBreakers.length()").value(3));
        mockMvc.perform(get("/actuator/metrics/resilience4j.circuitbreaker.state")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("resilience4j.circuitbreaker.state"));

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("cliente@example.com"));
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", bearer(customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CUSTOMER"));

        long categoryId = id(mockMvc.perform(withToken(postJson("/api/v1/categories", """
                        {"name":"Eletrônicos","description":"Itens eletrônicos"}
                        """), adminToken))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/categories/1")))
                .andReturn());

        mockMvc.perform(get("/api/v1/categories/{id}", categoryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("eletronicos"));
        mockMvc.perform(get("/api/v1/categories").param("name", "Eletr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(withToken(putJson("/api/v1/categories/" + categoryId, """
                        {"name":"Tecnologia","description":"Catálogo atualizado"}
                        """), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("tecnologia"));
        mockMvc.perform(withToken(patch("/api/v1/categories/{id}/deactivate", categoryId), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(withToken(patch("/api/v1/categories/{id}/activate", categoryId), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        long productId = id(mockMvc.perform(withToken(postJson("/api/v1/products", """
                        {"categoryId":%d,"name":"Teclado","sku":"tec-001",
                         "description":"Teclado mecânico","price":199.90,"stock":10}
                        """.formatted(categoryId)), adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("TEC-001"))
                .andReturn());

        mockMvc.perform(get("/api/v1/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(10));
        mockMvc.perform(get("/api/v1/products").param("categoryId", Long.toString(categoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/v1/categories/{id}/products", categoryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(productId));
        mockMvc.perform(withToken(putJson("/api/v1/products/" + productId, """
                        {"categoryId":%d,"name":"Teclado Pro",
                         "description":"Versão atualizada","price":249.90}
                        """.formatted(categoryId)), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Teclado Pro"));
        mockMvc.perform(withToken(patchJson("/api/v1/products/" + productId + "/stock",
                        "{\"quantity\":5}"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(15));
        mockMvc.perform(withToken(patch("/api/v1/products/{id}/deactivate", productId), adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(withToken(patch("/api/v1/products/{id}/activate", productId), adminToken))
                .andExpect(status().isOk());

        long orderId = id(mockMvc.perform(withToken(postJson("/api/v1/orders", """
                        {"shippingAddress":"Rua das Flores, 100",
                         "items":[{"productId":%d,"quantity":2}]}
                        """.formatted(productId))
                        .header("Idempotency-Key", "complete-flow-order"), customerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"))
                .andReturn());

        mockMvc.perform(withToken(get("/api/v1/orders/{id}", orderId), customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(499.8));
        mockMvc.perform(withToken(get("/api/v1/orders").param("size", "500"), customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        long gatewayCalls = meterRegistry.get("orderflow.payment.gateway.duration")
                .tags("gateway", "simulated", "operation", "create")
                .timer()
                .count();
        long paymentId = id(mockMvc.perform(withToken(postJson("/api/v1/payments", """
                        {"orderId":%d,"method":"PIX"}
                        """.formatted(orderId)), customerToken)
                        .header("X-Correlation-Id", "complete-flow-correlation"))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Correlation-Id", "complete-flow-correlation"))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andReturn());
        assertThat(meterRegistry.get("orderflow.payment.gateway.duration")
                .tags("gateway", "simulated", "operation", "create")
                .timer()
                .count()).isEqualTo(gatewayCalls + 1);

        mockMvc.perform(withToken(get("/api/v1/payments/{id}", paymentId), customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId));
        mockMvc.perform(withToken(patch("/api/v1/orders/{id}/ship", orderId), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"));
        mockMvc.perform(withToken(patch("/api/v1/orders/{id}/deliver", orderId), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));

        long cancelledOrderId = id(mockMvc.perform(withToken(postJson("/api/v1/orders", """
                        {"shippingAddress":"Rua B, 200",
                         "items":[{"productId":%d,"quantity":1}]}
                        """.formatted(productId))
                        .header("Idempotency-Key", "cancelled-flow-order"), customerToken))
                .andExpect(status().isCreated())
                .andReturn());
        mockMvc.perform(withToken(patch("/api/v1/orders/{id}/cancel", cancelledOrderId), customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(withToken(delete("/api/v1/products/{id}", productId), adminToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(withToken(delete("/api/v1/categories/{id}", categoryId), adminToken))
                .andExpect(status().isNoContent());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("productFilterScenarios")
    void deveCombinarFiltros_quandoBuscarProdutos(String scenario,
                                                   Map<String, String> parameters,
                                                   int expectedElements) throws Exception {
        seedProductsForFiltering();
        MockHttpServletRequestBuilder request = get("/api/v1/products");
        parameters.forEach(request::param);

        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(expectedElements));
    }

    @Test
    void deveLimitarPagina_quandoTamanhoSuperarCem() throws Exception {
        seedProductsForFiltering();

        mockMvc.perform(get("/api/v1/products").param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void deveRetornarErro_quandoFaixaDePrecoForInvalida() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .param("minPrice", "200.00")
                        .param("maxPrice", "100.00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("A faixa de preço informada é inválida"));
    }

    @Test
    void actuatorExposesOnlyOperationalEndpoints() throws Exception {
        String adminToken = token(login(ADMIN_EMAIL, ADMIN_PASSWORD));
        meterRegistry.get("orderflow.orders.created")
                .tag("status", "created")
                .counter()
                .increment();
        meterRegistry.get("orderflow.payments.failed")
                .tags("gateway", "asaas", "reason", "rejected")
                .counter()
                .increment();
        meterRegistry.get("orderflow.payment.gateway.duration")
                .tags("gateway", "asaas", "operation", "create")
                .timer()
                .record(Duration.ofMillis(50));
        meterRegistry.get("orderflow.reconciliation.divergences")
                .tag("type", "charge_data")
                .counter()
                .increment();

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("orderflow_orders_total")))
                .andExpect(content().string(containsString("orderflow_payments_failed_total")))
                .andExpect(content().string(containsString(
                        "orderflow_payment_gateway_duration_seconds")))
                .andExpect(content().string(containsString(
                        "orderflow_reconciliation_divergences_total")))
                .andExpect(content().string(containsString("orderflow_dlq_depth")));
        mockMvc.perform(get("/actuator/metrics/orderflow.orders.created")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("orderflow.orders.created"));
        mockMvc.perform(get("/actuator/info")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/metrics")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/circuitbreakers")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/env")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/heapdump")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/beans")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void securityValidationAndDomainErrorsUseSafeProblemDetails() throws Exception {
        String customerToken = token(registerCustomer("outro@example.com"));
        String adminToken = token(login(ADMIN_EMAIL, ADMIN_PASSWORD));

        mockMvc.perform(postJson("/api/v1/categories", "{\"name\":\"Sem token\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(withToken(postJson("/api/v1/categories", "{\"name\":\"Cliente\"}"),
                        customerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(postJson("/api/v1/auth/register", "{invalid-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://orderflow.dev/errors/malformed-request"));
        mockMvc.perform(postJson("/api/v1/auth/register", """
                        {"name":"Sem Documento","email":"sem-documento@example.com",
                         "password":"Customer123!"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("document"));
        mockMvc.perform(postJson("/api/v1/auth/login", """
                        {"email":"outro@example.com","password":"senha-incorreta"}
                        """))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/categories/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://orderflow.dev/errors/type-mismatch"));
        mockMvc.perform(get("/api/v1/categories/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://orderflow.dev/errors/resource-not-found"));
        mockMvc.perform(withToken(postJson("/api/v1/categories", "{\"name\":\"\"}"), adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"));

        long categoryId = id(mockMvc.perform(withToken(postJson("/api/v1/categories",
                        "{\"name\":\"Livros\"}"), adminToken))
                .andExpect(status().isCreated())
                .andReturn());
        mockMvc.perform(withToken(postJson("/api/v1/categories",
                        "{\"name\":\"Livros\"}"), adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://orderflow.dev/errors/business-rule"));

        long productId = id(mockMvc.perform(withToken(postJson("/api/v1/products", """
                        {"categoryId":%d,"name":"Livro","sku":"LIV-1",
                         "price":50.00,"stock":1}
                        """.formatted(categoryId)), adminToken))
                .andExpect(status().isCreated())
                .andReturn());
        mockMvc.perform(withToken(postJson("/api/v1/products", """
                        {"categoryId":%d,"name":"Livro duplicado","sku":"LIV-1",
                         "price":60.00,"stock":1}
                        """.formatted(categoryId)), adminToken))
                .andExpect(status().isConflict());

        long orderId = id(mockMvc.perform(withToken(postJson("/api/v1/orders", """
                        {"shippingAddress":"Rua C, 300",
                         "items":[{"productId":%d,"quantity":1}]}
                        """.formatted(productId))
                        .header("Idempotency-Key", "invalid-transition-order"), customerToken))
                .andExpect(status().isCreated())
                .andReturn());
        mockMvc.perform(withToken(patch("/api/v1/orders/{id}/ship", orderId), adminToken))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("https://orderflow.dev/errors/invalid-transition"));
        mockMvc.perform(withToken(postJson("/api/v1/payments", """
                        {"orderId":999,"method":"PIX"}
                        """), customerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    private MvcResult registerCustomer(String email) throws Exception {
        return mockMvc.perform(postJson("/api/v1/auth/register", """
                        {"name":"Cliente Teste","email":"%s","document":"52998224725",
                         "password":"Customer123!"}
                        """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();
    }

    static Stream<Arguments> productFilterScenarios() {
        return Stream.of(
                Arguments.of("sem filtros", Map.of(), 3),
                Arguments.of("nome", Map.of("name", "alpha"), 1),
                Arguments.of("categoria", Map.of("categoryId", "1"), 2),
                Arguments.of("preço mínimo", Map.of("minPrice", "100.00"), 2),
                Arguments.of("preço máximo", Map.of("maxPrice", "200.00"), 2),
                Arguments.of("ativo", Map.of("active", "true"), 2),
                Arguments.of("todos", Map.of(
                        "name", "alpha",
                        "categoryId", "1",
                        "minPrice", "40.00",
                        "maxPrice", "60.00",
                        "active", "true"), 1),
                Arguments.of("faixa e estado", Map.of(
                        "minPrice", "100.00",
                        "maxPrice", "200.00",
                        "active", "false"), 1),
                Arguments.of("categoria e estado", Map.of(
                        "categoryId", "2",
                        "active", "true"), 1));
    }

    private void seedProductsForFiltering() {
        Long firstCategory = jdbc.queryForObject("""
                INSERT INTO category (name, slug, active)
                VALUES (?, ?, true)
                RETURNING id
                """, Long.class, "Casa", "casa");
        Long secondCategory = jdbc.queryForObject("""
                INSERT INTO category (name, slug, active)
                VALUES (?, ?, true)
                RETURNING id
                """, Long.class, "Tecnologia", "tecnologia");
        insertProduct(firstCategory, "Alpha", "ALPHA-1", "50.00", true);
        insertProduct(firstCategory, "Beta", "BETA-1", "150.00", false);
        insertProduct(secondCategory, "Gamma", "GAMMA-1", "250.00", true);
    }

    private void insertProduct(Long categoryId, String name, String sku,
                               String price, boolean active) {
        jdbc.update("""
                INSERT INTO product (category_id, name, sku, price, stock, active)
                VALUES (?, ?, ?, ?, 10, ?)
                """, categoryId, name, sku, new BigDecimal(price), active);
    }

    private MvcResult login(String email, String password) throws Exception {
        return mockMvc.perform(postJson("/api/v1/auth/login", """
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String token(MvcResult result) throws Exception {
        return json(result).get("accessToken").stringValue();
    }

    private long id(MvcResult result) throws Exception {
        return json(result).get("id").asLong();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request,
                                                     String token) {
        return request.header("Authorization", bearer(token));
    }

    private MockHttpServletRequestBuilder postJson(String path, String body) {
        return post(path).contentType("application/json").content(body);
    }

    private MockHttpServletRequestBuilder putJson(String path, String body) {
        return put(path).contentType("application/json").content(body);
    }

    private MockHttpServletRequestBuilder patchJson(String path, String body) {
        return patch(path).contentType("application/json").content(body);
    }
}
