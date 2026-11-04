package com.mysample.demo.shared.error.exception;

import com.mysample.demo.shared.error.ErrorCode;
import java.io.Serial;
import java.util.Map;
import java.util.Objects;
import lombok.Getter;

public abstract sealed class ApplicationException extends RuntimeException
permits BusinessRuleException, ConflictException, DownstreamRejectedException, DownstreamUnavailableException , NotFoundException{
    @Serial
    private static final long serialVersionUID = 1L;

    @Getter
    private final ErrorCode errorCode;

    private final transient Object[] messageArguments;

    protected ApplicationException(ErrorCode errorCode, String logMessage, Throwable cause, Object... messageArguments) {
        super(logMessage, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
        this.messageArguments = messageArguments == null ? new Object[0] : messageArguments.clone();
    }

    /** Arguments for the placeholders ({0}, {1}...) of the client-facing message. */
    public Object[] getMessageArguments() {
        return messageArguments.clone();
    }

    /** Extra machine-readable fields added to the problem response. Override to add some. */
    public Map<String, Object> getProperties() {
        return Map.of();
    }
}
