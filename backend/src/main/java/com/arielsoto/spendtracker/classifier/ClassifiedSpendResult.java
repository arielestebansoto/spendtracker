package com.arielsoto.spendtracker.classifier;

import java.util.Map;

/**
 * The parsed classification plus the full Bedrock response, kept so data can be reprocessed later.
 * rawResponse is null when the call or the parsing failed.
 */
public record ClassifiedSpendResult(
    ClassifiedSpend classified,
    long inputTokens,
    long outputTokens,
    Map<String, Object> rawResponse
) {}
