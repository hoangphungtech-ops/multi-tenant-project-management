package com.project.backendapi.workspace;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name="workspaces", uniqueConstraints=@UniqueConstraint(name="uq_workspace_tenant_name", columnNames={"tenant_id","name"}))
public class Workspace {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tenant_id", nullable=false) private UUID tenantId;
    @Column(nullable=false, length=160) private String name;
    @Column(columnDefinition="text") private String description;
    @Column(name="created_by") private UUID createdBy;
    @Column(name="created_at", nullable=false) private OffsetDateTime createdAt;
    @Column(name="updated_at", nullable=false) private OffsetDateTime updatedAt;

    @PrePersist void onCreate() { var now=OffsetDateTime.now(); createdAt=now; updatedAt=now; }
    @PreUpdate void onUpdate() { updatedAt=OffsetDateTime.now(); }
    public UUID getId(){return id;} public UUID getTenantId(){return tenantId;}
    public void setTenantId(UUID v){tenantId=v;} public String getName(){return name;}
    public void setName(String v){name=v;} public String getDescription(){return description;}
    public void setDescription(String v){description=v;} public UUID getCreatedBy(){return createdBy;}
    public void setCreatedBy(UUID v){createdBy=v;} public OffsetDateTime getCreatedAt(){return createdAt;}
    public OffsetDateTime getUpdatedAt(){return updatedAt;}
}
