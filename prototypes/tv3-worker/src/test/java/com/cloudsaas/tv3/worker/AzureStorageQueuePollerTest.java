package com.cloudsaas.tv3.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.LockSupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AzureStorageQueuePollerTest {
    private static final String VALID_BODY = """
            {"schemaVersion":1,"jobId":"00000000-0000-0000-0000-00000000002a",
             "claimedTenantId":"00000000-0000-0000-0000-00000000000a"}
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private FakeQueues queues;
    private QueueMessageContractParser parser;
    private QueueStorageProperties properties;

    @BeforeEach
    void setUp() {
        queues = new FakeQueues();
        parser = new QueueMessageContractParser(objectMapper);
        properties = new QueueStorageProperties(
                "export-jobs",
                "export-jobs-poison",
                Duration.ofSeconds(30),
                3,
                Duration.ofSeconds(4),
                Duration.ofSeconds(40),
                null,
                null);
    }

    @Test
    void handlesEmptyQueueWithoutCallingTheProcessor() {
        assertThat(poller(message -> {
            throw new AssertionError("processor must not run for an empty queue");
        }).pollOnce()).isEqualTo(ExportQueuePoller.PollResult.EMPTY);

        assertThat(queues.deleted).isEmpty();
        assertThat(queues.updated).isEmpty();
    }

    @Test
    void durablyProcessedMessageIsDeletedUsingItsReceipt() {
        queues.add(message("message-1", "receipt-1", VALID_BODY, 1));

        assertThat(poller(ignored -> MessageProcessingDecision.DURABLY_COMPLETED).pollOnce())
                .isEqualTo(ExportQueuePoller.PollResult.MESSAGE_HANDLED);

        assertThat(queues.deleted).containsExactly(new Acknowledgement("message-1", "receipt-1"));
        assertThat(queues.poisonBodies).isEmpty();
    }

    @Test
    void malformedMessageIsSanitizedIntoPoisonQueueBeforeSourceDelete() throws Exception {
        queues.add(message("message-2", "receipt-2", "DO_NOT_COPY_THIS_BODY", 1));

        poller(ignored -> {
            throw new AssertionError("invalid input must not reach the processor");
        }).pollOnce();

        assertThat(queues.events).containsExactly("poison", "delete");
        JsonNode envelope = objectMapper.readTree(queues.poisonBodies.get(0));
        assertThat(envelope.get("errorCode").asText()).isEqualTo("INVALID_JSON");
        assertThat(queues.poisonBodies.get(0)).doesNotContain("DO_NOT_COPY_THIS_BODY");
    }

    @Test
    void doesNotDeleteSourceWhenPoisonSendFails() {
        queues.add(message("message-poison-failure", "receipt-5", "invalid", 1));
        queues.poisonFailure = new IllegalStateException("poison queue unavailable");

        assertThatThrownBy(() -> poller(ignored -> {
            throw new AssertionError("invalid input must not reach the processor");
        }).pollOnce()).isInstanceOf(IllegalStateException.class);

        assertThat(queues.deleted).isEmpty();
    }

    @Test
    void queueConnectionFailurePropagatesWithoutAcknowledging() {
        queues.receiveFailure = new IllegalStateException("provider details must not be logged");

        assertThatThrownBy(() -> poller(ignored -> MessageProcessingDecision.DURABLY_COMPLETED).pollOnce())
                .isInstanceOf(IllegalStateException.class);

        assertThat(queues.deleted).isEmpty();
        assertThat(queues.updated).isEmpty();
    }

    @Test
    void transientProcessingFailureUpdatesVisibilityButDoesNotDelete() {
        queues.add(message("message-1", "receipt-1", VALID_BODY, 1));

        poller(ignored -> {
            throw new IllegalStateException("transient provider message");
        }).pollOnce();

        assertThat(queues.updated).hasSize(1);
        QueueUpdate update = queues.updated.get(0);
        assertThat(update.acknowledgement()).isEqualTo(new Acknowledgement("message-1", "receipt-1"));
        assertThat(update.body()).isEqualTo(VALID_BODY);
        assertThat(update.visibilityTimeout()).isBetween(Duration.ofSeconds(2), Duration.ofSeconds(4));
        assertThat(queues.deleted).isEmpty();
    }

    @Test
    void renewsVisibilityDuringLongProcessingAndDeletesWithLatestReceipt() throws Exception {
        properties = new QueueStorageProperties(
                "export-jobs",
                "export-jobs-poison",
                Duration.ofMillis(100),
                3,
                Duration.ofSeconds(4),
                Duration.ofSeconds(40),
                null,
                null);
        queues.expectedVisibilityTimeout = Duration.ofMillis(100);
        queues.add(message("message-lease", "initial-receipt", VALID_BODY, 1));

        poller(ignored -> {
            LockSupport.parkNanos(Duration.ofMillis(260).toNanos());
            return MessageProcessingDecision.DURABLY_COMPLETED;
        }).pollOnce();

        assertThat(queues.updated).isNotEmpty();
        assertThat(queues.deleted).containsExactly(new Acknowledgement(
                "message-lease",
                queues.updated.get(queues.updated.size() - 1).nextPopReceipt()));
    }

    @Test
    void retryableFailureAtLimitMovesOnlySanitizedMetadataToPoison() throws Exception {
        queues.add(message("message-3", "receipt-3", VALID_BODY, 3));

        poller(ignored -> MessageProcessingDecision.RETRYABLE_FAILURE).pollOnce();

        JsonNode envelope = objectMapper.readTree(queues.poisonBodies.get(0));
        assertThat(envelope.get("errorCode").asText()).isEqualTo("RETRY_LIMIT_EXCEEDED");
        assertThat(queues.poisonBodies.get(0)).doesNotContain("claimedTenantId", "jobId");
        assertThat(queues.deleted).containsExactly(new Acknowledgement("message-3", "receipt-3"));
        assertThat(queues.events).containsExactly("poison", "delete");
    }

    @Test
    void duplicateDeliveryUsesEachCurrentReceiptAndLeavesIdempotencyToProcessor() {
        queues.add(
                message("message-4", "receipt-4-first", VALID_BODY, 1),
                message("message-4", "receipt-4-redelivered", VALID_BODY, 2));
        List<ExportQueueMessage> processed = new ArrayList<>();
        AzureStorageQueuePoller poller = poller(message -> {
            processed.add(message);
            return MessageProcessingDecision.DURABLY_COMPLETED;
        });

        poller.pollOnce();
        poller.pollOnce();

        assertThat(processed).hasSize(2);
        assertThat(queues.deleted).containsExactly(
                new Acknowledgement("message-4", "receipt-4-first"),
                new Acknowledgement("message-4", "receipt-4-redelivered"));
    }

    private AzureStorageQueuePoller poller(ExportMessageProcessor processor) {
        return new AzureStorageQueuePoller(queues, properties, parser, processor);
    }

    private static QueueDelivery message(String id, String receipt, String body, long dequeueCount) {
        return new QueueDelivery(id, receipt, body, dequeueCount);
    }

    private record Acknowledgement(String messageId, String popReceipt) {
    }

    private record QueueUpdate(
            Acknowledgement acknowledgement,
            String body,
            Duration visibilityTimeout,
            String nextPopReceipt) {
    }

    private static final class FakeQueues implements QueueStorageOperations {
        private final ArrayDeque<QueueDelivery> pending = new ArrayDeque<>();
        private final List<Acknowledgement> deleted = new ArrayList<>();
        private final List<QueueUpdate> updated = new ArrayList<>();
        private final List<String> poisonBodies = new ArrayList<>();
        private final List<String> events = new ArrayList<>();
        private RuntimeException receiveFailure;
        private RuntimeException poisonFailure;
        private Duration expectedVisibilityTimeout = Duration.ofSeconds(30);

        private void add(QueueDelivery... deliveries) {
            for (QueueDelivery delivery : deliveries) {
                pending.addLast(delivery);
            }
        }

        @Override
        public Optional<QueueDelivery> receive(Duration visibilityTimeout) {
            if (receiveFailure != null) {
                throw receiveFailure;
            }
            assertThat(visibilityTimeout).isEqualTo(expectedVisibilityTimeout);
            return Optional.ofNullable(pending.pollFirst());
        }

        @Override
        public String updateVisibility(String messageId, String popReceipt, String body, Duration visibilityTimeout) {
            String nextReceipt = "updated-receipt-" + (updated.size() + 1);
            updated.add(new QueueUpdate(
                    new Acknowledgement(messageId, popReceipt),
                    body,
                    visibilityTimeout,
                    nextReceipt));
            events.add("update");
            return nextReceipt;
        }

        @Override
        public void delete(String messageId, String popReceipt) {
            deleted.add(new Acknowledgement(messageId, popReceipt));
            events.add("delete");
        }

        @Override
        public void sendPoison(String body) {
            if (poisonFailure != null) {
                throw poisonFailure;
            }
            poisonBodies.add(body);
            events.add("poison");
        }
    }
}
