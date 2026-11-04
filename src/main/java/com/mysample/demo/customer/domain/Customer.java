package com.mysample.demo.customer.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Customer(UUID id, Long version, Integer externalId, CustomerDetails details,
                       Instant createdAt, Instant updatedAt) {

    public Customer {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(details, "details");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static Customer register(UUID id, CustomerDetails details, Instant now) {
        return new Customer(id, null, null, details, now, now);
    }

    /** A customer created from a user in the external directory. New row: version is null (INSERT). */
    public static Customer importFrom(UUID id, ExternalUser user, Instant now) {
        return new Customer(id, null, user.externalId(), user.details(), now, now);
    }

    public boolean isLinkedToDirectory(){
        return externalId != null;
    }

}
