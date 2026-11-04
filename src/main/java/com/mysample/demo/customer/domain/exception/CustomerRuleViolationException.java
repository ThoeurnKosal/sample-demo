package com.mysample.demo.customer.domain.exception;

import com.mysample.demo.shared.error.ErrorCode;
import com.mysample.demo.shared.error.exception.BusinessRuleException;
import java.io.Serial;
import java.util.Map;
import lombok.Getter;

@Getter
public class CustomerRuleViolationException extends BusinessRuleException {

    @Serial
    private static final long serialVersionUID = 1L;

    public enum Rule {
        FULL_NAME_REQUIRED,
        FULL_NAME_TOO_LONG,
        EMAIL_INVALID,
        PHONE_TOO_LONG,
        CITY_TOO_LONG
    }

    private final Rule rule;

    public CustomerRuleViolationException(Rule rule) {
        super(ErrorCode.CUSTOMER_RULE_VIOLATION, "Customer rule violated: " + rule, rule.name());
        this.rule = rule;
    }

    @Override
    public Map<String, Object> getProperties() {
        return Map.of("rule", rule.name());
    }
}

