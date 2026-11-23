package com.project.backendapi.tenant;

import com.project.backendapi.security.CurrentUser;
import jakarta.servlet.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import java.util.UUID;

@Component
public class TenantMembershipInterceptor implements HandlerInterceptor {
    private final TenantMembershipService memberships;
    private final CurrentUser currentUser;

    public TenantMembershipInterceptor(TenantMembershipService memberships, CurrentUser currentUser) {
        this.memberships = memberships; this.currentUser = currentUser;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/v1/tenants/")) return true;
        String[] parts = uri.split("/");
        if (parts.length < 5) return true;
        UUID tenantId = UUID.fromString(parts[4]);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) throw new org.springframework.security.access.AccessDeniedException("Unauthenticated");
        String userKey = currentUser.userKey(auth);
        // Membership lookup is repeated in service methods where transaction-scoped RLS is established.
        // This interceptor performs the requested role/membership gate for the tenant route.
        memberships.requireRole(tenantId, userKey, "OWNER", "MEMBER", "GUEST");
        TenantContext.set(tenantId);
        return true;
    }
}
