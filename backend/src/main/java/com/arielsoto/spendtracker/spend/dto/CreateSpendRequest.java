package com.arielsoto.spendtracker.spend.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateSpendRequest(
    @NotNull
    UUID categoryId,

    String description,

    @NotNull
    @DecimalMin("0.01")
    BigDecimal amount,

    @NotBlank
    String currency,

    @NotNull
    LocalDate spendDate
) {
    
}
