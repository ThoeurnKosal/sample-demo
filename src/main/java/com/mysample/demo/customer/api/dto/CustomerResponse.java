package com.mysample.demo.customer.api.dto;

import java.time.Instant;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        Integer externalUserId,
        String fullName,
        String email,
        String phone,
        String city,
        Instant createdAt
) {
}
