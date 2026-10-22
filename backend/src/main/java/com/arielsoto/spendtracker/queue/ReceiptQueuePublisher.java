package com.arielsoto.spendtracker.queue;

import java.util.UUID;

/**
 * Publishes the Spend id to the queue of the next pipeline stage.
 * Messages carry only the Spend id; workers load everything else themselves.
 */
public interface ReceiptQueuePublisher {

    void publishForExtraction(UUID spendId);

    void publishForEnrichment(UUID spendId);
}
