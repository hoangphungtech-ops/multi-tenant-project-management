package com.project.backendapi.tenant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class TenantMembershipService {
    private final JdbcTemplate jdbc;
    public TenantMembershipService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public String requireRole(UUID tenantId, String userKey, String... allowedRoles) {
        // Set tenant scope transaction-locally before querying RLS-protected membership rows.
        jdbc.queryForObject("select set_config('app.tenant_id', ?, true)", String.class, tenantId.toString());
        var roles = jdbc.query(
            "select tm.role from tenant_memberships tm join users u on u.id=tm.user_id " +
            "where tm.tenant_id=? and u.entra_object_id=? and tm.status='ACTIVE'",
            (rs, row) -> rs.getString(1), tenantId, userKey);
        if (roles.isEmpty()) throw new org.springframework.security.access.AccessDeniedException("No active tenant membership");
        String role = roles.get(0);
        for (String allowed : allowedRoles) if (allowed.equals(role)) return role;
        throw new org.springframework.security.access.AccessDeniedException("Insufficient tenant role");
    }

    @Transactional(readOnly = true)
    public UUID requireMemberUserId(UUID tenantId, String userKey, String... allowedRoles) {
        jdbc.queryForObject("select set_config('app.tenant_id', ?, true)", String.class, tenantId.toString());
        record Row(UUID userId, String role) {}
        var rows = jdbc.query(
            "select u.id, tm.role from tenant_memberships tm join users u on u.id=tm.user_id " +
            "where tm.tenant_id=? and u.entra_object_id=? and tm.status='ACTIVE'",
            (rs, row) -> new Row(rs.getObject(1, UUID.class), rs.getString(2)), tenantId, userKey);
        if (rows.isEmpty()) throw new org.springframework.security.access.AccessDeniedException("No active tenant membership");
        Row match = rows.get(0);
        for (String allowed : allowedRoles) if (allowed.equals(match.role())) return match.userId();
        throw new org.springframework.security.access.AccessDeniedException("Insufficient tenant role");
    }
}
