package com.project.backendapi.export;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name="export_jobs")
public class ExportJob {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tenant_id", nullable=false) private UUID tenantId;
    @Column(name="requested_by") private UUID requestedBy;
    @Column(nullable=false) private String status;
    @Column(name="queue_message_id") private String queueMessageId;
    @Column(name="blob_name") private String blobName;
    @Column(name="error_message") private String errorMessage;
    @Column(name="created_at") private OffsetDateTime createdAt;
    @Column(name="completed_at") private OffsetDateTime completedAt;
    protected ExportJob() {}
    public ExportJob(UUID tenantId) { this(tenantId, null); }
    public ExportJob(UUID tenantId, UUID requestedBy) {
        this.tenantId = tenantId;
        this.requestedBy = requestedBy;
        this.status = "PENDING";
        this.createdAt = OffsetDateTime.now();
    }
    public UUID getId(){return id;} public UUID getTenantId(){return tenantId;}
    public UUID getRequestedBy(){return requestedBy;}
    public String getStatus(){return status;} public void setQueueMessageId(String v){queueMessageId=v;}
    public String getBlobName(){return blobName;} public void setBlobName(String v){blobName=v;}
    public String getErrorMessage(){return errorMessage;}
    public OffsetDateTime getCreatedAt(){return createdAt;}
    public OffsetDateTime getCompletedAt(){return completedAt;}
}
