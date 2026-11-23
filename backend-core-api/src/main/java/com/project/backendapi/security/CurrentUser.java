package com.project.backendapi.security;

import com.project.backendapi.filter.DemoAuthenticationFilter.DemoPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {
    public String userKey(Authentication authentication) {
        if (authentication.getPrincipal() instanceof DemoPrincipal p) return p.userKey();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            String oid = jwt.getToken().getClaimAsString("oid");
            return oid != null ? oid : jwt.getToken().getSubject();
        }
        return authentication.getName();
    }
}
