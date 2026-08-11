package com.start.overflow.shared.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

public final class ResilientCacheErrorHandler implements CacheErrorHandler {
    private static final Logger log = LoggerFactory.getLogger(ResilientCacheErrorHandler.class);

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        logFailure("get", exception, cache, key);
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        logFailure("put", exception, cache, key);
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        logFailure("evict", exception, cache, key);
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        logFailure("clear", exception, cache, null);
    }

    private void logFailure(String operation, RuntimeException exception, Cache cache, Object key) {
        log.atWarn()
                .setCause(exception)
                .addKeyValue("cache", cache.getName())
                .addKeyValue("operation", operation)
                .addKeyValue("key", key)
                .log("Falha de cache ignorada; operação seguirá sem cache");
    }
}
