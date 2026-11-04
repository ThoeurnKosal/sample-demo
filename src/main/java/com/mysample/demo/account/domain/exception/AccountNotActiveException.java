package com.mysample.demo.account.domain.exception;

import com.mysample.demo.shared.error.ErrorCode;
import com.mysample.demo.shared.error.exception.BusinessRuleException;
import java.io.Serial;
import java.util.UUID;

/** 422: the account is closed; no money can move. */
public final class AccountNotActiveException extends BusinessRuleException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AccountNotActiveException(UUID accountId) {
        super(ErrorCode.ACCOUNT_NOT_ACTIVE,
                "Account " + accountId + " is not active",
                accountId);
    }
}
