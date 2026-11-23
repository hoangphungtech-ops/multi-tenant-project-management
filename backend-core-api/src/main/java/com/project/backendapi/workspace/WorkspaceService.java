package com.project.backendapi.workspace;

import com.project.backendapi.audit.*;
import com.project.backendapi.tenant.TenantMembershipService;
import org.slf4j.MDC;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class WorkspaceService {
    private final WorkspaceRepository repo;
    private final AuditEventRepository audit;
    private final TenantMembershipService memberships;
    private final JdbcTemplate jdbc;

    public WorkspaceService(WorkspaceRepository repo, AuditEventRepository audit,
                            TenantMembershipService memberships, JdbcTemplate jdbc) {
        this.repo=repo; this.audit=audit; this.memberships=memberships; this.jdbc=jdbc;
    }

    private void setRlsTenant(UUID tenantId) {
        jdbc.queryForObject("select set_config('app.tenant_id', ?, true)", String.class, tenantId.toString());
    }

    @Transactional
    public WorkspaceResponse create(UUID tenantId, String userKey, WorkspaceRequest request) {
        String role = memberships.requireRole(tenantId, userKey, "OWNER", "MEMBER");
        setRlsTenant(tenantId);
        Workspace w = new Workspace(); w.setTenantId(tenantId); w.setName(request.name().trim());
        w.setDescription(request.description());
        Workspace saved = repo.save(w);
        audit.save(new AuditEvent(tenantId, "WORKSPACE_CREATED", "WORKSPACE", saved.getId().toString(), MDC.get("request_id")));
        return WorkspaceResponse.from(saved);
    }

    @Transactional(readOnly=true)
    public List<WorkspaceResponse> list(UUID tenantId, String userKey) {
        memberships.requireRole(tenantId, userKey, "OWNER", "MEMBER", "GUEST");
        setRlsTenant(tenantId);
        return repo.findAllByTenantIdOrderByCreatedAtDesc(tenantId).stream().map(WorkspaceResponse::from).toList();
    }

    @Transactional(readOnly=true)
    public WorkspaceResponse get(UUID tenantId, UUID id, String userKey) {
        memberships.requireRole(tenantId, userKey, "OWNER", "MEMBER", "GUEST");
        setRlsTenant(tenantId);
        return WorkspaceResponse.from(repo.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new NoSuchElementException("Workspace not found")));
    }

    @Transactional
    public WorkspaceResponse update(UUID tenantId, UUID id, String userKey, WorkspaceRequest request) {
        memberships.requireRole(tenantId, userKey, "OWNER", "MEMBER");
        setRlsTenant(tenantId);
        Workspace w = repo.findByIdAndTenantId(id, tenantId).orElseThrow(() -> new NoSuchElementException("Workspace not found"));
        w.setName(request.name().trim()); w.setDescription(request.description());
        Workspace saved = repo.save(w);
        audit.save(new AuditEvent(tenantId, "WORKSPACE_UPDATED", "WORKSPACE", id.toString(), MDC.get("request_id")));
        return WorkspaceResponse.from(saved);
    }

    @Transactional
    public void delete(UUID tenantId, UUID id, String userKey) {
        memberships.requireRole(tenantId, userKey, "OWNER");
        setRlsTenant(tenantId);
        Workspace w = repo.findByIdAndTenantId(id, tenantId).orElseThrow(() -> new NoSuchElementException("Workspace not found"));
        repo.delete(w);
        audit.save(new AuditEvent(tenantId, "WORKSPACE_DELETED", "WORKSPACE", id.toString(), MDC.get("request_id")));
    }
}
