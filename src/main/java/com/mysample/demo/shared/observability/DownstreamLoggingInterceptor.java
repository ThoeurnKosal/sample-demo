package com.mysample.demo.shared.observability;

import java.io.IOException;
import java.net.URI;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

@Slf4j
@RequiredArgsConstructor
public class DownstreamLoggingInterceptor implements ClientHttpRequestInterceptor {

    static final String NO_HOST = "<no-host>";

    private final String system;

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        long start = System.nanoTime();
        String target = describe(request.getURI());
        try {
            ClientHttpResponse response = execution.execute(request, body);
            logResponse(request, target, response.getStatusCode().value(), start);
            return response;
        } catch (IOException e) {
            log.warn("downstream={} method={} uri={} error={} message=\"{}\" durationMs={}",
                    system, request.getMethod(), target, e.getClass().getSimpleName(), e.getMessage(),
                    elapsedMs(start));
            throw e;
        }
    }

    private void logResponse(HttpRequest request, String target, int status, long start) {
        if (status >= 500) {
            log.warn("downstream={} method={} uri={} status={} durationMs={}",
                    system, request.getMethod(), target, status, elapsedMs(start));
        } else {
            log.info("downstream={} method={} uri={} status={} durationMs={}",
                    system, request.getMethod(), target, status, elapsedMs(start));
        }
    }

    /**
     * Scheme, host, port and path only. The query string is left out because it can carry
     * personal data or tokens; user info is left out because it can carry credentials.
     * A request without a host (base URL not configured) is shown as "<no-host>/path".
     */
    static String describe(URI uri) {
        String path = Objects.toString(uri.getRawPath(), "");
        if (uri.getHost() == null) {
            return NO_HOST + path;
        }
        String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
        return uri.getScheme() + "://" + uri.getHost() + port + path;
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
