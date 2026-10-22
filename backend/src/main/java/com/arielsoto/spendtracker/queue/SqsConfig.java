package com.arielsoto.spendtracker.queue;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.arielsoto.spendtracker.receipt.ReceiptProcessingService;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
@EnableConfigurationProperties(SqsProperties.class)
class SqsConfig {

    @Bean
    SqsClient sqsClient(SqsProperties properties) {
        return SqsClient.builder()
            .region(Region.of(properties.region()))
            .build();
    }

    @Bean
    ReceiptQueuePublisher receiptQueuePublisher(SqsClient sqsClient, SqsProperties properties) {
        return new SqsReceiptQueuePublisher(sqsClient, properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.sqs", name = "polling-enabled", havingValue = "true")
    SqsQueuePoller extractionPoller(
        SqsClient sqsClient,
        SqsProperties properties,
        ReceiptProcessingService processingService
    ) {
        return new SqsQueuePoller(
            "receipt-extraction",
            sqsClient,
            properties.receiptExtractionQueueUrl(),
            properties.maxReceiveCount(),
            processingService::processExtraction
        );
    }
}
