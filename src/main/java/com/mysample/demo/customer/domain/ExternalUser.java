package com.mysample.demo.customer.domain;

import java.util.Objects;

public record ExternalUser(int externalId, CustomerDetails details) {

    public ExternalUser {
        Objects.requireNonNull(details, "details");
        if (externalId <= 0) {
            throw new IllegalArgumentException("externalId must be positive");
        }
    }

}
