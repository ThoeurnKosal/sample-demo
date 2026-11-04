package com.mysample.demo.shared.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Stable, machine-readable error codes returned to API clients as {@code errorCode}.
 * <ul>
 *   <li>Clients branch on these: never rename or reuse a code once released.</li>
 *   <li>Plain Java on purpose (no {@code HttpStatus}), so the domain can use it without Spring.
 *       The HTTP status is decided by the exception type in {@code GlobalExceptionHandler}.</li>
 *   <li>The client-facing text comes from {@code messages*.properties} under {@link #getMessageKey()};
 *       {@link #getDefaultMessage()} is the fallback when no translation exists.</li>
 * </ul>
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 400
    VALIDATION_FAILED("validation-failed", "Request validation failed."),
    MALFORMED_REQUEST("malformed-request", "The request could not be read. Check the body, parameter types and required parameters."),
    // 401 / 403
    UNAUTHORIZED("unauthorized", "Authentication is required. Send a valid bearer token."),
    ACCESS_DENIED("access-denied", "You are not allowed to perform this operation."),
    // 404
    CUSTOMER_NOT_FOUND("customer-not-found", "Customer {0} was not found."),
    EXTERNAL_USER_NOT_FOUND("external-user-not-found", "User {0} was not found in the customer directory."),
    ACCOUNT_NOT_FOUND("account-not-found", "Account {0} was not found."),
    ROUTE_NOT_FOUND("route-not-found", "No endpoint matches this URL."),
    // 405 / 406 / 415
    METHOD_NOT_ALLOWED("method-not-allowed", "This HTTP method is not supported for this URL."),
    NOT_ACCEPTABLE("not-acceptable", "The requested response format is not supported. Accept application/json."),
    UNSUPPORTED_MEDIA_TYPE("unsupported-media-type", "This content type is not supported. Send application/json."),
    // 409
    DUPLICATE_CUSTOMER("duplicate-customer", "A customer with the same email or directory id already exists."),
    ACCOUNT_ALREADY_EXISTS("account-already-exists", "Customer {0} already has an active {1} account."),
    CONCURRENT_MODIFICATION("concurrent-modification", "The resource was changed by another request. Reload it and try again."),
    // 422
    CUSTOMER_RULE_VIOLATION("customer-rule-violation", "Customer data violates a business rule: {0}."),
    ACCOUNT_NOT_ACTIVE("account-not-active", "Account {0} is closed. No money can be moved."),
    INSUFFICIENT_FUNDS("insufficient-funds", "The balance of account {0} is lower than the requested amount."),
    ACCOUNT_BALANCE_NOT_ZERO("account-balance-not-zero", "Account {0} still has money. Withdraw the balance before closing it."),
    INVALID_AMOUNT("invalid-amount", "Amount {0} {1} must be positive, have at most two decimals and keep the balance within the limit."),
    UNSUPPORTED_CURRENCY("unsupported-currency", "Currency {0} is not supported. Use USD or KHR."),
    // 502 / 503
    DOWNSTREAM_REJECTED("downstream-rejected", "A downstream system rejected the request."),
    DOWNSTREAM_UNAVAILABLE("downstream-unavailable", "A required downstream system is temporarily unavailable. Please retry later."),
    // 500
    INTERNAL_ERROR("internal-error", "An unexpected error occurred.");

    /** Used in the problem {@code type} URI and the message key. */
    private final String slug;

    /** English text with {@link java.text.MessageFormat} placeholders, used when no translation exists. */
    private final String defaultMessage;

    public String getMessageKey() {
        return "error." + slug;
    }
}
