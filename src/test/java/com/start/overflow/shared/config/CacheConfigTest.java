package com.start.overflow.shared.config;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CacheConfigTest {
    @Test
    void deveConfigurarTtl_quandoInicializarCaches() {
        CacheConfig config = new CacheConfig(mock(RedisConnectionFactory.class),
                JsonMapper.builder().findAndAddModules().build());

        RedisCacheManager manager = (RedisCacheManager) config.cacheManager();
        manager.initializeCaches();
        RedisCacheConfiguration categories = configuration(manager, "categories");
        RedisCacheConfiguration products = configuration(manager, "products");

        assertThat(categories.getTtlFunction().getTimeToLive(null, null))
                .isEqualTo(Duration.ofMinutes(10));
        assertThat(products.getTtlFunction().getTimeToLive(null, null))
                .isEqualTo(Duration.ofMinutes(5));
        assertThat(Map.of("categories", categories, "products", products).values()).allSatisfy(configuration -> {
            assertThat(configuration.getAllowCacheNullValues()).isFalse();
            String json = StandardCharsets.UTF_8.decode(configuration.getValueSerializationPair()
                    .write(Map.of("name", "OrderFlow"))).toString();
            assertThat(json).startsWith("{").contains("OrderFlow");
        });
    }

    private RedisCacheConfiguration configuration(RedisCacheManager manager, String name) {
        TransactionAwareCacheDecorator decorator = (TransactionAwareCacheDecorator) manager.getCache(name);
        RedisCache cache = (RedisCache) decorator.getTargetCache();
        return cache.getCacheConfiguration();
    }
}
