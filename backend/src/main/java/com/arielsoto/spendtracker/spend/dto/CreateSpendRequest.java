package com.arielsoto.spendtracker.spend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateSpendRequest(
    UUID categoryId,

    String description,

    @NotNull
    @DecimalMin("0.01")
    BigDecimal amount,

    @NotNull
    LocalDate spendDate
) {
    
}
