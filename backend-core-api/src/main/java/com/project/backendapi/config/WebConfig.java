package com.project.backendapi.config;

import com.project.backendapi.tenant.TenantMembershipInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final TenantMembershipInterceptor membershipInterceptor;
    private final String[] allowedOrigins;

    public WebConfig(TenantMembershipInterceptor membershipInterceptor,
                     @Value("${CORS_ALLOWED_ORIGINS:http://localhost:5173}") String corsOrigins) {
        this.membershipInterceptor = membershipInterceptor;
        this.allowedOrigins = corsOrigins.split(",");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(membershipInterceptor).addPathPatterns("/api/v1/tenants/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins(allowedOrigins)
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("Authorization", "Content-Type", "X-Request-Id", "X-Demo-User", "X-Demo-Tenant")
            .exposedHeaders("X-Request-Id");
    }
}
