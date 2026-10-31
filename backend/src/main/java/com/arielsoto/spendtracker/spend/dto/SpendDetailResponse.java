package com.arielsoto.spendtracker.spend.dto;

import com.arielsoto.spendtracker.spend.FailureReason;
import com.arielsoto.spendtracker.spend.SpendStatus;

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
    LocalDate spendDate,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    String receiptUrl,
    SpendStatus status,
    // Set only on a FAILED Spend that cannot be retried.
    FailureReason failureReason
) {
}