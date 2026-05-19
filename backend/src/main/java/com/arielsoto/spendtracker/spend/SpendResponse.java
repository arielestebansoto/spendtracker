package com.arielsoto.spendtracker.spend;

import java.math.BigDecimal;
import java.time.LocalDate;

import java.util.UUID;

public record SpendResponse(
    UUID id,
    UUID categoryId,
    String category,
    String description,
    BigDecimal amount,
    String currency,
    LocalDate spendDate
) {
    
}
