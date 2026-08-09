package com.start.overflow.shared.idempotency;

import com.start.overflow.shared.exception.BusinessRuleException;
import com.start.overflow.shared.exception.IdempotencyConflictException;
import com.start.overflow.shared.exception.IdempotencyPayloadMismatchException;
import com.start.overflow.shared.exception.IdempotencyUnavailableException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyAspectTest {
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> values;
    @Mock ProceedingJoinPoint joinPoint;
    @Mock Idempotent annotation;
    @Mock Authentication authentication;

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private IdempotencyAspect aspect;
    private Map<String, Object> payload;

    @BeforeEach
    void setUp() {
        aspect = new IdempotencyAspect(redis, objectMapper,
                new IdempotencyProperties(Duration.ofSeconds(30), Duration.ofHours(24)));
        payload = Map.of("productId", 10, "quantity", 2);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");
        request.addHeader(IdempotencyAspect.HEADER, "order-attempt-1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("7");
        when(redis.opsForValue()).thenReturn(values);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"order-attempt-1", payload});
        when(annotation.payloadArgument()).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    @Test
    void newKeyIsClaimedAtomicallyAndStoredForTwentyFourHours() throws Throwable {
        when(values.setIfAbsent(eq("idem:7:order-attempt-1"), anyString(),
                eq(Duration.ofSeconds(30)))).thenReturn(true);
        when(joinPoint.proceed()).thenReturn(ResponseEntity.created(URI.create("/api/v1/orders/42"))
                .body(Map.of("id", 42)));

        Object result = aspect.enforce(joinPoint, annotation);

        assertThat(((ResponseEntity<?>) result).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(values).set(eq("idem:7:order-attempt-1"), contains("\"DONE\""),
                eq(Duration.ofHours(24)));
    }

    @Test
    void completedKeyReplaysTheSameBodyWithStatusOk() throws Throwable {
        String requestHash = hash(objectMapper.writeValueAsString(payload));
        IdempotencyRecord record = IdempotencyRecord.done(requestHash, 201,
                "{\"id\":42}", "/api/v1/orders/42");
        when(values.setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(30))))
                .thenReturn(false);
        when(values.get("idem:7:order-attempt-1"))
                .thenReturn(objectMapper.writeValueAsString(record));

        ResponseEntity<?> response = (ResponseEntity<?>) aspect.enforce(joinPoint, annotation);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getLocation()).isEqualTo(URI.create("/api/v1/orders/42"));
        assertThat(response.getBody().toString()).contains("\"id\":42");
        verify(joinPoint, never()).proceed();
    }

    @Test
    void sameKeyWithDifferentPayloadIsRejected() throws Exception {
        IdempotencyRecord record = IdempotencyRecord.done(hash("outro-payload"), 201,
                "{\"id\":42}", "/api/v1/orders/42");
        when(values.setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(30))))
                .thenReturn(false);
        when(values.get(anyString())).thenReturn(objectMapper.writeValueAsString(record));

        assertThatThrownBy(() -> aspect.enforce(joinPoint, annotation))
                .isInstanceOf(IdempotencyPayloadMismatchException.class);
    }

    @Test
    void concurrentRequestReceivesConflict() throws Exception {
        String requestHash = hash(objectMapper.writeValueAsString(payload));
        when(values.setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(30))))
                .thenReturn(false);
        when(values.get(anyString())).thenReturn(objectMapper.writeValueAsString(
                IdempotencyRecord.inProgress(requestHash)));

        assertThatThrownBy(() -> aspect.enforce(joinPoint, annotation))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void processingFailureReleasesTheKeyForRetry() throws Throwable {
        when(values.setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(30))))
                .thenReturn(true);
        when(joinPoint.proceed()).thenThrow(new BusinessRuleException("Falha esperada"));

        assertThatThrownBy(() -> aspect.enforce(joinPoint, annotation))
                .isInstanceOf(BusinessRuleException.class);
        verify(redis).delete("idem:7:order-attempt-1");
    }

    @Test
    void redisFailureReturnsServiceUnavailableInsteadOfCreatingWithoutGuarantee() {
        when(values.setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(30))))
                .thenThrow(new RedisConnectionFailureException("Redis offline"));

        assertThatThrownBy(() -> aspect.enforce(joinPoint, annotation))
                .isInstanceOf(IdempotencyUnavailableException.class);
    }

    private String hash(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
