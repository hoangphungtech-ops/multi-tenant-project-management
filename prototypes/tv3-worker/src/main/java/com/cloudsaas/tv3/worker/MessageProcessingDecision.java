package com.cloudsaas.tv3.worker;

public enum MessageProcessingDecision {
    DURABLY_COMPLETED,
    DURABLY_REJECTED,
    POISON_TENANT_MISMATCH,
    RETRYABLE_FAILURE
}
