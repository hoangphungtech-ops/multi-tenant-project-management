package com.cloudsaas.tv3.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class QueuePollingLifecycleTest {
    @Test
    void retriesPollFailuresAndStopsGracefully() throws Exception {
        AtomicInteger polls = new AtomicInteger();
        CountDownLatch successfulPoll = new CountDownLatch(1);
        ExportQueuePoller poller = () -> {
            if (polls.incrementAndGet() == 1) {
                throw new IllegalStateException("sensitive provider message must not be logged");
            }
            successfulPoll.countDown();
            return ExportQueuePoller.PollResult.MESSAGE_HANDLED;
        };
        WorkerProperties properties = new WorkerProperties(
                Duration.ofMillis(5),
                Duration.ofMillis(10),
                Duration.ofMillis(50),
                Duration.ofSeconds(1));
        QueuePollingLifecycle lifecycle = new QueuePollingLifecycle(poller, properties);

        lifecycle.start();
        assertThat(successfulPoll.await(2, TimeUnit.SECONDS)).isTrue();
        lifecycle.stop();

        assertThat(polls.get()).isGreaterThanOrEqualTo(2);
        assertThat(lifecycle.isRunning()).isFalse();
    }

    @Test
    void rejectsDurationsThatWouldBecomeBusyPollingAfterMillisecondConversion() {
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> new WorkerProperties(
                        Duration.ofNanos(1),
                        Duration.ofMillis(10),
                        Duration.ofMillis(20),
                        Duration.ofSeconds(1)));
    }
}
