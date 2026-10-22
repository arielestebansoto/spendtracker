package com.arielsoto.spendtracker.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;

@ExtendWith(MockitoExtension.class)
class SqsQueuePollerTest {

    private static final String QUEUE_URL = "https://sqs.test/queue";

    @Mock
    private SqsClient sqs;

    private final List<String> calls = new ArrayList<>();

    private SqsQueuePoller pollerWhoseWorkerThrows(boolean throwing) {
        return new SqsQueuePoller("test", sqs, QUEUE_URL, 3, (spendId, lastAttempt) -> {
            calls.add(spendId + ":" + lastAttempt);
            if (throwing) {
                throw new RuntimeException("boom");
            }
        });
    }

    private Message message(String body, int receiveCount) {
        return Message.builder()
            .body(body)
            .receiptHandle("handle")
            .attributes(Map.of(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT, String.valueOf(receiveCount)))
            .build();
    }

    @Test
    void successfulMessageIsDeleted() {
        UUID spendId = UUID.randomUUID();

        pollerWhoseWorkerThrows(false).handle(message(spendId.toString(), 1));

        assertEquals(List.of(spendId + ":false"), calls);
        ArgumentCaptor<DeleteMessageRequest> delete = ArgumentCaptor.forClass(DeleteMessageRequest.class);
        verify(sqs).deleteMessage(delete.capture());
        assertEquals(QUEUE_URL, delete.getValue().queueUrl());
        assertEquals("handle", delete.getValue().receiptHandle());
    }

    @Test
    void failedMessageIsNotDeletedSoSqsRedeliversIt() {
        pollerWhoseWorkerThrows(true).handle(message(UUID.randomUUID().toString(), 1));

        verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void messageReceivedForTheLastTimeIsFlaggedAsLastAttempt() {
        UUID spendId = UUID.randomUUID();

        pollerWhoseWorkerThrows(false).handle(message(spendId.toString(), 3));

        assertEquals(List.of(spendId + ":true"), calls);
    }

    @Test
    void malformedMessageIsDeletedWithoutCallingTheWorker() {
        pollerWhoseWorkerThrows(false).handle(message("not-a-uuid", 1));

        assertEquals(List.of(), calls);
        verify(sqs).deleteMessage(any(DeleteMessageRequest.class));
    }
}
