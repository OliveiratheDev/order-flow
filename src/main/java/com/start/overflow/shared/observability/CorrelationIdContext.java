package com.start.overflow.shared.observability;

import org.slf4j.MDC;

import java.util.UUID;

public final class CorrelationIdContext {
    public static final String MDC_KEY = "correlationId";

    private CorrelationIdContext() {
    }

    public static String currentOrCreate() {
        String current = MDC.get(MDC_KEY);
        return current == null || current.isBlank() ? UUID.randomUUID().toString() : current;
    }
}
