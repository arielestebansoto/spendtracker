package com.arielsoto.spendtracker.receipt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Maximum Receipt uploads per user in the last minute and in the last day. */
@ConfigurationProperties(prefix = "app.receipt-upload-limit")
public record ReceiptUploadLimitProperties(
    int perMinute,
    int perDay
) {}
