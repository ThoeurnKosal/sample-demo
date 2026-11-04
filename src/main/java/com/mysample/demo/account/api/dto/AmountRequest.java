package com.mysample.demo.account.api.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record AmountRequest(
        @NotNull
        @Positive
        @Digits(integer = 17, fraction = 2)
        BigDecimal amount) {
}

