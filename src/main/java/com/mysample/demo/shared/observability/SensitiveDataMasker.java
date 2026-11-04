package com.mysample.demo.shared.observability;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toUnmodifiableSet;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Removes credentials and personal data from text before it is logged. Plain Java, no
 * framework, so it can be reused by the outbound {@code DownstreamLoggingInterceptor} too.
 * <p>
 * Field names are compared normalised (lower case, without {@code _} and {@code -}), so
 * {@code client_secret}, {@code clientSecret} and {@code Client-Secret} are the same field.
 * Masking is fail-safe: when in doubt (a name containing "token"), the value is hidden.
 */
public final class SensitiveDataMasker {

    public static final String MASK = "****";

    /** A key containing one of these is always secret, whatever the configuration says. */
    private static final List<String> SECRET_KEY_PARTS = List.of("password", "secret", "token");

    /** {@code "key" : value} where value is a JSON string, number, boolean or null. */
    private static final Pattern JSON_PAIR = Pattern.compile(
            "\"((?:\\\\.|[^\"\\\\])*)\"(\\s*:\\s*)"
                    + "(\"(?:\\\\.|[^\"\\\\])*\"|-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?|true|false|null)");

    private static final Pattern LINE_BREAKS = Pattern.compile("[\\r\\n\\u2028\\u2029]+\\s*");

    private final Set<String> maskedHeaders;
    private final Set<String> maskedFields;
    private final Set<String> partiallyMaskedFields;

    public SensitiveDataMasker(Collection<String> maskedHeaders,
                               Collection<String> maskedFields,
                               Collection<String> partiallyMaskedFields) {
        this.maskedHeaders = maskedHeaders.stream()
                .map(header -> header.toLowerCase(Locale.ROOT))
                .collect(toUnmodifiableSet());
        this.maskedFields = maskedFields.stream().map(SensitiveDataMasker::normalise).collect(toUnmodifiableSet());
        this.partiallyMaskedFields = partiallyMaskedFields.stream()
                .map(SensitiveDataMasker::normalise)
                .collect(toUnmodifiableSet());
    }

    /** {@code Authorization: Bearer eyJ...} becomes {@code Bearer ****}: the scheme stays visible. */
    public String maskHeader(String name, String value) {
        if (value == null || !maskedHeaders.contains(name.toLowerCase(Locale.ROOT))) {
            return oneLine(value);
        }
        int space = value.indexOf(' ');
        boolean hasScheme = space > 0 && name.toLowerCase(Locale.ROOT).endsWith("authorization");
        return hasScheme ? value.substring(0, space) + " " + MASK : MASK;
    }

    /** Masks values of sensitive keys anywhere in a JSON document (nested objects and arrays too). */
    public String maskJson(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }
        String masked = JSON_PAIR.matcher(json).replaceAll(match -> Matcher.quoteReplacement(maskPair(match)));
        return oneLine(masked);
    }

    /** Masks {@code application/x-www-form-urlencoded} bodies and query strings. */
    public String maskForm(String form) {
        if (form == null || form.isEmpty()) {
            return form;
        }
        return oneLine(Arrays.stream(form.split("&", -1)).map(this::maskFormPair).collect(joining("&")));
    }

    /**
     * Replaces line breaks with a space. Logged values come from the client: a value with a
     * line break could otherwise forge a fake log line (log injection).
     */
    public static String oneLine(String text) {
        return text == null ? null : LINE_BREAKS.matcher(text).replaceAll(" ");
    }

    boolean isSecret(String normalisedKey) {
        return maskedFields.contains(normalisedKey)
                || SECRET_KEY_PARTS.stream().anyMatch(normalisedKey::contains);
    }

    private String maskPair(MatchResult pair) {
        String key = normalise(pair.group(1));
        String value = pair.group(3);
        String prefix = "\"" + pair.group(1) + "\"" + pair.group(2);
        if (isSecret(key)) {
            return prefix + "\"" + MASK + "\"";
        }
        if (partiallyMaskedFields.contains(key) && value.startsWith("\"")) {
            return prefix + "\"" + partial(value.substring(1, value.length() - 1)) + "\"";
        }
        return pair.group();
    }

    private String maskFormPair(String pair) {
        int equals = pair.indexOf('=');
        if (equals < 0) {
            return pair;
        }
        String rawKey = pair.substring(0, equals);
        String key = normalise(decode(rawKey));
        if (isSecret(key)) {
            return rawKey + "=" + MASK;
        }
        if (partiallyMaskedFields.contains(key)) {
            return rawKey + "=" + partial(decode(pair.substring(equals + 1)));
        }
        return pair;
    }

    /**
     * {@code dara@example.com} becomes {@code d***@example.com}. A value with digits (a phone
     * number) keeps its last 3 digits and its formatting: {@code 012 345 670} becomes
     * {@code *** *** 670}. Anything else keeps its last 3 characters.
     */
    static String partial(String value) {
        int at = value.indexOf('@');
        if (at > 0) {
            return value.charAt(0) + "***" + value.substring(at);
        }
        long digits = value.chars().filter(Character::isDigit).count();
        if (digits > 4) {
            StringBuilder masked = new StringBuilder(value.length());
            long seen = 0;
            for (char c : value.toCharArray()) {
                boolean isDigit = Character.isDigit(c);
                seen += isDigit ? 1 : 0;
                masked.append(isDigit && seen <= digits - 3 ? '*' : c);
            }
            return masked.toString();
        }
        if (digits > 0 || value.length() <= 4) {
            return MASK;
        }
        return "*".repeat(value.length() - 3) + value.substring(value.length() - 3);
    }

    static String normalise(String key) {
        return key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    }

    private static String decode(String text) {
        try {
            return URLDecoder.decode(text, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException malformed) {
            return text;
        }
    }
}
