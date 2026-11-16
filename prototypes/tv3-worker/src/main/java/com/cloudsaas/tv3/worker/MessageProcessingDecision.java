package com.cloudsaas.tv3.worker;

public enum MessageProcessingDecision {
    DURABLY_COMPLETED,
    DURABLY_REJECTED,
    RETRYABLE_FAILURE
}
