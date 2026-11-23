package com.cloudsaas.tv3.worker;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("tv3.worker")
public record WorkerProperties(
        Duration emptyPollInterval,
        Duration initialErrorBackoff,
        Duration maxErrorBackoff,
        Duration shutdownTimeout) {

    public WorkerProperties {
        requirePositive("emptyPollInterval", emptyPollInterval);
        requirePositive("initialErrorBackoff", initialErrorBackoff);
        requirePositive("maxErrorBackoff", maxErrorBackoff);
        requirePositive("shutdownTimeout", shutdownTimeout);
        if (maxErrorBackoff.compareTo(initialErrorBackoff) < 0) {
            throw new IllegalArgumentException("maxErrorBackoff must be >= initialErrorBackoff");
        }
    }

    private static void requirePositive(String name, Duration value) {
        if (value == null || value.isZero() || value.isNegative() || value.toMillis() == 0) {
            throw new IllegalArgumentException(name + " must be at least one millisecond");
        }
    }
}
