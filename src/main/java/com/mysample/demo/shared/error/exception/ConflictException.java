package com.mysample.demo.shared.error.exception;

import com.mysample.demo.shared.error.ErrorCode;
import java.io.Serial;

public abstract non-sealed class ConflictException extends ApplicationException{
    @Serial
    private static final long serialVersionUID = 1L;

    protected ConflictException(ErrorCode errorCode, String logMessage, Object... messageArguments) {
        super(errorCode, logMessage, null, messageArguments);
    }
}
