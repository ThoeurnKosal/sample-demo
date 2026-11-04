package com.mysample.demo.shared.error.exception;

import com.mysample.demo.shared.error.ErrorCode;
import java.io.Serial;

public abstract non-sealed class BusinessRuleException extends ApplicationException{
    @Serial
    private static final long serialVersionUID = 1L;

    protected BusinessRuleException(ErrorCode errorCode, String logMessage, Object... messageArguments) {
        super(errorCode, logMessage, null, messageArguments);
    }
}
