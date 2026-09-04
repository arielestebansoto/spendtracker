package com.arielsoto.spendtracker.ocr;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ocr")
public record OcrProperties(
    String projectId,
    String credentialsPath
) {}
