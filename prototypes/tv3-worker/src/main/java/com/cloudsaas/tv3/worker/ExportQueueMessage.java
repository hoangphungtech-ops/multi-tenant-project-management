package com.cloudsaas.tv3.worker;

import java.util.UUID;

public record ExportQueueMessage(
        int schemaVersion,
        UUID jobId,
        UUID claimedTenantId,
        String requestId) {
}
