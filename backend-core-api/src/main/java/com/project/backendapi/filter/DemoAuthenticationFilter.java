package com.project.backendapi.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
public class DemoAuthenticationFilter implements Filter {
    @Value("${app.security.demo-mode:true}") private boolean enabled;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        if (enabled && SecurityContextHolder.getContext().getAuthentication() == null) {
            String user = req.getHeader("X-Demo-User");
            String tenant = req.getHeader("X-Demo-Tenant");
            if (user != null && tenant != null) {
                try {
                    UUID.fromString(tenant);
                    var auth = new UsernamePasswordAuthenticationToken(
                        new DemoPrincipal(user, tenant), "N/A",
                        List.of(new SimpleGrantedAuthority("ROLE_USER")));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } catch (IllegalArgumentException ignored) {
                    // Invalid tenant header is not authenticated.
                }
            }
        }
        chain.doFilter(request, response);
    }

    public record DemoPrincipal(String userKey, String tenantId) {}
}
