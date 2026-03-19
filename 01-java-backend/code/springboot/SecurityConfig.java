
package com.faangprep.javabackend.springboot;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security configuration for a stateless REST API:
 *
 * - Stateless JWT auth using Spring OAuth2 Resource Server
 * - CORS restricted to known origins
 * - CSRF disabled (stateless API)
 * - Role-based access control
 * - Method-level security with @PreAuthorize
 *
 * application.yml:
 * spring:
 *   security:
 *     oauth2:
 *       resourceserver:
 *         jwt:
 *           jwk-set-uri: https://auth.example.com/.well-known/jwks.json
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // enables @PreAuthorize, @PostAuthorize
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CSRf not needed for stateless APIs — no cookies for auth
            .csrf(AbstractHttpConfigurer::disable)

            // Stateless: no HTTP sessions
            .sessionManagement(s ->
                s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // CORS — restrictive, explicit origins
            .cors(c -> c.configurationSource(corsConfigurationSource()))

            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/actuator/health",
                    "/actuator/info",
                    "/api/v1/auth/**"
                ).permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/internal/**").hasAuthority("SCOPE_internal")
                .anyRequest().authenticated()
            )

            // OAuth2 Resource Server with JWT
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            );

        return http.build();
    }

    // Maps JWT claims to Spring Security GrantedAuthorities
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authConverter = new JwtGrantedAuthoritiesConverter();
        // Our auth server puts roles in "roles" claim (not default "scope")
        authConverter.setAuthoritiesClaimName("roles");
        authConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authConverter);
        // Use "sub" claim as principal name
        converter.setPrincipalClaimName("sub");
        return converter;
    }

    // JwtDecoder — fetches JWKS from auth server, verifies RS256 signatures
    @Bean
    public JwtDecoder jwtDecoder() {
        // In production, use jwk-set-uri from properties (auto-configured by Spring Boot)
        // This is shown for explicitness:
        return NimbusJwtDecoder
            .withJwkSetUri("https://auth.example.com/.well-known/jwks.json")
            .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Cost factor 12 — ~200ms on modern hardware
        // High enough to resist brute force, low enough for user-facing auth
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Explicit allowed origins — never use "*" with credentials
        config.setAllowedOrigins(List.of(
            "https://app.example.com",
            "https://admin.example.com"
        ));

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        config.setAllowedHeaders(List.of(
            "Authorization",
            "Content-Type",
            "X-Requested-With",
            "X-Request-ID",
            "X-Idempotency-Key"
        ));

        config.setExposedHeaders(List.of(
            "X-Request-ID",
            "X-RateLimit-Limit",
            "X-RateLimit-Remaining",
            "X-RateLimit-Reset",
            "Retry-After"
        ));

        config.setAllowCredentials(true);
        config.setMaxAge(3600L); // preflight cache: 1 hour

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
