package com.mysample.demo.account.domain.exception;

import com.mysample.demo.shared.error.ErrorCode;
import com.mysample.demo.shared.error.exception.BusinessRuleException;
import java.io.Serial;

/** 422: currency is not one of Account.SUPPORTED_CURRENCIES. */
public final class UnsupportedCurrencyException extends BusinessRuleException {

    @Serial
    private static final long serialVersionUID = 1L;

    public UnsupportedCurrencyException(String currency) {
        super(ErrorCode.UNSUPPORTED_CURRENCY,
                "Unsupported currency " + currency,
                currency);
    }
}
