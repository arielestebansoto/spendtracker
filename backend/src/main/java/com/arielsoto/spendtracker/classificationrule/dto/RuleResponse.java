package com.arielsoto.spendtracker.classificationrule.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record RuleResponse(
    UUID id,
    String condition,
    UUID categoryId,
    String categoryName,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
