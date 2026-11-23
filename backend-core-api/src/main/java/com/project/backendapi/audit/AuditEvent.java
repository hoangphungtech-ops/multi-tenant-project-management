package com.project.backendapi.audit;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name="audit_events")
public class AuditEvent {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tenant_id", nullable=false) private UUID tenantId;
    @Column(name="actor_user_id") private UUID actorUserId;
    @Column(nullable=false) private String action;
    @Column(name="resource_type", nullable=false) private String resourceType;
    @Column(name="resource_id") private String resourceId;
    @Column(name="request_id") private String requestId;
    @Column(columnDefinition="jsonb") private String details;
    @Column(name="created_at") private OffsetDateTime createdAt;
    protected AuditEvent() {}
    public AuditEvent(UUID tenantId, String action, String resourceType, String resourceId, String requestId) {
        this.tenantId=tenantId; this.action=action; this.resourceType=resourceType;
        this.resourceId=resourceId; this.requestId=requestId; this.createdAt=OffsetDateTime.now();
    }
}
