package com.mysample.demo.shared.observability;

import static java.util.stream.Collectors.toUnmodifiableSet;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Logs every inbound request together with its response as ONE log event, written when the
 * response is complete (no interleaving between concurrent requests). Runs around Spring
 * Security, so rejected calls (401/403) are logged too.
 * <ul>
 *   <li>Credentials and personal data are masked by {@link SensitiveDataMasker}.</li>
 *   <li>Bodies are logged only for JSON, form and text content, and only when smaller than
 *       {@code max-body-length}: a body is never cut, so a secret can never be half-logged.</li>
 *   <li>The traceId is already in the MDC (Boot's tracing filter runs earlier), so the log
 *       pattern prints it on this line like on every other line of the request.</li>
 *   <li>Logging can never break a request: a failure here is reported and swallowed.</li>
 * </ul>
 * Logger: {@code com.mysample.demo.shared.observability.HttpLoggingFilter}, level INFO
 * (WARN for 5xx). Set it to WARN to keep only server errors.
 */
public class HttpLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpLoggingFilter.class);

    private final HttpLoggingProperties properties;
    private final SensitiveDataMasker masker;
    private final Set<String> hiddenHeaders;

    public HttpLoggingFilter(HttpLoggingProperties properties, SensitiveDataMasker masker) {
        this.properties = properties;
        this.masker = masker;
        this.hiddenHeaders = properties.hiddenHeaders().stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(toUnmodifiableSet());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.enabled() || !log.isInfoEnabled();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // +1 byte: lets us tell "exactly max" from "more than max" (the latter is not logged)
        ContentCachingRequestWrapper cachedRequest =
                new ContentCachingRequestWrapper(request, properties.maxBodyLength() + 1);
        ContentCachingResponseWrapper cachedResponse = new ContentCachingResponseWrapper(response);
        long start = System.nanoTime();
        Exception failure = null;
        try {
            chain.doFilter(cachedRequest, cachedResponse);
        } catch (IOException | ServletException | RuntimeException ex) {
            failure = ex;
            throw ex;
        } finally {
            long millis = (System.nanoTime() - start) / 1_000_000;
            try {
                write(cachedRequest, cachedResponse, millis, failure);
            } catch (RuntimeException loggingFailure) {
                log.warn("HTTP traffic logging failed: {}", loggingFailure.toString());
            }
            cachedResponse.copyBodyToResponse(); // the client must always get its body
        }
    }

    private void write(ContentCachingRequestWrapper request, ContentCachingResponseWrapper response,
                       long millis, Exception failure) {
        // An exception that escaped every handler becomes a 500 in the container
        int status = failure == null ? response.getStatus() : HttpStatus.INTERNAL_SERVER_ERROR.value();
        String query = query(request);
        HttpExchange exchange = new HttpExchange(
                request.getMethod(),
                SensitiveDataMasker.oneLine(request.getRequestURL().toString()) + query,
                SensitiveDataMasker.oneLine(request.getRequestURI()) + query,
                status,
                reason(status),
                millis,
                failure == null ? null : failure.getClass().getSimpleName(),
                properties.includeHeaders() ? requestHeaders(request) : null,
                properties.includeBody() ? body(request.getContentAsByteArray(), request.getContentType()) : null,
                properties.includeHeaders() ? responseHeaders(response) : null,
                properties.includeBody() ? body(response.getContentAsByteArray(), response.getContentType()) : null);

        String message = properties.pretty() ? exchange.pretty() : exchange.compact();
        // Key-value pairs become separate JSON fields in structured logs (ECS field names);
        // the plain console pattern does not print them, so local output is unchanged.
        (status >= 500 ? log.atWarn() : log.atInfo())
                .addKeyValue("http.request.method", request.getMethod())
                .addKeyValue("url.path", SensitiveDataMasker.oneLine(request.getRequestURI()))
                .addKeyValue("http.response.status_code", status)
                .addKeyValue("event.duration", millis * 1_000_000)   // ECS: nanoseconds
                .log(message);
    }

    private String query(HttpServletRequest request) {
        String query = request.getQueryString();
        return query == null ? "" : "?" + masker.maskForm(query);
    }

    /** Sorted by name (case-insensitive), noise headers left out, sensitive values masked. */
    private Map<String, String> requestHeaders(HttpServletRequest request) {
        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String name : Collections.list(request.getHeaderNames())) {
            if (!isHidden(name)) {
                headers.put(name, joined(name, Collections.list(request.getHeaders(name))));
            }
        }
        return headers;
    }

    private Map<String, String> responseHeaders(HttpServletResponse response) {
        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String name : response.getHeaderNames()) {
            if (!isHidden(name)) {
                headers.put(name, joined(name, response.getHeaders(name)));
            }
        }
        return headers;
    }

    private boolean isHidden(String headerName) {
        return hiddenHeaders.contains(headerName.toLowerCase(Locale.ROOT));
    }

    private String joined(String name, Collection<String> values) {
        return values.stream().map(value -> masker.maskHeader(name, value)).collect(Collectors.joining("; "));
    }

    /** {@code null} means "nothing to show" (no body). */
    private String body(byte[] content, String contentType) {
        if (content.length == 0) {
            return null;
        }
        if (content.length > properties.maxBodyLength()) {
            return "[body larger than " + properties.maxBodyLength() + " bytes, not logged]";
        }
        MediaType type = parse(contentType);
        if (type == null || !isLoggable(type)) {
            return "[" + content.length + " bytes of " + (contentType == null ? "unknown content type" : contentType)
                    + ", not logged]";
        }
        Charset charset = type.getCharset() != null ? type.getCharset() : StandardCharsets.UTF_8;
        String text = new String(content, charset);
        return MediaType.APPLICATION_FORM_URLENCODED.includes(type) ? masker.maskForm(text) : masker.maskJson(text);
    }

    private static boolean isLoggable(MediaType type) {
        String subtype = type.getSubtype();
        return "json".equals(subtype) || subtype.endsWith("+json")
                || MediaType.APPLICATION_FORM_URLENCODED.includes(type)
                || "text".equals(type.getType());
    }

    private static MediaType parse(String contentType) {
        if (contentType == null) {
            return null;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException invalid) {
            return null;
        }
    }

    private static String reason(int status) {
        HttpStatus known = HttpStatus.resolve(status);
        return known == null ? "" : known.getReasonPhrase();
    }
}
