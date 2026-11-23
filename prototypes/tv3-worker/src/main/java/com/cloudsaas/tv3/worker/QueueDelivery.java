package com.cloudsaas.tv3.worker;

public record QueueDelivery(
        String messageId,
        String popReceipt,
        String body,
        long dequeueCount) {
}
