package com.cloudsaas.tv3.worker;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("tv3.queue")
public record QueueStorageProperties(
        String queueName,
        String poisonQueueName,
        Duration visibilityTimeout,
        int maxDequeueCount,
        Duration initialRetryDelay,
        Duration maxRetryDelay,
        String connectionString,
        String endpoint) {

    private static final Duration MAX_QUEUE_DURATION = Duration.ofDays(7);

    public QueueStorageProperties {
        requireQueueName("queueName", queueName);
        requireQueueName("poisonQueueName", poisonQueueName);
        if (queueName.equals(poisonQueueName)) {
            throw new IllegalArgumentException("queueName and poisonQueueName must differ");
        }
        requireQueueDuration("visibilityTimeout", visibilityTimeout);
        requireQueueDuration("initialRetryDelay", initialRetryDelay);
        requireQueueDuration("maxRetryDelay", maxRetryDelay);
        if (initialRetryDelay.compareTo(maxRetryDelay) > 0) {
            throw new IllegalArgumentException("maxRetryDelay must be >= initialRetryDelay");
        }
        if (maxDequeueCount < 1) {
            throw new IllegalArgumentException("maxDequeueCount must be positive");
        }
    }

    private static void requireQueueName(String name, String value) {
        if (value == null || !value.matches("[a-z0-9](?:[a-z0-9-]{1,61}[a-z0-9])")) {
            throw new IllegalArgumentException(name + " must be a valid Azure Queue name");
        }
    }

    private static void requireQueueDuration(String name, Duration value) {
        if (value == null || value.isZero() || value.isNegative()
                || value.compareTo(MAX_QUEUE_DURATION) > 0 || value.toMillis() == 0) {
            throw new IllegalArgumentException(name + " must be between 1 ms and 7 days");
        }
    }
}
