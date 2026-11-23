package com.project.backendapi.workspace;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {
    List<Workspace> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<Workspace> findByIdAndTenantId(UUID id, UUID tenantId);
}
