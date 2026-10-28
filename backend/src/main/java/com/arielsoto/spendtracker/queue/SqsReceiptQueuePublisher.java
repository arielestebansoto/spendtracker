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
        send(extractionQueueUrl, new SpendMessage(spendId, false));
    }

    @Override
    public void publishForEnrichment(UUID spendId) {
        send(enrichmentQueueUrl, new SpendMessage(spendId, false));
    }

    @Override
    public void publishForCategoryOnlyEnrichment(UUID spendId) {
        send(enrichmentQueueUrl, new SpendMessage(spendId, true));
    }

    private void send(String queueUrl, SpendMessage message) {
        sqs.sendMessage(request -> request.queueUrl(queueUrl).messageBody(message.toBody()));
    }
}
