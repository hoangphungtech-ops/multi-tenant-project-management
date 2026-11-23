package com.project.backendapi.export;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ExportJobRepository extends JpaRepository<ExportJob, UUID> {
    Optional<ExportJob> findByIdAndTenantId(UUID id, UUID tenantId);
}
