package com.mysample.demo.shared.observability;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * One request/response pair, already masked, and its two log formats. Optional parts are
 * {@code null} and left out. Plain Java so the format can be unit-tested without Spring.
 *
 * @param url  full URL with masked query, shown in the pretty format
 * @param path path with masked query, shown in the compact format (the host is the same on every line)
 */
record HttpExchange(String method, String url, String path, int status, String reason, long millis,
        String failure, Map<String, String> requestHeaders, String requestBody,
        Map<String, String> responseHeaders, String responseBody) {

    private static final String NL = System.lineSeparator();
    private static final String RULE = "─".repeat(90);
    private static final String INDENT = "│     ";

    /** {@code POST /api/v1/customers -> 201 Created (35 ms) request.body={...} response.body={...}} */
    String compact() {
        StringBuilder line = new StringBuilder()
                .append(method).append(' ').append(path)
                .append(" -> ").append(status).append(' ').append(reason)
                .append(" (").append(millis).append(" ms)");
        append(line, "unhandled", failure);
        append(line, "request.headers", inline(requestHeaders));
        append(line, "request.body", requestBody);
        append(line, "response.headers", inline(responseHeaders));
        append(line, "response.body", responseBody);
        return line.toString();
    }

    /**
     * <pre>
     * ┌──────────────────────────────────────────
     * │ REQUEST   POST http://localhost:8080/api/v1/customers
     * │ ── headers
     * │     Authorization : Bearer ****
     * │     Content-Type  : application/json
     * │ ── body
     * │     {
     * │       "fullName": "Dara Sok",
     * │       "email": "d***@example.com"
     * │     }
     * ├──────────────────────────────────────────
     * │ RESPONSE  201 Created  (35 ms)
     * │ ── headers
     * │     Location      : http://localhost:8080/api/v1/customers/...
     * │ ── body
     * │     { ... }
     * └──────────────────────────────────────────
     * </pre>
     */
    String pretty() {
        StringBuilder block = new StringBuilder(NL);
        block.append('┌').append(RULE).append(NL);
        block.append("│ REQUEST   ").append(method).append(' ').append(url).append(NL);
        headers(block, requestHeaders);
        body(block, requestBody);
        block.append('├').append(RULE).append(NL);
        block.append("│ RESPONSE  ").append(status).append(' ').append(reason)
                .append("  (").append(millis).append(" ms)");
        if (failure != null) {
            block.append("  unhandled ").append(failure);
        }
        block.append(NL);
        headers(block, responseHeaders);
        body(block, responseBody);
        return block.append('└').append(RULE).toString();
    }

    private static void headers(StringBuilder block, Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return;
        }
        int width = headers.keySet().stream().mapToInt(String::length).max().orElse(0);
        block.append("│ ── headers").append(NL);
        headers.forEach((name, value) -> block.append(INDENT)
                .append(name).append(" ".repeat(width - name.length()))
                .append(" : ").append(value).append(NL));
    }

    private static void body(StringBuilder block, String body) {
        if (body == null) {
            return;
        }
        block.append("│ ── body").append(NL);
        String text = JsonPrettyPrinter.looksLikeJson(body) ? JsonPrettyPrinter.print(body, "  ") : body;
        for (String line : text.split("\n", -1)) {
            block.append(INDENT).append(line).append(NL);
        }
    }

    private static String inline(Map<String, String> headers) {
        if (headers == null) {
            return null;
        }
        return headers.entrySet().stream()
                .map(header -> header.getKey() + "=" + header.getValue())
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private static void append(StringBuilder line, String name, String value) {
        if (value != null) {
            line.append(' ').append(name).append('=').append(value);
        }
    }
}
