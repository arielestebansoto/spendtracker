package com.arielsoto.spendtracker.aiusage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai-usage")
public record AiUsageProperties(
    Limits global,
    Limits user
) {
    public record Limits(
        int analyzeExpensePages,
        int detectTextPages,
        long bedrockInputTokens,
        long bedrockOutputTokens
    ) {}
}
