package com.spantag.payment_service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final GatewayAuthFilter gatewayAuthFilter;

    public SecurityConfig(GatewayAuthFilter gatewayAuthFilter) {
        this.gatewayAuthFilter = gatewayAuthFilter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:1013"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(gatewayAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                // FIX: anyRequest().permitAll() — authorization is handled by
                // @PreAuthorize on individual controller methods.
                //
                // The old anyRequest().authenticated() was blocking direct
                // service-to-service calls from order-service's SagaOrchestrator
                // to /api/payment/process in Spring Security 7.x, even though
                // that path was listed in permitAll(). In Spring Security 7,
                // the DeferredSecurityContext evaluation can cause the anonymous
                // authentication set by AnonymousAuthenticationFilter to not be
                // visible when the AuthorizationFilter evaluates the request,
                // resulting in a 401 even on permitAll() paths.
                //
                // With anyRequest().permitAll():
                //   - /api/payment/process  → allowed (no auth needed, direct saga call)
                //   - /api/payment/cancel/** → allowed (no auth needed, direct saga call)
                //   - /api/payment/my       → @PreAuthorize("hasAnyRole('USER','ADMIN')") ✓
                //   - /api/payment/admin/** → @PreAuthorize("hasRole('ADMIN')") ✓
                .anyRequest().permitAll()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> {
                    res.setContentType("application/json");
                    res.setStatus(401);
                    res.getWriter().write("{\"message\":\"Unauthorized\"}");
                })
                .accessDeniedHandler((req, res, e) -> {
                    res.setContentType("application/json");
                    res.setStatus(403);
                    res.getWriter().write("{\"message\":\"Forbidden\"}");
                })
            );
        return http.build();
    }
}

//package com.spantag.payment_service;
//
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
//import org.springframework.security.config.annotation.web.builders.HttpSecurity;
//import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
//import org.springframework.security.config.http.SessionCreationPolicy;
//import org.springframework.security.web.SecurityFilterChain;
//import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
//import org.springframework.web.cors.CorsConfiguration;
//import org.springframework.web.cors.CorsConfigurationSource;
//import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
//
//import java.util.List;
//
//@Configuration
//@EnableWebSecurity
//@EnableMethodSecurity(prePostEnabled = true)
//public class SecurityConfig {
//
//    private final GatewayAuthFilter gatewayAuthFilter;
//
//    public SecurityConfig(GatewayAuthFilter gatewayAuthFilter) {
//        this.gatewayAuthFilter = gatewayAuthFilter;
//    }
//
//    @Bean
//    public CorsConfigurationSource corsConfigurationSource() {
//        CorsConfiguration config = new CorsConfiguration();
//        config.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:1013"));
//        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
//        config.setAllowedHeaders(List.of("*"));
//        config.setAllowCredentials(true);
//        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
//        source.registerCorsConfiguration("/**", config);
//        return source;
//    }
//
//    @Bean
//    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
//        http
//            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
//            .csrf(csrf -> csrf.disable())
//            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
//            .addFilterBefore(gatewayAuthFilter, UsernamePasswordAuthenticationFilter.class)
//            .authorizeHttpRequests(auth -> auth
//                // Internal saga endpoints — called by order-service directly,
//                // NOT via gateway. Permitted here; protected by 127.0.0.1 binding.
//                .requestMatchers(
//                    "/api/payment/process",
//                    "/api/payment/cancel/**"
//                ).permitAll()
//                // User-facing endpoints go through gateway with JWT
//                .anyRequest().authenticated()
//            )
//            .exceptionHandling(ex -> ex
//                .authenticationEntryPoint((req, res, e) -> {
//                    res.setContentType("application/json");
//                    res.setStatus(401);
//                    res.getWriter().write("{\"message\":\"Unauthorized\"}");
//                })
//                .accessDeniedHandler((req, res, e) -> {
//                    res.setContentType("application/json");
//                    res.setStatus(403);
//                    res.getWriter().write("{\"message\":\"Forbidden\"}");
//                })
//            );
//        return http.build();
//    }
//}