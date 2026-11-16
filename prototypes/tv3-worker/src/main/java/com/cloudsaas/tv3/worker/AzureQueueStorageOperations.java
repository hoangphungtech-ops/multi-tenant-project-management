package com.cloudsaas.tv3.worker;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.Optional;

import com.azure.core.http.rest.PagedIterable;
import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.models.QueueMessageItem;

public final class AzureQueueStorageOperations implements QueueStorageOperations {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final QueueClient exportQueue;
    private final QueueClient poisonQueue;

    public AzureQueueStorageOperations(QueueClient exportQueue, QueueClient poisonQueue) {
        this.exportQueue = exportQueue;
        this.poisonQueue = poisonQueue;
    }

    @Override
    public Optional<QueueDelivery> receive(Duration visibilityTimeout) {
        PagedIterable<QueueMessageItem> received =
                exportQueue.receiveMessages(1, visibilityTimeout, REQUEST_TIMEOUT, null);
        Iterator<QueueMessageItem> iterator = received.iterator();
        if (!iterator.hasNext()) {
            return Optional.empty();
        }
        QueueMessageItem message = iterator.next();
        return Optional.of(new QueueDelivery(
                message.getMessageId(),
                message.getPopReceipt(),
                new String(message.getBody().toBytes(), StandardCharsets.UTF_8),
                message.getDequeueCount()));
    }

    @Override
    public String updateVisibility(String messageId, String popReceipt, String body, Duration visibilityTimeout) {
        return exportQueue.updateMessage(messageId, popReceipt, body, visibilityTimeout).getPopReceipt();
    }

    @Override
    public void delete(String messageId, String popReceipt) {
        exportQueue.deleteMessage(messageId, popReceipt);
    }

    @Override
    public void sendPoison(String body) {
        poisonQueue.sendMessage(body);
    }
}
