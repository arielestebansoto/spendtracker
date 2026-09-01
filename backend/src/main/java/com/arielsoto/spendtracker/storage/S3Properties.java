package com.arielsoto.spendtracker.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage.s3")
public record S3Properties(
    String bucketName,
    String region,
    String accessKeyId,
    String secretAccessKey
) {}
