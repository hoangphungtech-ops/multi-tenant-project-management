package com.project.backendapi.tenant;

import com.project.backendapi.filter.DemoAuthenticationFilter.DemoPrincipal;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.UUID;

@Component
public class TenantContextFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            UUID tenant = null;
            if (auth != null && auth.getPrincipal() instanceof DemoPrincipal p) {
                tenant = UUID.fromString(p.tenantId());
            } else if (auth instanceof JwtAuthenticationToken jwt) {
                String claim = jwt.getToken().getClaimAsString("tenant_id");
                if (claim != null) tenant = UUID.fromString(claim);
            }
            if (tenant != null) TenantContext.set(tenant);
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
