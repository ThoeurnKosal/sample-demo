package com.mysample.demo.account.domain.exception;

import com.mysample.demo.shared.error.ErrorCode;
import com.mysample.demo.shared.error.exception.BusinessRuleException;
import java.io.Serial;
import java.util.UUID;

/** 422: a withdrawal larger than the balance. */
public final class InsufficientFundsException extends BusinessRuleException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InsufficientFundsException(UUID accountId) {
        super(ErrorCode.INSUFFICIENT_FUNDS,
                "Insufficient funds on account " + accountId,
                accountId);
    }
}
