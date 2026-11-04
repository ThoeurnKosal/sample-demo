package com.mysample.demo.account.domain.exception;

import com.mysample.demo.shared.error.ErrorCode;
import com.mysample.demo.shared.error.exception.NotFoundException;
import java.io.Serial;
import java.util.UUID;

/** 404: no account with this id. */
public final class AccountNotFoundException extends NotFoundException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AccountNotFoundException(UUID accountId) {
        super(ErrorCode.ACCOUNT_NOT_FOUND,
                "Account " + accountId + " not found",
                accountId);
    }
}
