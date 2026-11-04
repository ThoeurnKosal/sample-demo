package com.mysample.demo.customer.domain;

import com.mysample.demo.customer.domain.exception.CustomerRuleViolationException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The descriptive data of a customer, whether typed in by a user or imported from the
 * external directory. Invariants and normalization live here, so every path that creates
 * a customer (API, import, MapStruct mappers, batch...) gets the same rules.
 * Violations raise {@link CustomerRuleViolationException} (HTTP 422).
 */
public record CustomerDetails(String fullName, String email, String phone, String city) {

    public static final int MAX_NAME = 100;
    public static final int MAX_EMAIL = 254;
    public static final int MAX_PHONE = 40;
    public static final int MAX_CITY = 100;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public CustomerDetails {
        fullName = blankToNull(fullName);
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT); // one customer per mailbox
        phone = blankToNull(phone);
        city = blankToNull(city);

        require(fullName != null, CustomerRuleViolationException.Rule.FULL_NAME_REQUIRED);
        require(fullName.length() <= MAX_NAME, CustomerRuleViolationException.Rule.FULL_NAME_TOO_LONG);
        require(email != null && email.length() <= MAX_EMAIL && EMAIL_PATTERN.matcher(email).matches(), CustomerRuleViolationException.Rule.EMAIL_INVALID);
        require(phone == null || phone.length() <= MAX_PHONE, CustomerRuleViolationException.Rule.PHONE_TOO_LONG);
        require(city == null || city.length() <= MAX_CITY, CustomerRuleViolationException.Rule.CITY_TOO_LONG);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static void require(boolean condition, CustomerRuleViolationException.Rule rule) {
        if (!condition) {
            throw new CustomerRuleViolationException(rule);
        }
    }
}
