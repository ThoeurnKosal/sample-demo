package com.mysample.demo.customer.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ImportCustomerRequest(@NotNull @Min(1) Integer externalUserId) {
}
