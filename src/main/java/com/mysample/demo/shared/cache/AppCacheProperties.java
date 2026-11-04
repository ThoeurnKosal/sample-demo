package com.mysample.demo.shared.cache;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * {@code app.cache.*}. Whether caching is on at all is Spring's {@code spring.cache.type}
 * ({@code redis}, or {@code none} as a kill switch).
 *
 * @param keyPrefix  prefix of every Redis key: {@code <keyPrefix><cacheName>::<key>}. Bump the
 *                   version part ({@code v1}) when a cached class changes shape.
 * @param defaultTtl TTL of a cache without its own entry in {@link #ttl()}
 * @param ttl        TTL per cache name, e.g. {@code directory-users: 30m}
 */
@Validated
@ConfigurationProperties(prefix = "app.cache")
public record AppCacheProperties(
        @DefaultValue("demo:v1:") @NotBlank String keyPrefix,
        @DefaultValue("10m") @NotNull Duration defaultTtl,
        Map<String, @NotNull Duration> ttl) {

    public AppCacheProperties {
        ttl = ttl == null ? Map.of() : Map.copyOf(ttl);
    }
}
