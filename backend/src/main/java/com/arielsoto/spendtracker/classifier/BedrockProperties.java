package com.arielsoto.spendtracker.classifier;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.bedrock")
public record BedrockProperties(
    String modelId
) {}
