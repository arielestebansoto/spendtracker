package com.arielsoto.spendtracker.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@EnableConfigurationProperties(S3Properties.class)
@ConditionalOnProperty(
    prefix = "app.storage.s3",
    name = "bucket-name",
    matchIfMissing = false
)
class S3StorageConfig {

    @Bean
    S3Client s3Client(S3Properties properties) {
        return S3Client.builder()
            .region(Region.of(properties.region()))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        properties.accessKeyId(),
                        properties.secretAccessKey()
                    )
                )
            )
            .build();
    }
}
