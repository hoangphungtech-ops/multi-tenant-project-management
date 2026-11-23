package com.cloudsaas.tv3.worker;

import java.time.Duration;
import java.util.Optional;

public interface QueueStorageOperations {
    Optional<QueueDelivery> receive(Duration visibilityTimeout);

    String updateVisibility(String messageId, String popReceipt, String body, Duration visibilityTimeout);

    void delete(String messageId, String popReceipt);

    void sendPoison(String body);
}
