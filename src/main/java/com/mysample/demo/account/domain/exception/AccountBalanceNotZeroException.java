package com.mysample.demo.account.domain.exception;

import com.mysample.demo.shared.error.ErrorCode;
import com.mysample.demo.shared.error.exception.BusinessRuleException;
import java.io.Serial;
import java.util.UUID;

/** 422: an account can only be closed when its balance is zero. */
public final class AccountBalanceNotZeroException extends BusinessRuleException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AccountBalanceNotZeroException(UUID accountId) {
        super(ErrorCode.ACCOUNT_BALANCE_NOT_ZERO,
                "Account " + accountId + " cannot be closed: balance is not zero",
                accountId);
    }
}
