package com.cloudsaas.tv3.worker;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ThreadLocalRandom;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public final class AzureStorageQueuePoller implements ExportQueuePoller {
    private static final Logger log = LoggerFactory.getLogger(AzureStorageQueuePoller.class);
    private final QueueStorageOperations queues;
    private final QueueStorageProperties properties;
    private final QueueMessageContractParser parser;
    private final ExportMessageProcessor processor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AzureStorageQueuePoller(
            QueueStorageOperations queues,
            QueueStorageProperties properties,
            QueueMessageContractParser parser,
            ExportMessageProcessor processor) {
        this.queues = Objects.requireNonNull(queues);
        this.properties = Objects.requireNonNull(properties);
        this.parser = Objects.requireNonNull(parser);
        this.processor = Objects.requireNonNull(processor);
    }

    @Override
    public PollResult pollOnce() {
        var delivery = queues.receive(properties.visibilityTimeout());
        if (delivery.isEmpty()) {
            return PollResult.EMPTY;
        }
        QueueDelivery message = delivery.get();

        String messageId = required(message.messageId(), "messageId");
        String popReceipt = required(message.popReceipt(), "popReceipt");
        String body = message.body();
        ExportQueueMessage parsed;
        try {
            parsed = parser.parse(body);
        } catch (QueueMessageContractException ex) {
            moveToPoison(message, messageId, popReceipt, ex.errorCode());
            return PollResult.MESSAGE_HANDLED;
        }

        try (var jobId = MDC.putCloseable("jobId", parsed.jobId().toString());
                var correlationId = MDC.putCloseable("correlationId", UUID.randomUUID().toString())) {
            return process(message, messageId, popReceipt, body, parsed);
        }
    }

    private PollResult process(QueueDelivery message, String messageId, String popReceipt,
            String body, ExportQueueMessage parsed) {
        long started = System.nanoTime();
        MessageProcessingDecision decision;
        String retryErrorCode = "RETRY_LIMIT_EXCEEDED";
        MessageVisibilityLease lease = new MessageVisibilityLease(message, body);
        try (lease) {
            try {
                decision = Objects.requireNonNull(processor.process(parsed), "processor decision");
            } catch (RuntimeException ex) {
                decision = MessageProcessingDecision.RETRYABLE_FAILURE;
                retryErrorCode = "PROCESSING_ERROR";
            }
        }
        popReceipt = lease.popReceipt();

        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        log.atInfo().addKeyValue("outcome", decision).addKeyValue("durationMs", durationMs)
                .log("export_processing_result outcome={} durationMs={}", decision, durationMs);
        return switch (decision) {
            case POISON_TENANT_MISMATCH -> {
                moveToPoison(message, messageId, popReceipt, "TENANT_MISMATCH");
                yield PollResult.MESSAGE_HANDLED;
            }
            case DURABLY_COMPLETED, DURABLY_REJECTED -> {
                queues.delete(messageId, popReceipt);
                yield PollResult.MESSAGE_HANDLED;
            }
            case RETRYABLE_FAILURE -> {
                retryOrPoison(message, messageId, popReceipt, body, retryErrorCode);
                yield PollResult.MESSAGE_HANDLED;
            }
        };
    }

    private void retryOrPoison(
            QueueDelivery message,
            String messageId,
            String popReceipt,
            String body,
            String poisonErrorCode) {
        long dequeueCount = Math.max(1, message.dequeueCount());
        if (dequeueCount >= properties.maxDequeueCount()) {
            moveToPoison(message, messageId, popReceipt, poisonErrorCode);
            return;
        }
        queues.updateVisibility(
                messageId,
                popReceipt,
                body,
                retryVisibility(dequeueCount));
    }

    private void moveToPoison(
            QueueDelivery source,
            String messageId,
            String popReceipt,
            String errorCode) {
        PoisonQueueEnvelope envelope = new PoisonQueueEnvelope(
                1,
                messageId,
                errorCode,
                Math.max(1, source.dequeueCount()),
                Instant.now().toString());
        try {
            queues.sendPoison(objectMapper.writeValueAsString(envelope));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize sanitized poison envelope", ex);
        }
        queues.delete(messageId, popReceipt);
    }

    private final class MessageVisibilityLease implements AutoCloseable {
        private final QueueDelivery message;
        private final String body;
        private final AtomicReference<String> popReceipt;
        private final AtomicReference<RuntimeException> renewalFailure = new AtomicReference<>();
        private final ScheduledExecutorService renewer;

        private MessageVisibilityLease(QueueDelivery message, String body) {
            this.message = message;
            this.body = body;
            popReceipt = new AtomicReference<>(message.popReceipt());
            renewer = Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "tv3-queue-visibility");
                thread.setDaemon(true);
                return thread;
            });
            long intervalMillis = Math.max(1, properties.visibilityTimeout().toMillis() / 2);
            renewer.scheduleAtFixedRate(
                    this::renew,
                    intervalMillis,
                    intervalMillis,
                    TimeUnit.MILLISECONDS);
        }

        private void renew() {
            if (renewalFailure.get() != null) {
                return;
            }
            try {
                String updatedReceipt = queues.updateVisibility(
                        message.messageId(),
                        popReceipt.get(),
                        body,
                        properties.visibilityTimeout());
                popReceipt.set(required(updatedReceipt, "updated popReceipt"));
            } catch (RuntimeException ex) {
                renewalFailure.compareAndSet(null, ex);
                renewer.shutdown();
            }
        }

        private String popReceipt() {
            return popReceipt.get();
        }

        @Override
        public void close() {
            renewer.shutdown();
            try {
                if (!renewer.awaitTermination(5, TimeUnit.SECONDS)) {
                    renewer.shutdownNow();
                    throw new IllegalStateException("Timed out while stopping queue visibility renewer");
                }
            } catch (InterruptedException ex) {
                renewer.shutdownNow();
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while stopping queue visibility renewer", ex);
            }
            RuntimeException failure = renewalFailure.get();
            if (failure != null) {
                throw new IllegalStateException("Unable to renew queue message visibility", failure);
            }
        }
    }

    private Duration retryVisibility(long dequeueCount) {
        long initialMillis = properties.initialRetryDelay().toMillis();
        long maxMillis = properties.maxRetryDelay().toMillis();
        int shifts = (int) Math.min(62, Math.max(0, dequeueCount - 1));
        long exponentialMillis = initialMillis > (Long.MAX_VALUE >> shifts)
                ? Long.MAX_VALUE
                : initialMillis << shifts;
        long cappedMillis = Math.min(maxMillis, exponentialMillis);
        long floorMillis = Math.max(1, cappedMillis / 2);
        long rangeMillis = Math.max(1, cappedMillis - floorMillis + 1);
        return Duration.ofMillis(floorMillis + ThreadLocalRandom.current().nextLong(rangeMillis));
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Azure Queue response omitted " + name);
        }
        return value;
    }

    private record PoisonQueueEnvelope(
            int schemaVersion,
            String sourceMessageId,
            String errorCode,
            long dequeueCount,
            String failedAt) {
    }
}
