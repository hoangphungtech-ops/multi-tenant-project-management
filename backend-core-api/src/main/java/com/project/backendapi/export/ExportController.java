package com.project.backendapi.export;

import com.project.backendapi.azure.BlobSasService;
import com.project.backendapi.security.CurrentUser;
import com.project.backendapi.tenant.TenantMembershipService;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.time.OffsetDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/exports")
public class ExportController {
    private final ExportJobRepository jobs;
    private final TenantMembershipService memberships;
    private final CurrentUser currentUser;
    private final ExportQueueService queue;
    private final BlobSasService blobSasService;
    private final JdbcTemplate jdbc;

    public ExportController(ExportJobRepository jobs, TenantMembershipService memberships, CurrentUser currentUser,
                            ExportQueueService queue, BlobSasService blobSasService, JdbcTemplate jdbc) {
        this.jobs=jobs; this.memberships=memberships; this.currentUser=currentUser;
        this.queue=queue; this.blobSasService=blobSasService; this.jdbc=jdbc;
    }
    private String user() { return currentUser.userKey(SecurityContextHolder.getContext().getAuthentication()); }

    @PostMapping
    public ResponseEntity<Map<String,Object>> create(@PathVariable UUID tenantId) {
        UUID requesterUserId = memberships.requireMemberUserId(tenantId, user(), "OWNER", "MEMBER");
        jdbc.queryForObject("select set_config('app.tenant_id', ?, true)", String.class, tenantId.toString());
        ExportJob job = jobs.save(new ExportJob(tenantId, requesterUserId));
        String messageId = queue.enqueue(job.getId(), tenantId);
        job.setQueueMessageId(messageId);
        jobs.save(job);
        return ResponseEntity.accepted().body(Map.of(
            "id", job.getId(),
            "jobId", job.getId(),
            "status", job.getStatus(),
            "requestedBy", requesterUserId,
            "queueMessageId", messageId
        ));
    }

    @GetMapping("/{jobId}")
    public Map<String,Object> get(@PathVariable UUID tenantId, @PathVariable UUID jobId) {
        memberships.requireRole(tenantId, user(), "OWNER", "MEMBER", "GUEST");
        jdbc.queryForObject("select set_config('app.tenant_id', ?, true)", String.class, tenantId.toString());
        ExportJob job = jobs.findByIdAndTenantId(jobId, tenantId).orElseThrow();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", job.getId());
        result.put("jobId", job.getId());
        result.put("status", job.getStatus());
        if (job.getRequestedBy() != null) result.put("requestedBy", job.getRequestedBy());
        if (job.getCreatedAt() != null) result.put("createdAt", job.getCreatedAt());
        if (job.getCompletedAt() != null) result.put("completedAt", job.getCompletedAt());
        if (job.getErrorMessage() != null) result.put("errorCode", job.getErrorMessage());
        return result;
    }

    @GetMapping("/{jobId}/download")
    public ResponseEntity<Map<String,Object>> download(@PathVariable UUID tenantId, @PathVariable UUID jobId) {
        memberships.requireRole(tenantId, user(), "OWNER", "MEMBER");
        jdbc.queryForObject("select set_config('app.tenant_id', ?, true)", String.class, tenantId.toString());
        ExportJob job = jobs.findByIdAndTenantId(jobId, tenantId).orElseThrow();
        if (!"COMPLETED".equals(job.getStatus()) || job.getBlobName() == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "EXPORT_NOT_READY", "message", "Export job is not completed yet"));
        }
        String sasUrl = blobSasService.createReadSas(job.getBlobName());
        return ResponseEntity.ok(Map.of(
            "url", sasUrl,
            "expiresAt", OffsetDateTime.now().plusMinutes(10).toString()
        ));
    }
}
