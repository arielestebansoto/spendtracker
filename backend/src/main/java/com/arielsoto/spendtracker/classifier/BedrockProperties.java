package com.arielsoto.spendtracker.classifier;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.bedrock")
public record BedrockProperties(
    String modelId,
    Duration apiCallTimeout
) {}
