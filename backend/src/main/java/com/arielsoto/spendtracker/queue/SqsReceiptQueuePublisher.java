package com.arielsoto.spendtracker.queue;

import java.util.UUID;

import software.amazon.awssdk.services.sqs.SqsClient;

public class SqsReceiptQueuePublisher implements ReceiptQueuePublisher {

    private final SqsClient sqs;
    private final String extractionQueueUrl;
    private final String enrichmentQueueUrl;

    public SqsReceiptQueuePublisher(SqsClient sqs, SqsProperties properties) {
        this.sqs = sqs;
        this.extractionQueueUrl = properties.receiptExtractionQueueUrl();
        this.enrichmentQueueUrl = properties.spendEnrichmentQueueUrl();
    }

    @Override
    public void publishForExtraction(UUID spendId) {
        send(extractionQueueUrl, spendId);
    }

    @Override
    public void publishForEnrichment(UUID spendId) {
        send(enrichmentQueueUrl, spendId);
    }

    private void send(String queueUrl, UUID spendId) {
        sqs.sendMessage(request -> request.queueUrl(queueUrl).messageBody(spendId.toString()));
    }
}
