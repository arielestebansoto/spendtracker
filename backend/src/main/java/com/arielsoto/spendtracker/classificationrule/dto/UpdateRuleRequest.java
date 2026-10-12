package com.arielsoto.spendtracker.classificationrule.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateRuleRequest(
    @NotBlank(message = "Condition must not be empty")
    @Size(max = 128, message = "Condition must be at most 128 characters")
    String condition,

    @NotNull(message = "Category is required")
    UUID categoryId
) {}
