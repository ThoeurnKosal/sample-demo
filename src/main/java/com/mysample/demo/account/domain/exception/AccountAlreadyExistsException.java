package com.mysample.demo.account.domain.exception;

import com.mysample.demo.shared.error.ErrorCode;
import com.mysample.demo.shared.error.exception.ConflictException;
import java.io.Serial;
import java.util.UUID;

/** 409: the customer already has an ACTIVE account in this currency. */
public final class AccountAlreadyExistsException extends ConflictException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AccountAlreadyExistsException(UUID customerId, String currency) {
        super(ErrorCode.ACCOUNT_ALREADY_EXISTS,
                "Customer " + customerId + " already has an active " + currency + " account",
                customerId, currency);
    }
}
