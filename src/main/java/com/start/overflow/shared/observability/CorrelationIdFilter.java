package com.start.overflow.shared.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Correlation-Id";
    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);
    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        long startedAt = System.nanoTime();
        String correlationId = resolve(request.getHeader(HEADER));
        MDC.put(CorrelationIdContext.MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);

        log.atInfo()
                .addKeyValue("httpMethod", request.getMethod())
                .addKeyValue("httpPath", request.getRequestURI())
                .log("Requisição HTTP iniciada");
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            log.atInfo()
                    .addKeyValue("httpMethod", request.getMethod())
                    .addKeyValue("httpPath", request.getRequestURI())
                    .addKeyValue("httpStatus", response.getStatus())
                    .addKeyValue("durationMs", durationMs)
                    .log("Requisição HTTP finalizada");
            MDC.clear();
        }
    }

    private String resolve(String candidate) {
        if (candidate != null) {
            String normalized = candidate.strip();
            if (VALID_ID.matcher(normalized).matches()) {
                return normalized;
            }
        }
        return UUID.randomUUID().toString();
    }
}
