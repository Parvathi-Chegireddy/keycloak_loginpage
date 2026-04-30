package com.spantag.Apigatewayapplication;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class KeycloakJwtFilter extends AbstractGatewayFilterFactory<KeycloakJwtFilter.Config> {

    private final ReactiveJwtDecoder jwtDecoder;

    public KeycloakJwtFilter(ReactiveJwtDecoder jwtDecoder) {
        super(Config.class);
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String path = exchange.getRequest().getPath().toString();
            String authHeader = exchange.getRequest()
                    .getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                System.err.printf(
                    "[KeycloakJwtFilter] %s → REJECT: no Authorization: Bearer header%n", path);
                return reject(exchange.getResponse(),
                        "Unauthenticated — no Bearer token provided");
            }

            String tokenValue = authHeader.substring(7);

            return jwtDecoder.decode(tokenValue)
                    .flatMap(jwt -> {
                        String username = extractUsername(jwt);
                        String role     = extractRole(jwt);
                        String email    = nvl(jwt.getClaimAsString("email"));

                        System.out.printf(
                            "[KeycloakJwtFilter] %s → user=%s role=%s email=%s issuer=%s%n",
                            path, username, role, email, jwt.getIssuer()
                        );

                        
                        ServerHttpRequest mutated = exchange.getRequest().mutate()
                                .headers(h -> {
                                    h.remove("X-Auth-Username");
                                    h.remove("X-Auth-Role");
                                    h.remove("X-Auth-Email");
                                    h.remove("X-Auth-Provider");
                                    h.set("X-Auth-Username", nvl(username));
                                    h.set("X-Auth-Role",     nvl(role));
                                    h.set("X-Auth-Email",    email);
                                    h.set("X-Auth-Provider", "keycloak");
                                })
                                .build();

                        return chain.filter(exchange.mutate().request(mutated).build());
                    })
                    .onErrorResume(e -> {
                        System.err.printf(
                            "[KeycloakJwtFilter] %s → REJECT: JWT error: %s%n",
                            path, e.getMessage());
                        return reject(exchange.getResponse(),
                                "Unauthenticated — invalid or expired token");
                    });
        };
    }

    private String extractUsername(Jwt jwt) {
        String u = jwt.getClaimAsString("preferred_username");
        return (u != null && !u.isBlank()) ? u : jwt.getSubject();
    }

    @SuppressWarnings("unchecked")
    private String extractRole(Jwt jwt) {
        try {
            var realmAccess = jwt.getClaim("realm_access");
            if (realmAccess instanceof java.util.Map<?,?> map) {
                Object roles = map.get("roles");
                if (roles instanceof List<?> list) {
                    if (list.contains("admin") || list.contains("ROLE_ADMIN")) return "ROLE_ADMIN";
                    if (list.contains("user")  || list.contains("ROLE_USER"))  return "ROLE_USER";
                }
            }
            var topRoles = jwt.getClaim("roles");
            if (topRoles instanceof List<?> list) {
                if (list.contains("admin") || list.contains("ROLE_ADMIN")) return "ROLE_ADMIN";
                if (list.contains("user")  || list.contains("ROLE_USER"))  return "ROLE_USER";
            }
        } catch (Exception e) {
            System.err.println("[KeycloakJwtFilter] role extraction error: " + e.getMessage());
        }
        return "ROLE_USER";
    }

    private String nvl(String v) { return v != null ? v : ""; }

    private Mono<Void> reject(ServerHttpResponse response, String message) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] b = ("{\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        DataBuffer buf = response.bufferFactory().wrap(b);
        return response.writeWith(Mono.just(buf));
    }

    public static class Config {}
}

