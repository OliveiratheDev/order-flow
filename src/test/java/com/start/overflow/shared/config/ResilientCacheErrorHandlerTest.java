package com.start.overflow.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResilientCacheErrorHandlerTest {
    @Test
    void deveIgnorarFalhas_quandoRedisEstiverIndisponivel() {
        ResilientCacheErrorHandler handler = new ResilientCacheErrorHandler();
        Cache cache = mock(Cache.class);
        RuntimeException failure = new RuntimeException("indisponível");
        when(cache.getName()).thenReturn("products");

        assertThatCode(() -> {
            handler.handleCacheGetError(failure, cache, 1L);
            handler.handleCachePutError(failure, cache, 1L, "value");
            handler.handleCacheEvictError(failure, cache, 1L);
            handler.handleCacheClearError(failure, cache);
        }).doesNotThrowAnyException();
    }
}
