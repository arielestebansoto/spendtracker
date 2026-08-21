package com.arielsoto.spendtracker.spend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SpendListItemResponse(
    UUID id,
    String category,
    BigDecimal amount,
    LocalDate spendDate,
    String description
) {
}