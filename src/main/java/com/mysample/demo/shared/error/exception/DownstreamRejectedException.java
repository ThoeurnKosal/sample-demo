package com.mysample.demo.shared.error.exception;

import com.mysample.demo.shared.error.ErrorCode;
import java.io.Serial;

public non-sealed class DownstreamRejectedException extends ApplicationException{

    @Serial
    private static final long serialVersionUID = 1L;

    private final String system;
    private final int upstreamStatus;

    public DownstreamRejectedException(String system, int upstreamStatus) {
        super(ErrorCode.DOWNSTREAM_REJECTED,
                "Downstream system '" + system + "' rejected the request with HTTP " + upstreamStatus, null);
        this.system = system;
        this.upstreamStatus = upstreamStatus;
    }

}
