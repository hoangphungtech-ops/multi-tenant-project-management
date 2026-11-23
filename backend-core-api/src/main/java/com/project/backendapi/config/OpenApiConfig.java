package com.project.backendapi.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI backendOpenAPI() {
        return new OpenAPI().info(new Info()
            .title("Backend Core API")
            .version("v1")
            .description("Multi-tenant Backend API – Member 2"));
    }
}
