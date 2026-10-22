package com.arielsoto.spendtracker.queue;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.sqs")
public record SqsProperties(
    String region,
    String receiptExtractionQueueUrl,
    String spendEnrichmentQueueUrl,
    int maxReceiveCount,
    boolean pollingEnabled
) {}
