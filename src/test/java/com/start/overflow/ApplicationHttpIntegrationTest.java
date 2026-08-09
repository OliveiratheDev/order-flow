package com.start.overflow;

import com.start.overflow.identity.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import java.util.Set;

import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

        long paymentId = id(mockMvc.perform(withToken(postJson("/api/v1/payments", """
                        {"orderId":%d,"method":"PIX"}
                        """.formatted(orderId)), customerToken)
                        .header("X-Correlation-Id", "complete-flow-correlation"))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Correlation-Id", "complete-flow-correlation"))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andReturn());

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
                        {"name":"Cliente Teste","email":"%s","password":"Customer123!"}
                        """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();
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
