package com.mysample.demo.shared.cache;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

/**
 * Makes the cache optional: a Redis failure (down, timeout, NOPERM, unreadable entry) never
 * fails the request. A failed read is treated as a miss, so the annotated method runs and
 * reads from the real source; a failed write is skipped.
 * <p>
 * Every failure increments {@code cache.errors{cache,operation}}: alert on its rate, a log
 * line alone is only forensics. The key is not logged (it can be a customer identifier).
 */
final class MeteredCacheErrorHandler implements CacheErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(MeteredCacheErrorHandler.class);

    private final ObjectProvider<MeterRegistry> meterRegistry;

    MeteredCacheErrorHandler(ObjectProvider<MeterRegistry> meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        record("get", cache, exception);
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        record("put", cache, exception);
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        record("evict", cache, exception);
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        record("clear", cache, exception);
    }

    private void record(String operation, Cache cache, RuntimeException exception) {
        log.warn("Cache {} on '{}' failed, using the source instead: {}",
                operation, cache.getName(), exception.toString());
        meterRegistry.ifAvailable(registry -> registry
                .counter("cache.errors", "cache", cache.getName(), "operation", operation)
                .increment());
    }
}
