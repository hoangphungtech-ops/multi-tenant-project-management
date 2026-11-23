package com.project.backendapi.security;

import com.project.backendapi.filter.CorrelationFilter;
import com.project.backendapi.filter.DemoAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
    @Value("${app.security.demo-mode:true}") private boolean demoMode;
    @Value("${app.security.audience:}") private String audience;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                             CorrelationFilter correlationFilter,
                                             DemoAuthenticationFilter demoFilter) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**",
                                 "/actuator/health", "/error").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(correlationFilter, UsernamePasswordAuthenticationFilter.class);

        if (demoMode) {
            http.addFilterBefore(demoFilter, UsernamePasswordAuthenticationFilter.class);
        } else {
            http.oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));
        }
        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer) {
        NimbusJwtDecoder decoder = JwtDecoders.fromIssuerLocation(issuer);
        if (audience != null && !audience.isBlank()) {
            OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuer);
            OAuth2TokenValidator<Jwt> audienceValidator = jwt ->
                jwt.getAudience().contains(audience)
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid audience", null));
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator));
        }
        return decoder;
    }
}
