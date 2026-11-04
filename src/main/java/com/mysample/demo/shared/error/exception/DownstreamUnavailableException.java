package com.mysample.demo.shared.error.exception;

import com.mysample.demo.shared.error.ErrorCode;
import java.io.Serial;

public non-sealed class DownstreamUnavailableException extends ApplicationException{

    @Serial
    private static final long serialVersionUID = 1L;

    private final String system;

    /** HTTP status from the downstream system, or {@code null} for timeouts and I/O errors. */
    private final Integer upstreamStatus;

    public DownstreamUnavailableException(String system, Integer upstreamStatus, Throwable cause) {
        super(ErrorCode.DOWNSTREAM_UNAVAILABLE,
                "Downstream system '" + system + "' is unavailable (HTTP " + upstreamStatus + ")", cause);
        this.system = system;
        this.upstreamStatus = upstreamStatus;
    }

}
