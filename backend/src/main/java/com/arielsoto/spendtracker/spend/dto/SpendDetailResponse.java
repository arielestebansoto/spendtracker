package com.arielsoto.spendtracker.spend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record SpendDetailResponse(
    UUID id,
    UUID categoryId,
    String category,
    String description,
    BigDecimal amount,
    String currency,
    LocalDate spendDate,
    LocalDateTime createdAt
) {
}