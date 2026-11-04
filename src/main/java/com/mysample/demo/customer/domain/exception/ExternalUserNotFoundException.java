package com.mysample.demo.customer.domain.exception;

import com.mysample.demo.shared.error.ErrorCode;
import com.mysample.demo.shared.error.exception.NotFoundException;
import java.io.Serial;

public class ExternalUserNotFoundException extends NotFoundException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ExternalUserNotFoundException(int externalId) {
        super(ErrorCode.EXTERNAL_USER_NOT_FOUND, "No user " + externalId + " in the customer directory",
                String.valueOf(externalId)); // String, so MessageFormat does not print "1,000"
    }

}
