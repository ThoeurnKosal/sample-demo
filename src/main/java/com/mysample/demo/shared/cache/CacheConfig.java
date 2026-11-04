package com.mysample.demo.shared.cache;

import io.lettuce.core.ClientOptions;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

/**
 * Spring Cache backed by Redis. Spring Boot creates the {@code RedisCacheManager} when
 * {@code spring.cache.type=redis}; this class only adds our rules to it.
 * <ul>
 *   <li>Order {@link Ordered#HIGHEST_PRECEDENCE}: the cache runs outside retry and circuit
 *       breaker, so cached data is still served while a downstream circuit is open.</li>
 *   <li>JSON values with type information limited to our own and {@code java.util} classes.</li>
 *   <li>Keys {@code demo:v1:<cache>::<key>}, a TTL on every cache, null values never stored.</li>
 *   <li>Clearing a cache uses SCAN, never KEYS (KEYS blocks Redis and is denied by our ACL).</li>
 *   <li>Statistics on, so Boot publishes {@code cache.gets{result=hit|miss}} per cache.</li>
 *   <li>Redis is optional: see {@link MeteredCacheErrorHandler}.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching(order = Ordered.HIGHEST_PRECEDENCE)
@EnableConfigurationProperties(AppCacheProperties.class)
public class CacheConfig implements CachingConfigurer {

    /** Only these packages may appear as a type id in a cached value. */
    private static final String[] ALLOWED_TYPE_PREFIXES = {"com.mysample.demo.", "java.util."};

    private static final int SCAN_BATCH_SIZE = 1000;

    private final ObjectProvider<MeterRegistry> meterRegistry;

    CacheConfig(ObjectProvider<MeterRegistry> meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new MeteredCacheErrorHandler(meterRegistry);
    }

    @Bean
    RedisCacheManagerBuilderCustomizer redisCacheRules(AppCacheProperties properties,
                                                       RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .computePrefixWith(cacheName -> properties.keyPrefix() + cacheName + "::")
                .serializeValuesWith(SerializationPair.fromSerializer(cacheValueSerializer()))
                .disableCachingNullValues()
                .entryTtl(properties.defaultTtl());
        return builder -> {
            builder.cacheWriter(RedisCacheWriter.nonLockingRedisCacheWriter(
                            connectionFactory, BatchStrategies.scan(SCAN_BATCH_SIZE)))
                    .cacheDefaults(defaults)
                    .enableStatistics();
            properties.ttl().forEach((cacheName, ttl) ->
                    builder.withCacheConfiguration(cacheName, defaults.entryTtl(ttl)));
        };
    }

    /**
     * While the connection is down, reject commands at once instead of queueing them until the
     * command timeout. A Redis outage then costs no latency: the error handler turns the failure
     * into a miss and the request goes to the source. Lettuce keeps reconnecting in the background.
     */
    @Bean
    LettuceClientOptionsBuilderCustomizer failFastWhileRedisIsDisconnected() {
        return options -> options.disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS);
    }

    /** Visible for tests: the exact serializer the cache uses. */
    static GenericJacksonJsonRedisSerializer cacheValueSerializer() {
        BasicPolymorphicTypeValidator.Builder validator = BasicPolymorphicTypeValidator.builder();
        for (String prefix : ALLOWED_TYPE_PREFIXES) {
            validator.allowIfSubType(prefix);
        }
        PolymorphicTypeValidator onlyKnownTypes = validator.build();
        return GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(onlyKnownTypes)
                .typePropertyName("@class")
                .build();
    }
}
