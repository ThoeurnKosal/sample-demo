package com.mysample.demo.shared.observability;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * {@code app.http-logging.*}: inbound HTTP traffic log. Defaults are production-safe:
 * one compact line per request, no headers, no bodies. Turn on details per environment.
 *
 * @param pretty                multi-line, boxed format for humans (local); compact one line otherwise
 * @param includeHeaders        log request and response headers (sensitive ones masked)
 * @param includeBody           log JSON / form / text bodies (sensitive fields masked)
 * @param maxBodyLength         larger bodies are not logged at all (never cut in the middle of a secret)
 * @param urlPatterns           servlet URL patterns the filter applies to
 * @param maskedHeaders         header names whose value is replaced (case-insensitive)
 * @param hiddenHeaders         header names left out of the log entirely: noise that never
 *                              helps reading a request (Postman token, default security headers)
 * @param maskedFields          JSON / form field names whose value is replaced; names containing
 *                              "password", "secret" or "token" are always masked
 * @param partiallyMaskedFields fields shown partially: e-mail as {@code d***@example.com},
 *                              phone numbers with only the last 3 digits
 */
@Validated
@ConfigurationProperties(prefix = "app.http-logging")
public record HttpLoggingProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("false") boolean pretty,
        @DefaultValue("false") boolean includeHeaders,
        @DefaultValue("false") boolean includeBody,
        @DefaultValue("4096") @Min(256) @Max(1_048_576) int maxBodyLength,
        @DefaultValue("/api/*") @NotEmpty List<String> urlPatterns,
        @DefaultValue({"authorization", "proxy-authorization", "cookie", "set-cookie",
                "x-api-key", "x-auth-token", "x-vault-token"}) Set<String> maskedHeaders,
        @DefaultValue({"postman-token", "connection", "keep-alive", "accept-encoding",
                "x-content-type-options", "x-xss-protection", "x-frame-options", "pragma", "expires"})
        Set<String> hiddenHeaders,
        @DefaultValue({"password", "secret", "token", "authorization", "apiKey", "secretId", "roleId",
                "pin", "otp", "cvv", "cardNumber"}) Set<String> maskedFields,
        @DefaultValue({"email", "phone"}) Set<String> partiallyMaskedFields) {
}
