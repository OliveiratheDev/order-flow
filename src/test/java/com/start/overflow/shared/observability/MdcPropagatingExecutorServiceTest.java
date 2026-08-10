package com.start.overflow.shared.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MdcPropagatingExecutorServiceTest {
    private final ExecutorService executor = new MdcPropagatingExecutorService(
            Executors.newSingleThreadExecutor());

    @AfterEach
    void shutdown() throws InterruptedException {
        MDC.clear();
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void propagatesCallerContextAndCleansWorkerBetweenTasks() throws Exception {
        MDC.put(CorrelationIdContext.MDC_KEY, "correlation-123");

        assertThat(executor.submit(() -> MDC.get(CorrelationIdContext.MDC_KEY)).get())
                .isEqualTo("correlation-123");

        MDC.clear();

        assertThat(executor.submit(() -> MDC.get(CorrelationIdContext.MDC_KEY)).get())
                .isNull();
    }
}
