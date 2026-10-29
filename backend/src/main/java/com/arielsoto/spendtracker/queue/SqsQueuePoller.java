package com.arielsoto.spendtracker.queue;

import java.util.function.BiConsumer;

import org.springframework.context.SmartLifecycle;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;

/**
 * Long-polls one queue on its own thread and hands each message (a Spend id, optionally flagged category-only) to the worker.
 * The worker receives whether this is the last delivery attempt SQS will make.
 * A message is deleted only when the worker returns normally; otherwise SQS redelivers it
 * after the visibility timeout and, after the last attempt, moves it to the dead-letter queue.
 */
@Slf4j
public class SqsQueuePoller implements SmartLifecycle {

    private static final int WAIT_TIME_SECONDS = 20;
    private static final long ERROR_BACKOFF_MILLIS = 5_000;

    private final String name;
    private final SqsClient sqs;
    private final String queueUrl;
    private final int maxReceiveCount;
    private final BiConsumer<SpendMessage, Boolean> worker;

    private volatile boolean running;
    private Thread thread;

    public SqsQueuePoller(
        String name,
        SqsClient sqs,
        String queueUrl,
        int maxReceiveCount,
        BiConsumer<SpendMessage, Boolean> worker
    ) {
        this.name = name;
        this.sqs = sqs;
        this.queueUrl = queueUrl;
        this.maxReceiveCount = maxReceiveCount;
        this.worker = worker;
    }

    @Override
    public void start() {
        running = true;
        thread = new Thread(this::pollLoop, "sqs-poller-" + name);
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void stop() {
        running = false;
        thread.interrupt();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void pollLoop() {
        log.info("sqs_poller_started name={}", name);
        while (running) {
            try {
                sqs.receiveMessage(request -> request
                        .queueUrl(queueUrl)
                        .maxNumberOfMessages(1)
                        .waitTimeSeconds(WAIT_TIME_SECONDS)
                        .messageSystemAttributeNames(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT))
                    .messages()
                    .forEach(this::handle);
            } catch (Exception e) {
                if (!running) {
                    break;
                }
                log.error("sqs_poller_error name={}", name, e);
                pauseBeforeRetrying();
            }
        }
        log.info("sqs_poller_stopped name={}", name);
    }

    void handle(Message message) {
        SpendMessage spendMessage;
        try {
            spendMessage = SpendMessage.parse(message.body());
        } catch (IllegalArgumentException e) {
            log.warn("sqs_message_malformed name={} messageId={}", name, message.messageId());
            delete(message);
            return;
        }

        int receiveCount = Integer.parseInt(
            message.attributes().getOrDefault(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT, "1")
        );
        try {
            worker.accept(spendMessage, receiveCount >= maxReceiveCount);
            delete(message);
        } catch (Exception e) {
            log.warn("sqs_message_failed name={} spendId={} receiveCount={}", name, spendMessage.spendId(), receiveCount, e);
        }
    }

    private void delete(Message message) {
        sqs.deleteMessage(DeleteMessageRequest.builder()
            .queueUrl(queueUrl)
            .receiptHandle(message.receiptHandle())
            .build());
    }

    private void pauseBeforeRetrying() {
        try {
            Thread.sleep(ERROR_BACKOFF_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
