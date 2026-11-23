package com.cloudsaas.tv3.worker;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

public final class QueuePollingLifecycle implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(QueuePollingLifecycle.class);

    private final ExportQueuePoller poller;
    private final WorkerProperties properties;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile Thread workerThread;

    QueuePollingLifecycle(ExportQueuePoller poller, WorkerProperties properties) {
        this.poller = poller;
        this.properties = properties;
    }

    @Override
    public synchronized void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        workerThread = new Thread(this::poll, "tv3-export-worker");
        workerThread.setDaemon(false);
        workerThread.start();
        log.info("event=worker_polling_started");
    }

    private void poll() {
        long backoffMillis = properties.initialErrorBackoff().toMillis();
        long maxBackoffMillis = properties.maxErrorBackoff().toMillis();
        while (running.get()) {
            try {
                ExportQueuePoller.PollResult result = Objects.requireNonNull(
                        poller.pollOnce(), "pollOnce must return a result");
                backoffMillis = properties.initialErrorBackoff().toMillis();
                if (result == ExportQueuePoller.PollResult.EMPTY) {
                    sleep(properties.emptyPollInterval().toMillis());
                }
            } catch (InterruptedException ex) {
                running.set(false);
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ex) {
                log.warn("event=worker_poll_failed errorType={}", ex.getClass().getSimpleName());
                sleep(jitter(backoffMillis));
                backoffMillis = Math.min(maxBackoffMillis, saturatedDouble(backoffMillis));
            }
        }
    }

    private static long jitter(long backoffMillis) {
        long half = Math.max(1, backoffMillis / 2);
        long range = Math.max(1, backoffMillis - half + 1);
        return half + ThreadLocalRandom.current().nextLong(range);
    }

    private static long saturatedDouble(long value) {
        return value > Long.MAX_VALUE / 2 ? Long.MAX_VALUE : value * 2;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            if (running.get()) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public synchronized void stop() {
        if (!running.getAndSet(false)) {
            joinWorker();
            return;
        }
        joinWorker();
    }

    private void joinWorker() {
        Thread thread = workerThread;
        if (thread == null) {
            return;
        }
        thread.interrupt();
        try {
            thread.join(properties.shutdownTimeout().toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        if (thread.isAlive()) {
            log.warn("event=worker_shutdown_timeout");
        } else {
            log.info("event=worker_polling_stopped");
        }
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return 0;
    }
}
