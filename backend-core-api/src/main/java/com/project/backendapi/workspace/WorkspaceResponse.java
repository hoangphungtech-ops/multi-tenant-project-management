package com.project.backendapi.workspace;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WorkspaceResponse(UUID id, UUID tenantId, String name, String description,
                                OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    public static WorkspaceResponse from(Workspace w) {
        return new WorkspaceResponse(w.getId(), w.getTenantId(), w.getName(), w.getDescription(),
                                     w.getCreatedAt(), w.getUpdatedAt());
    }
}
