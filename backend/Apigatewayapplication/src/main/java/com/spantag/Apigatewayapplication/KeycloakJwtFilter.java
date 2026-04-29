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

/**
 * KeycloakJwtFilter — reads Authorization header directly, validates JWT,
 * injects X-Auth-* headers in a SINGLE mutate() call.
 *
 * FIX for POST 401: The previous version called exchange.mutate() twice:
 *   once to strip X-Auth headers, once to add new ones.
 *   Spring Cloud Gateway's ServerHttpRequest.mutate() wraps the request in a
 *   decorator each time. For POST requests with a body, the second wrap lost
 *   the reference to the body Flux, so the downstream order-service received
 *   a request with no body and (effectively) no authentication context.
 *
 * FIX: Combine both strip + inject into ONE mutate() call using the headers()
 *   Consumer lambda — atomically removes old headers and sets new ones.
 *   The body is never touched, so POST bodies flow through correctly.
 */
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

            // Read Authorization header from original request
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

                        // ── CRITICAL FIX: ONE mutate() call ──────────────────
                        // Strip spoofed headers AND inject trusted headers in a
                        // single headers() operation. This avoids double-wrapping
                        // the request, which caused POST body loss in WebFlux.
                        ServerHttpRequest mutated = exchange.getRequest().mutate()
                                .headers(h -> {
                                    // Remove any spoofed headers from client
                                    h.remove("X-Auth-Username");
                                    h.remove("X-Auth-Role");
                                    h.remove("X-Auth-Email");
                                    h.remove("X-Auth-Provider");
                                    // Inject trusted headers from validated JWT
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

//package com.spantag.Apigatewayapplication;
//
//import org.springframework.cloud.gateway.filter.GatewayFilter;
//import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
//import org.springframework.core.io.buffer.DataBuffer;
//import org.springframework.http.HttpHeaders;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.MediaType;
//import org.springframework.http.server.reactive.ServerHttpRequest;
//import org.springframework.http.server.reactive.ServerHttpResponse;
//import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
//import org.springframework.security.oauth2.jwt.Jwt;
//import org.springframework.stereotype.Component;
//import reactor.core.publisher.Mono;
//
//import java.nio.charset.StandardCharsets;
//import java.util.List;
//
//@Component
//public class KeycloakJwtFilter extends AbstractGatewayFilterFactory<KeycloakJwtFilter.Config> {
//
//    private final ReactiveJwtDecoder jwtDecoder;
//
//    public KeycloakJwtFilter(ReactiveJwtDecoder jwtDecoder) {
//        super(Config.class);
//        this.jwtDecoder = jwtDecoder;
//    }
//
//    @Override
//    public GatewayFilter apply(Config config) {
//        return (exchange, chain) -> {
//            String path = exchange.getRequest().getPath().toString();
//
//            // ── STEP 1: Strip any spoofed X-Auth-* headers from client ─────
//            ServerHttpRequest stripped = exchange.getRequest().mutate()
//                    .headers(h -> {
//                        h.remove("X-Auth-Username");
//                        h.remove("X-Auth-Role");
//                        h.remove("X-Auth-Email");
//                        h.remove("X-Auth-Provider");
//                    })
//                    .build();
//            var cleanExchange = exchange.mutate().request(stripped).build();
//
//            // ── STEP 2: Read Authorization header DIRECTLY ─────────────────
//            String authHeader = exchange.getRequest()
//                    .getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
//
//            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
//                System.err.printf(
//                    "[KeycloakJwtFilter] %s → REJECT: no Authorization: Bearer header%n", path);
//                return reject(cleanExchange.getResponse(),
//                        "Unauthenticated — no Bearer token provided");
//            }
//
//            String tokenValue = authHeader.substring(7);
//
//            // ── STEP 3: Decode and validate JWT via ReactiveJwtDecoder ──────
//            // This hits Keycloak JWKS to verify the signature.
//            // If the token is invalid/expired, jwtDecoder.decode() returns an error.
//            return jwtDecoder.decode(tokenValue)
//                    .flatMap(jwt -> {
//                        // ── STEP 4: Extract claims ───────────────────────────
//                        String username = extractUsername(jwt);
//                        String role     = extractRole(jwt);
//                        String email    = nvl(jwt.getClaimAsString("email"));
//
//                        System.out.printf(
//                            "[KeycloakJwtFilter] %s → user=%s role=%s email=%s issuer=%s%n",
//                            path, username, role, email, jwt.getIssuer()
//                        );
//
//                        // ── STEP 5: Inject trusted X-Auth-* headers ──────────
//                        ServerHttpRequest mutated = cleanExchange.getRequest().mutate()
//                                .header("X-Auth-Username", nvl(username))
//                                .header("X-Auth-Role",     nvl(role))
//                                .header("X-Auth-Email",    email)
//                                .header("X-Auth-Provider", "keycloak")
//                                .build();
//
//                        return chain.filter(cleanExchange.mutate().request(mutated).build());
//                    })
//                    .onErrorResume(e -> {
//                        // JWT invalid, expired, or signature verification failed
//                        System.err.printf(
//                            "[KeycloakJwtFilter] %s → REJECT: JWT decode failed: %s%n",
//                            path, e.getMessage());
//                        return reject(cleanExchange.getResponse(),
//                                "Unauthenticated — invalid or expired token");
//                    });
//        };
//    }
//
//    private String extractUsername(Jwt jwt) {
//        String u = jwt.getClaimAsString("preferred_username");
//        return (u != null && !u.isBlank()) ? u : jwt.getSubject();
//    }
//
//    @SuppressWarnings("unchecked")
//    private String extractRole(Jwt jwt) {
//        try {
//            var realmAccess = jwt.getClaim("realm_access");
//            if (realmAccess instanceof java.util.Map<?,?> map) {
//                Object roles = map.get("roles");
//                if (roles instanceof List<?> list) {
//                    if (list.contains("admin") || list.contains("ROLE_ADMIN")) return "ROLE_ADMIN";
//                    if (list.contains("user")  || list.contains("ROLE_USER"))  return "ROLE_USER";
//                }
//            }
//            var topRoles = jwt.getClaim("roles");
//            if (topRoles instanceof List<?> list) {
//                if (list.contains("admin") || list.contains("ROLE_ADMIN")) return "ROLE_ADMIN";
//                if (list.contains("user")  || list.contains("ROLE_USER"))  return "ROLE_USER";
//            }
//        } catch (Exception e) {
//            System.err.println("[KeycloakJwtFilter] role extraction error: " + e.getMessage());
//        }
//        return "ROLE_USER";
//    }
//
//    private String nvl(String v) { return v != null ? v : ""; }
//
//    private Mono<Void> reject(ServerHttpResponse response, String message) {
//        response.setStatusCode(HttpStatus.UNAUTHORIZED);
//        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
//        byte[] b = ("{\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
//        DataBuffer buf = response.bufferFactory().wrap(b);
//        return response.writeWith(Mono.just(buf));
//    }
//
//    public static class Config {}
//}


//package com.spantag.Apigatewayapplication;
//
//import org.springframework.cloud.gateway.filter.GatewayFilter;
//import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
//import org.springframework.core.io.buffer.DataBuffer;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.MediaType;
//import org.springframework.http.server.reactive.ServerHttpRequest;
//import org.springframework.http.server.reactive.ServerHttpResponse;
//import org.springframework.security.core.context.ReactiveSecurityContextHolder;
//import org.springframework.security.oauth2.jwt.Jwt;
//import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
//import org.springframework.stereotype.Component;
//import reactor.core.publisher.Mono;
//
//import java.nio.charset.StandardCharsets;
//import java.util.List;
//
///**
// * Keycloak JWT Gateway Filter
// *
// * Spring Security OAuth2 Resource Server already validated the Keycloak JWT
// * (via JWKS URI) before this filter runs.
// *
// * This filter:
// *   1. Strips any spoofed X-Auth-* headers from the client request
// *   2. Reads the validated JWT claims from the ReactiveSecurityContext
// *   3. Injects trusted X-Auth-Username, X-Auth-Role, X-Auth-Email headers
// *      so downstream services can trust them without re-validating the JWT
// *
// * FIX: Added detailed logging so you can see in the Gateway console exactly
// * what claims are being extracted and passed downstream. This makes it easy
// * to spot mismatches (e.g. wrong realm, missing roles claim).
// */
//@Component
//public class KeycloakJwtFilter extends AbstractGatewayFilterFactory<KeycloakJwtFilter.Config> {
//
//    public KeycloakJwtFilter() {
//        super(Config.class);
//    }
//
//    @Override
//    public GatewayFilter apply(Config config) {
//        return (exchange, chain) -> {
//            String path = exchange.getRequest().getPath().toString();
//
//            // ── STEP 1: Strip spoofed headers from client ─────────────
//            ServerHttpRequest stripped = exchange.getRequest().mutate()
//                    .headers(h -> {
//                        h.remove("X-Auth-Username");
//                        h.remove("X-Auth-Role");
//                        h.remove("X-Auth-Email");
//                        h.remove("X-Auth-Provider");
//                    })
//                    .build();
//
//            var strippedExchange = exchange.mutate().request(stripped).build();
//
//            // ── STEP 2: Read validated JWT from SecurityContext ───────
//            return ReactiveSecurityContextHolder.getContext()
//                    .flatMap(ctx -> {
//                        // FIX: More informative null-check error message
//                        if (ctx.getAuthentication() == null) {
//                            System.err.printf("[KeycloakJwtFilter] %s → REJECT: no authentication in SecurityContext%n", path);
//                            return reject(strippedExchange.getResponse(),
//                                    HttpStatus.UNAUTHORIZED,
//                                    "No authentication found — Bearer token missing or invalid");
//                        }
//
//                        if (!(ctx.getAuthentication() instanceof JwtAuthenticationToken jwtAuth)) {
//                            System.err.printf("[KeycloakJwtFilter] %s → REJECT: authentication is not JwtAuthenticationToken (got: %s)%n",
//                                    path, ctx.getAuthentication().getClass().getSimpleName());
//                            return reject(strippedExchange.getResponse(),
//                                    HttpStatus.UNAUTHORIZED,
//                                    "Invalid authentication type — expected Keycloak JWT");
//                        }
//
//                        Jwt jwt = jwtAuth.getToken();
//
//                        // ── STEP 3: Extract claims ─────────────────────
//                        String username = extractUsername(jwt);
//                        String role     = extractRole(jwt);
//                        String email    = jwt.getClaimAsString("email");
//                        String provider = "keycloak";
//
//                        // FIX: Log what we're injecting downstream so you can
//                        // verify the right claims are flowing through
//                        System.out.printf(
//                            "[KeycloakJwtFilter] %s → user=%s role=%s email=%s issuer=%s%n",
//                            path, username, role, email, jwt.getIssuer()
//                        );
//
//                        // ── STEP 4: Inject trusted headers ────────────
//                        ServerHttpRequest mutated = strippedExchange.getRequest().mutate()
//                                .header("X-Auth-Username", nvl(username))
//                                .header("X-Auth-Role",     nvl(role))
//                                .header("X-Auth-Email",    nvl(email))
//                                .header("X-Auth-Provider", provider)
//                                .build();
//
//                        return chain.filter(strippedExchange.mutate().request(mutated).build());
//                    })
//                    // FIX: switchIfEmpty fires when ReactiveSecurityContextHolder is empty
//                    // (i.e. Spring Security never populated it — means no Authorization header
//                    //  was present at all, or SecurityConfig let the request through unauthenticated)
//                    .switchIfEmpty(Mono.defer(() -> {
//                        System.err.printf(
//                            "[KeycloakJwtFilter] %s → REJECT: ReactiveSecurityContextHolder is empty " +
//                            "(no Authorization: Bearer header was sent by the client)%n", path
//                        );
//                        return reject(strippedExchange.getResponse(),
//                                HttpStatus.UNAUTHORIZED,
//                                "Unauthenticated — no Bearer token provided");
//                    }));
//        };
//    }
//
//    /**
//     * Extract username — prefers 'preferred_username' (standard Keycloak claim),
//     * falls back to 'sub'.
//     */
//    private String extractUsername(Jwt jwt) {
//        String preferred = jwt.getClaimAsString("preferred_username");
//        if (preferred != null && !preferred.isBlank()) return preferred;
//        return jwt.getSubject();
//    }
//
//    /**
//     * Extract role from Keycloak JWT.
//     *
//     * Keycloak embeds roles in:
//     *   realm_access.roles  → for realm-level roles
//     *   resource_access.<clientId>.roles → for client-level roles
//     *
//     * FIX: Also checks 'roles' top-level claim as fallback (some Keycloak
//     * configs add a roles mapper at the top level).
//     */
//    @SuppressWarnings("unchecked")
//    private String extractRole(Jwt jwt) {
//        try {
//            // Check realm_access.roles (standard Keycloak)
//            var realmAccess = jwt.getClaim("realm_access");
//            if (realmAccess instanceof java.util.Map<?, ?> realmMap) {
//                Object rolesObj = realmMap.get("roles");
//                if (rolesObj instanceof List<?> roles) {
//                    if (roles.contains("admin") || roles.contains("ROLE_ADMIN")) {
//                        return "ROLE_ADMIN";
//                    }
//                    if (roles.contains("user") || roles.contains("ROLE_USER")) {
//                        return "ROLE_USER";
//                    }
//                }
//            }
//
//            // Fallback: top-level 'roles' claim
//            var rolesTopLevel = jwt.getClaim("roles");
//            if (rolesTopLevel instanceof List<?> rolesList) {
//                if (rolesList.contains("admin") || rolesList.contains("ROLE_ADMIN")) {
//                    return "ROLE_ADMIN";
//                }
//                if (rolesList.contains("user") || rolesList.contains("ROLE_USER")) {
//                    return "ROLE_USER";
//                }
//            }
//
//            // Fallback: Spring Security 'scope' claim
//            String scope = jwt.getClaimAsString("scope");
//            if (scope != null && scope.contains("admin")) {
//                return "ROLE_ADMIN";
//            }
//
//        } catch (Exception e) {
//            System.err.println("[KeycloakJwtFilter] Could not extract role: " + e.getMessage());
//        }
//
//        return "ROLE_USER"; // safe default
//    }
//
//    private String nvl(String v) {
//        return v != null ? v : "";
//    }
//
//    private Mono<Void> reject(ServerHttpResponse response, HttpStatus status, String message) {
//        response.setStatusCode(status);
//        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
//        byte[] bytes = ("{\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
//        DataBuffer buffer = response.bufferFactory().wrap(bytes);
//        return response.writeWith(Mono.just(buffer));
//    }
//
//    public static class Config {}
//}