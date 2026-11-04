package com.mysample.demo.shared.error.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes problem+json from the security filter chain, where @RestControllerAdvice cannot help
 * (the request never reached a controller). Same shape as every other error of the API.
 */
@Component
@RequiredArgsConstructor
class SecurityProblemWriter {

    private static final String TYPE_BASE = "https://problems.sample-demo.example/";

    private final JsonMapper jsonMapper;

    void write(HttpServletRequest request, HttpServletResponse response,
               HttpStatus status, String errorCode, String detail) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", TYPE_BASE + errorCode.toLowerCase(Locale.ROOT).replace('_', '-'));
        body.put("title", status.getReasonPhrase());
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("instance", request.getRequestURI());
        body.put("errorCode", errorCode);
        body.put("traceId", MDC.get("traceId"));
        body.put("timestamp", Instant.now().toString());

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getOutputStream(), body);
    }
}
