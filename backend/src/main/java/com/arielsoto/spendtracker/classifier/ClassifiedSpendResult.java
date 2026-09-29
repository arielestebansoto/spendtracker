package com.arielsoto.spendtracker.classifier;

public record ClassifiedSpendResult(
    ClassifiedSpend classified,
    long inputTokens,
    long outputTokens
) {}
