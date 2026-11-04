package com.mysample.demo.shared.handler;

import com.mysample.demo.shared.error.ErrorCode;
import java.net.URI;
import java.time.Clock;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * The only place that builds error bodies, so every error has the same RFC 9457 shape:
 * <pre>
 * { "type", "title", "status", "detail", "instance", "errorCode", "traceId", "timestamp", ...extra }
 * </pre>
 * {@code detail} is localized from {@code messages*.properties} using the request's
 * {@code Accept-Language} (e.g. {@code km} for Khmer), falling back to the English default.
 */
@Component
@RequiredArgsConstructor
public class ProblemDetailFactory {

    static final String TYPE_BASE_URI = "https://problems.sample-demo.example/";

    private final MessageSource messageSource;
    private final Clock clock;

    public ProblemDetail build(HttpStatusCode status, ErrorCode code, String requestPath,
                               Object[] messageArguments, Map<String, Object> extraProperties) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, localizedMessage(code, messageArguments));
        problem.setType(URI.create(TYPE_BASE_URI + code.getSlug()));
        HttpStatus knownStatus = HttpStatus.resolve(status.value());
        problem.setTitle(knownStatus != null ? knownStatus.getReasonPhrase() : "Error");
        if (requestPath != null) {
            try {
                problem.setInstance(URI.create(requestPath));
            } catch (IllegalArgumentException ignored) {
                // A malformed request path must never turn an error response into a 500.
            }
        }
        problem.setProperty("errorCode", code.name());
        String traceId = MDC.get("traceId");
        if (traceId != null) {
            problem.setProperty("traceId", traceId);
        }
        problem.setProperty("timestamp", clock.instant().toString());
        extraProperties.forEach(problem::setProperty);
        return problem;
    }

    public ProblemDetail build(HttpStatusCode status, ErrorCode code, String requestPath) {
        return build(status, code, requestPath, new Object[0], Map.of());
    }

    private String localizedMessage(ErrorCode code, Object[] arguments) {
        return messageSource.getMessage(code.getMessageKey(), arguments, code.getDefaultMessage(),
                LocaleContextHolder.getLocale());
    }
}

