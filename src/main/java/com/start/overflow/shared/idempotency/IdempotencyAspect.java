package com.start.overflow.shared.idempotency;

import com.start.overflow.shared.exception.IdempotencyConflictException;
import com.start.overflow.shared.exception.IdempotencyKeyRequiredException;
import com.start.overflow.shared.exception.IdempotencyPayloadMismatchException;
import com.start.overflow.shared.exception.IdempotencyUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class IdempotencyAspect {
    public static final String HEADER = "Idempotency-Key";
    private static final Logger log = LoggerFactory.getLogger(IdempotencyAspect.class);
    private static final Pattern VALID_KEY = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final IdempotencyProperties properties;

    public IdempotencyAspect(StringRedisTemplate redis, ObjectMapper objectMapper,
                             IdempotencyProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Around("@annotation(idempotent)")
    public Object enforce(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        HttpServletRequest request = currentRequest();
        String idempotencyKey = validateKey(request.getHeader(HEADER));
        String userId = currentUserId();
        Object payload = payload(joinPoint.getArgs(), idempotent.payloadArgument());
        String requestHash = sha256(serialize(payload));
        String redisKey = "idem:" + userId + ":" + idempotencyKey;

        for (int attempt = 0; attempt < 2; attempt++) {
            if (claim(redisKey, requestHash)) {
                return processNew(joinPoint, redisKey, requestHash);
            }
            IdempotencyRecord existing = read(redisKey);
            if (existing == null) {
                continue;
            }
            if (!requestHash.equals(existing.requestHash())) {
                throw new IdempotencyPayloadMismatchException();
            }
            if (existing.state() == IdempotencyRecord.State.IN_PROGRESS) {
                throw new IdempotencyConflictException();
            }
            return replay(existing);
        }
        throw new IdempotencyConflictException();
    }

    private Object processNew(ProceedingJoinPoint joinPoint, String redisKey,
                              String requestHash) throws Throwable {
        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable throwable) {
            release(redisKey);
            throw throwable;
        }

        if (!(result instanceof ResponseEntity<?> response)) {
            release(redisKey);
            throw new IllegalStateException("Endpoint idempotente deve retornar ResponseEntity");
        }

        String responseBody = serialize(response.getBody());
        String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
        IdempotencyRecord completed = IdempotencyRecord.done(requestHash,
                response.getStatusCode().value(), responseBody, location);
        storeCompleted(redisKey, completed);
        return result;
    }

    private ResponseEntity<?> replay(IdempotencyRecord existing) {
        HttpHeaders headers = new HttpHeaders();
        if (existing.location() != null) {
            headers.setLocation(URI.create(existing.location()));
        }
        return ResponseEntity.status(HttpStatus.OK)
                .headers(headers)
                .contentType(MediaType.APPLICATION_JSON)
                .body(existing.responseBody());
    }

    private boolean claim(String redisKey, String requestHash) {
        try {
            Boolean created = redis.opsForValue().setIfAbsent(redisKey,
                    serialize(IdempotencyRecord.inProgress(requestHash)),
                    properties.processingTtl());
            if (created == null) {
                throw unavailable(null);
            }
            return created;
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    private IdempotencyRecord read(String redisKey) {
        try {
            String value = redis.opsForValue().get(redisKey);
            return value == null ? null : deserializeRecord(value);
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    private void storeCompleted(String redisKey, IdempotencyRecord completed) {
        try {
            redis.opsForValue().set(redisKey, serialize(completed), properties.retentionTtl());
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    private void release(String redisKey) {
        try {
            redis.delete(redisKey);
        } catch (DataAccessException exception) {
            log.atWarn().setCause(exception)
                    .addKeyValue("idempotencyRedisKeyHash", sha256(redisKey))
                    .log("Falha ao liberar chave de idempotência");
        }
    }

    private Object payload(Object[] arguments, int index) {
        if (index < 0 || index >= arguments.length) {
            throw new IllegalStateException("Índice de payload idempotente inválido");
        }
        return arguments[index];
    }

    private String validateKey(String value) {
        if (value == null || value.isBlank()) {
            throw new IdempotencyKeyRequiredException();
        }
        String normalized = value.strip();
        if (!VALID_KEY.matcher(normalized).matches()) {
            throw new IdempotencyKeyRequiredException(
                    "Idempotency-Key deve ter até 128 caracteres seguros");
        }
        return normalized;
    }

    private String currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new IdempotencyUnavailableException(
                    "Não foi possível identificar o usuário da requisição");
        }
        return authentication.getName();
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        throw new IllegalStateException("Contexto HTTP não disponível para idempotência");
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Falha ao serializar estado de idempotência", exception);
        }
    }

    private IdempotencyRecord deserializeRecord(String value) {
        try {
            return objectMapper.readValue(value, IdempotencyRecord.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Estado de idempotência inválido no Redis", exception);
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 não está disponível", exception);
        }
    }

    private IdempotencyUnavailableException unavailable(Throwable cause) {
        return new IdempotencyUnavailableException(
                "A garantia de idempotência está temporariamente indisponível", cause);
    }
}
