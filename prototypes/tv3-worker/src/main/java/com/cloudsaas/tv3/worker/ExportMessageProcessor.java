package com.cloudsaas.tv3.worker;

@FunctionalInterface
public interface ExportMessageProcessor {
    /**
     * The durable decisions may only be returned after the corresponding job state has committed.
     * The implementation must resolve jobId from TV2's authoritative store and must not trust
     * claimedTenantId when selecting a tenant or reading tenant data.
     */
    MessageProcessingDecision process(ExportQueueMessage message);
}
