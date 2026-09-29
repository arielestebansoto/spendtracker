package com.arielsoto.spendtracker.aiusage.dto;

public record AiUsageResponse(
    int analyzeExpenseUsed, int analyzeExpenseLimit,
    int detectTextUsed, int detectTextLimit,
    long bedrockInputUsed, long bedrockInputLimit,
    long bedrockOutputUsed, long bedrockOutputLimit
) {}
