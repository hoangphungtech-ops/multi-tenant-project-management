package com.cloudsaas.tv3.worker;

@FunctionalInterface
public interface ExportQueuePoller {
    PollResult pollOnce() throws Exception;

    enum PollResult {
        EMPTY,
        MESSAGE_HANDLED
    }
}
