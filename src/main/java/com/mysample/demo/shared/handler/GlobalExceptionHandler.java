package com.mysample.demo.shared.handler;

import com.mysample.demo.shared.error.ErrorCode;
import com.mysample.demo.shared.error.exception.ApplicationException;
import com.mysample.demo.shared.error.exception.BusinessRuleException;
import com.mysample.demo.shared.error.exception.ConflictException;
import com.mysample.demo.shared.error.exception.DownstreamRejectedException;
import com.mysample.demo.shared.error.exception.DownstreamUnavailableException;
import com.mysample.demo.shared.error.exception.NotFoundException;
import com.mysample.demo.shared.model.FieldErrorDetail;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every exception into an RFC 9457 {@code application/problem+json} response.
 * <ul>
 *   <li>Our exceptions: the HTTP status comes from the exception <b>category</b>
 *       (sealed {@link ApplicationException} hierarchy), never from individual codes.</li>
 *   <li>Spring MVC's own exceptions (bad JSON, 404 route, 405, 415...) are handled by the parent
 *       class; {@link #handleExceptionInternal} gives them the same body shape.</li>
 *   <li>Security filter errors (401/403) are routed here by the handlers in {@code shared.error.security}.</li>
 *   <li>Every error increments {@code api.errors{errorCode,status}}: alert on the rate, not on log lines.</li>
 *   <li>Logging: 500 at ERROR with stack trace; other 5xx at WARN; 4xx at DEBUG.
 *       Clients never see stack traces, SQL or downstream bodies.</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    static final String RETRY_AFTER_SECONDS = "30";

    /** 422. Looked up by code: the constant's name differs between Spring versions (ENTITY vs CONTENT). */
    private static final HttpStatus UNPROCESSABLE = HttpStatus.valueOf(422);

    private final ProblemDetailFactory problems;
    private final ObjectProvider<MeterRegistry> meterRegistry;

    // ---- Our exceptions: status by category ------------------------------------------------

    @ExceptionHandler(ApplicationException.class)
    ResponseEntity<ProblemDetail> handleApplicationException(ApplicationException ex, HttpServletRequest request) {
        HttpStatus status = statusOf(ex);
        ProblemDetail body = problems.build(status, ex.getErrorCode(), request.getRequestURI(),
                ex.getMessageArguments(), ex.getProperties());
        return respond(ex, status, ex.getErrorCode(), body);
    }

    private static HttpStatus statusOf(ApplicationException ex) {
        return switch (ex) {
            case NotFoundException ignored -> HttpStatus.NOT_FOUND;
            case ConflictException ignored -> HttpStatus.CONFLICT;
            case BusinessRuleException ignored -> UNPROCESSABLE;
            case DownstreamRejectedException ignored -> HttpStatus.BAD_GATEWAY;
            case DownstreamUnavailableException ignored -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }

    // ---- Third-party exceptions with a precise meaning --------------------------------------

    /** Circuit breaker is open: we did not even try the downstream call. */
    @ExceptionHandler(CallNotPermittedException.class)
    ResponseEntity<ProblemDetail> handleCircuitOpen(CallNotPermittedException ex, HttpServletRequest request) {
        return simple(ex, HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.DOWNSTREAM_UNAVAILABLE, request);
    }

    /** Optimistic locking ({@code @Version}): someone else changed the row since we read it. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest request) {
        return simple(ex, HttpStatus.CONFLICT, ErrorCode.CONCURRENT_MODIFICATION, request);
    }

    /** Missing/invalid token. Reaches us from {@code ProblemDetailAuthenticationEntryPoint}. */
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return simple(ex, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, request);
    }

    /** Missing scope: from {@code @PreAuthorize}, or from the filter chain via {@code ProblemDetailAccessDeniedHandler}. */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return simple(ex, HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED, request);
    }

    /** Anything else is a bug: 500, full stack trace in the log, nothing internal in the response. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        return simple(ex, HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, request);
    }

    // ---- Spring MVC exceptions (handled by ResponseEntityExceptionHandler) --------------------

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return validationFailed(ex, ValidationErrorExtractor.from(ex), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return validationFailed(ex, ValidationErrorExtractor.from(ex), headers, status, request);
    }

    /**
     * Every Spring MVC exception ends here (bad JSON, unknown route, 405, 415...). Replaces the
     * default body with ours, keeps the headers Spring set (e.g. {@code Allow} on 405).
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ErrorCode code;
        Object responseBody;
        if (body instanceof ProblemDetail problem && hasErrorCode(problem)) {
            code = ErrorCode.valueOf(String.valueOf(problem.getProperties().get("errorCode")));
            responseBody = problem;
        } else {
            code = codeForStatus(statusCode);
            responseBody = problems.build(statusCode, code, pathOf(request));
        }
        record(ex, statusCode, code);
        return super.handleExceptionInternal(ex, responseBody, headers, statusCode, request);
    }

    private ResponseEntity<Object> validationFailed(Exception ex, List<FieldErrorDetail> errors,
                                                    HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = problems.build(status, ErrorCode.VALIDATION_FAILED, pathOf(request),
                new Object[0], Map.of("errors", errors));
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    private static ErrorCode codeForStatus(HttpStatusCode status) {
        return switch (status.value()) {
            case 404 -> ErrorCode.ROUTE_NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 406 -> ErrorCode.NOT_ACCEPTABLE;
            case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            default -> status.is4xxClientError() ? ErrorCode.MALFORMED_REQUEST : ErrorCode.INTERNAL_ERROR;
        };
    }

    // ---- helpers -------------------------------------------------------------------------------

    private ResponseEntity<ProblemDetail> simple(Exception ex, HttpStatus status, ErrorCode code,
                                                 HttpServletRequest request) {
        return respond(ex, status, code, problems.build(status, code, request.getRequestURI()));
    }

    private ResponseEntity<ProblemDetail> respond(Exception ex, HttpStatus status, ErrorCode code, ProblemDetail body) {
        record(ex, status, code);
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status);
        if (status == HttpStatus.SERVICE_UNAVAILABLE) {
            response.header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS);
        }
        return response.body(body);
    }

    /** One log line and one counter per error, whatever path it came through. */
    private void record(Exception ex, HttpStatusCode status, ErrorCode code) {
        if (status.value() == 500) {
            log.error("Unhandled error [{}]", code, ex);
        } else if (status.is5xxServerError()) {
            log.warn("Request failed [{}]: {}", code, ex.getMessage());
        } else {
            log.debug("Request rejected [{}]: {}", code, ex.getMessage());
        }
        meterRegistry.ifAvailable(registry -> registry
                .counter("api.errors", "errorCode", code.name(), "status", String.valueOf(status.value()))
                .increment());
    }

    private static boolean hasErrorCode(ProblemDetail problem) {
        return problem.getProperties() != null && problem.getProperties().containsKey("errorCode");
    }

    private static String pathOf(WebRequest request) {
        return request instanceof ServletWebRequest servlet ? servlet.getRequest().getRequestURI() : null;
    }
}
