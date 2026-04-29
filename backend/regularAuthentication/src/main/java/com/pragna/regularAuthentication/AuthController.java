package com.pragna.regularAuthentication;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final KeycloakAdminService keycloakAdminService;

    public AuthController(UserService userService,
                          AuthenticationManager authenticationManager,
                          KeycloakAdminService keycloakAdminService) {
        this.userService           = userService;
        this.authenticationManager = authenticationManager;
        this.keycloakAdminService  = keycloakAdminService;
    }

    // ── POST /api/auth/register ───────────────────────────────────────────
    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@RequestBody RegisterRequest req) {
        try {
            User user = new User();
            user.setUsername(req.getUsername());
            user.setPassword(req.getPassword());
            user.setEmail(req.getEmail());
            String role = (req.getRole() != null && !req.getRole().isBlank())
                    ? req.getRole() : "ROLE_USER";

            userService.registerUser(user, role);
            try {
                keycloakAdminService.createKeycloakUser(
                        req.getUsername(), req.getPassword(), req.getEmail(), role);
            } catch (Exception e) {
                System.err.printf("[AUTH] Keycloak registration failed: %s%n", e.getMessage());
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message",  "User registered successfully",
                "username", req.getUsername(),
                "role",     role
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    // ── POST /api/auth/login ──────────────────────────────────────────────
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody LoginRequest req, HttpServletResponse httpResponse) {
        try {
            Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword())
            );
            boolean isAdmin = auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            String dbRole = isAdmin ? "ROLE_ADMIN" : "ROLE_USER";

            if (!keycloakAdminService.userExistsInKeycloak(req.getUsername())) {
                User dbUser = userService.findByUsername(req.getUsername());
                String email = dbUser != null ? dbUser.getEmail() : "";
                keycloakAdminService.createKeycloakUser(
                        req.getUsername(), req.getPassword(), email, dbRole);
            }

            keycloakAdminService.syncUserRole(req.getUsername(), dbRole);

            Map<String, Object> tokens = keycloakAdminService
                    .obtainToken(req.getUsername(), req.getPassword());

            if (tokens.containsKey("error")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Keycloak authentication failed"));
            }

            setRefreshTokenCookie(httpResponse, (String) tokens.get("refresh_token"));

            Map<String, Object> response = new HashMap<>();
            response.put("message",     "Login successful");
            response.put("accessToken", tokens.get("access_token"));
            response.put("username",    req.getUsername());
            response.put("role",        dbRole);
            response.put("expiresIn",   tokens.get("expires_in"));
            response.put("loginMethod", "keycloak");
            response.put("provider",    "keycloak");
            return ResponseEntity.ok(response);

        } catch (BadCredentialsException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid username or password"));
        } catch (Exception ex) {
            System.err.println("[AUTH] Login error: " + ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Login failed: " + ex.getMessage()));
        }
    }

    // ── POST /api/auth/logout ─────────────────────────────────────────────
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletResponse httpResponse) {
        clearRefreshTokenCookie(httpResponse);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    // ── POST /api/auth/refresh ────────────────────────────────────────────
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(
            HttpServletRequest request, HttpServletResponse httpResponse) {
        String refreshToken = extractRefreshCookie(request);
        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "No refresh token"));
        }
        try {
            Map<String, Object> tokens = keycloakAdminService.refreshToken(refreshToken);
            setRefreshTokenCookie(httpResponse, (String) tokens.get("refresh_token"));
            Map<String, Object> response = new HashMap<>();
            response.put("accessToken", tokens.get("access_token"));
            response.put("expiresIn",   tokens.get("expires_in"));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            clearRefreshTokenCookie(httpResponse);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Refresh failed: " + e.getMessage()));
        }
    }

    // ── POST /api/auth/oauth2/save-user ───────────────────────────────────
    @PostMapping("/oauth2/save-user")
    public ResponseEntity<Map<String, Object>> saveOAuth2User(
            @RequestBody Map<String, String> req) {
        try {
            String username    = req.getOrDefault("username",    "");
            String email       = req.getOrDefault("email",       "");
            String displayName = req.getOrDefault("displayName", username);
            String avatar      = req.getOrDefault("avatar",      "");
            String provider    = req.getOrDefault("provider",    "oauth2");
            final String role  = "ROLE_USER";

            if (userService.findByUsername(username) == null) {
                User oauthUser = new User();
                oauthUser.setUsername(username);
                oauthUser.setPassword(UUID.randomUUID().toString());
                oauthUser.setEmail(email);
                oauthUser.setDisplayName(displayName);
                oauthUser.setAvatarUrl(avatar);
                oauthUser.setProvider(provider);
                userService.registerUser(oauthUser, role);
            }

            // Ensure user exists in Keycloak (will be needed for token issuance)
            if (!keycloakAdminService.userExistsInKeycloak(username)) {
                keycloakAdminService.createKeycloakUser(
                        username, UUID.randomUUID().toString(), email, role);
            }

            Map<String, Object> resp = new HashMap<>();
            resp.put("message",     "OAuth2 user saved");
            resp.put("username",    username);
            resp.put("displayName", displayName);
            resp.put("provider",    provider);
            resp.put("role",        role);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("message", "OAuth2 save failed: " + e.getMessage()));
        }
    }

    // ── POST /api/auth/oauth2/token ───────────────────────────────────────
    // Issues a real Keycloak JWT for an OAuth2 user (Google/GitHub).
    //
    // APPROACH: Temporary password technique (works on ALL Keycloak versions):
    //   1. Generate a UUID temp password
    //   2. Set it on the Keycloak user via Admin API (resetPassword)
    //   3. Call obtainToken(username, tempPassword) → real Keycloak JWT
    //   4. Immediately rotate to another UUID password → temp password invalid
    //   5. Return accessToken + set kc_refresh_token HttpOnly cookie
    //
    // This avoids the Keycloak 26 token exchange incompatibility
    // ("requested_subject not supported in standard token exchange").
    @PostMapping("/oauth2/token")
    public ResponseEntity<Map<String, Object>> issueOAuth2Token(
            @RequestBody Map<String, String> req,
            HttpServletResponse httpResponse) {

        String username = req.get("username");
        if (username == null || username.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "username is required"));
        }

        System.out.printf("[AUTH] OAuth2 token issuance for user=%s%n", username);

        try {
            // Step 1: Generate a UUID temp password
            String tempPassword = UUID.randomUUID().toString();

            // Step 2: Set temp password on Keycloak user via admin API
            keycloakAdminService.setUserTempPassword(username, tempPassword);
            System.out.printf("[AUTH] Temp password set for OAuth2 user=%s%n", username);

            // Step 3: Get real Keycloak JWT using temp password
            Map<String, Object> tokens = keycloakAdminService
                    .obtainToken(username, tempPassword);

            if (tokens.containsKey("error")) {
                return ResponseEntity.status(500)
                        .body(Map.of("message", "Failed to obtain token for OAuth2 user"));
            }

            // Step 4: Rotate password immediately so temp password can't be reused
            String newRandomPassword = UUID.randomUUID().toString();
            keycloakAdminService.setUserTempPassword(username, newRandomPassword);
            System.out.printf("[AUTH] ✓ Password rotated for OAuth2 user=%s%n", username);

            // Step 5: Set refresh token as HttpOnly cookie — same as regular login
            String accessToken  = (String) tokens.get("access_token");
            String refreshToken = (String) tokens.get("refresh_token");
            setRefreshTokenCookie(httpResponse, refreshToken);

            System.out.printf("[AUTH] ✓ OAuth2 token issued for user=%s%n", username);

            Map<String, Object> response = new HashMap<>();
            response.put("accessToken", accessToken);
            response.put("username",    username);
            response.put("expiresIn",   tokens.get("expires_in"));
            response.put("role",        "ROLE_USER");
            response.put("loginMethod", "oauth2");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.printf("[AUTH] OAuth2 token error for user=%s: %s%n",
                    username, e.getMessage());
            return ResponseEntity.status(500)
                    .body(Map.of("message", "OAuth2 token error: " + e.getMessage()));
        }
    }

    // ── Cookie helpers ────────────────────────────────────────────────────
    private void setRefreshTokenCookie(HttpServletResponse response, String token) {
        if (token == null) return;
        response.addHeader("Set-Cookie",
                "kc_refresh_token=" + token
                + "; Path=/"
                + "; HttpOnly"
                + "; Max-Age=604800"
                + "; SameSite=Lax");
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        response.addHeader("Set-Cookie",
                "kc_refresh_token=; Path=/; HttpOnly; Max-Age=0; SameSite=Lax");
    }

    private String extractRefreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (var cookie : request.getCookies()) {
            if ("kc_refresh_token".equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }
}

//package com.pragna.regularAuthentication;
//
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpServletResponse;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.MediaType;
//import org.springframework.http.ResponseEntity;
//import org.springframework.security.authentication.AuthenticationManager;
//import org.springframework.security.authentication.BadCredentialsException;
//import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
//import org.springframework.security.core.Authentication;
//import org.springframework.web.bind.annotation.*;
//import org.springframework.web.reactive.function.client.WebClient;
//
//import java.util.HashMap;
//import java.util.Map;
//
//@RestController
//@RequestMapping("/api/auth")
//public class AuthController {
//
//    private final UserService userService;
//    private final AuthenticationManager authenticationManager;
//    private final KeycloakAdminService keycloakAdminService;
//
//    @org.springframework.beans.factory.annotation.Value("${keycloak.auth-server-url}")
//    private String authServerUrl;
//
//    @org.springframework.beans.factory.annotation.Value("${keycloak.realm}")
//    private String realm;
//
//    @org.springframework.beans.factory.annotation.Value("${keycloak.admin.client-id}")
//    private String adminClientId;
//
//    @org.springframework.beans.factory.annotation.Value("${keycloak.admin.client-secret}")
//    private String adminClientSecret;
//
//    public AuthController(UserService userService,
//                          AuthenticationManager authenticationManager,
//                          KeycloakAdminService keycloakAdminService) {
//        this.userService           = userService;
//        this.authenticationManager = authenticationManager;
//        this.keycloakAdminService  = keycloakAdminService;
//    }
//
//    // ── POST /api/auth/register ───────────────────────────────────────────
//    @PostMapping("/register")
//    public ResponseEntity<Map<String, String>> register(@RequestBody RegisterRequest req) {
//        try {
//            User user = new User();
//            user.setUsername(req.getUsername());
//            user.setPassword(req.getPassword());
//            user.setEmail(req.getEmail());
//            String role = (req.getRole() != null && !req.getRole().isBlank())
//                    ? req.getRole() : "ROLE_USER";
//
//            userService.registerUser(user, role);
//            try {
//                keycloakAdminService.createKeycloakUser(
//                        req.getUsername(), req.getPassword(), req.getEmail(), role);
//            } catch (Exception e) {
//                System.err.printf("[AUTH] Keycloak registration failed: %s%n", e.getMessage());
//            }
//            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
//                "message",  "User registered successfully",
//                "username", req.getUsername(),
//                "role",     role
//            ));
//        } catch (RuntimeException e) {
//            return ResponseEntity.status(HttpStatus.CONFLICT)
//                    .body(Map.of("message", e.getMessage()));
//        }
//    }
//
//    // ── POST /api/auth/login ──────────────────────────────────────────────
//    @PostMapping("/login")
//    public ResponseEntity<Map<String, Object>> login(
//            @RequestBody LoginRequest req, HttpServletResponse httpResponse) {
//        try {
//            Authentication auth = authenticationManager.authenticate(
//                new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword())
//            );
//            boolean isAdmin = auth.getAuthorities().stream()
//                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
//            String dbRole = isAdmin ? "ROLE_ADMIN" : "ROLE_USER";
//
//            if (!keycloakAdminService.userExistsInKeycloak(req.getUsername())) {
//                User dbUser = userService.findByUsername(req.getUsername());
//                String email = dbUser != null ? dbUser.getEmail() : "";
//                keycloakAdminService.createKeycloakUser(
//                        req.getUsername(), req.getPassword(), email, dbRole);
//            }
//
//            // Sync DB role → Keycloak role before issuing token
//            keycloakAdminService.syncUserRole(req.getUsername(), dbRole);
//
//            Map<String, Object> tokens = keycloakAdminService
//                    .obtainToken(req.getUsername(), req.getPassword());
//
//            if (tokens.containsKey("error")) {
//                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
//                        .body(Map.of("message", "Keycloak authentication failed"));
//            }
//
//            setRefreshTokenCookie(httpResponse, (String) tokens.get("refresh_token"));
//
//            Map<String, Object> response = new HashMap<>();
//            response.put("message",     "Login successful");
//            response.put("accessToken", tokens.get("access_token"));
//            response.put("username",    req.getUsername());
//            response.put("role",        dbRole);
//            response.put("expiresIn",   tokens.get("expires_in"));
//            response.put("loginMethod", "keycloak");
//            response.put("provider",    "keycloak");
//            return ResponseEntity.ok(response);
//
//        } catch (BadCredentialsException ex) {
//            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
//                    .body(Map.of("message", "Invalid username or password"));
//        } catch (Exception ex) {
//            System.err.println("[AUTH] Login error: " + ex.getMessage());
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                    .body(Map.of("message", "Login failed: " + ex.getMessage()));
//        }
//    }
//
//    // ── POST /api/auth/logout ─────────────────────────────────────────────
//    @PostMapping("/logout")
//    public ResponseEntity<Map<String, String>> logout(HttpServletResponse httpResponse) {
//        clearRefreshTokenCookie(httpResponse);
//        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
//    }
//
//    // ── POST /api/auth/refresh ────────────────────────────────────────────
//    @PostMapping("/refresh")
//    public ResponseEntity<Map<String, Object>> refresh(
//            HttpServletRequest request, HttpServletResponse httpResponse) {
//        String refreshToken = extractRefreshCookie(request);
//        if (refreshToken == null) {
//            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
//                    .body(Map.of("message", "No refresh token"));
//        }
//        try {
//            Map<String, Object> tokens = keycloakAdminService.refreshToken(refreshToken);
//            setRefreshTokenCookie(httpResponse, (String) tokens.get("refresh_token"));
//            Map<String, Object> response = new HashMap<>();
//            response.put("accessToken", tokens.get("access_token"));
//            response.put("expiresIn",   tokens.get("expires_in"));
//            return ResponseEntity.ok(response);
//        } catch (Exception e) {
//            clearRefreshTokenCookie(httpResponse);
//            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
//                    .body(Map.of("message", "Refresh failed: " + e.getMessage()));
//        }
//    }
//
//    // ── POST /api/auth/oauth2/save-user ───────────────────────────────────
//    @PostMapping("/oauth2/save-user")
//    public ResponseEntity<Map<String, Object>> saveOAuth2User(
//            @RequestBody Map<String, String> req) {
//        try {
//            String username    = req.getOrDefault("username",    "");
//            String email       = req.getOrDefault("email",       "");
//            String displayName = req.getOrDefault("displayName", username);
//            String avatar      = req.getOrDefault("avatar",      "");
//            String provider    = req.getOrDefault("provider",    "oauth2");
//            final String role  = "ROLE_USER";  // always ROLE_USER for OAuth2
//
//            if (userService.findByUsername(username) == null) {
//                User oauthUser = new User();
//                oauthUser.setUsername(username);
//                oauthUser.setPassword(java.util.UUID.randomUUID().toString());
//                oauthUser.setEmail(email);
//                oauthUser.setDisplayName(displayName);
//                oauthUser.setAvatarUrl(avatar);
//                oauthUser.setProvider(provider);
//                userService.registerUser(oauthUser, role);
//            }
//
//            // Ensure user exists in Keycloak
//            if (!keycloakAdminService.userExistsInKeycloak(username)) {
//                keycloakAdminService.createKeycloakUser(
//                        username, java.util.UUID.randomUUID().toString(), email, role);
//            }
//
//            Map<String, Object> resp = new HashMap<>();
//            resp.put("message",     "OAuth2 user saved");
//            resp.put("username",    username);
//            resp.put("displayName", displayName);
//            resp.put("provider",    provider);
//            resp.put("role",        role);
//            return ResponseEntity.ok(resp);
//        } catch (Exception e) {
//            return ResponseEntity.status(500)
//                    .body(Map.of("message", "OAuth2 save failed: " + e.getMessage()));
//        }
//    }
//
//    // ── POST /api/auth/oauth2/token ───────────────────────────────────────
//    // Called by oauth2-service after successful Google/GitHub login.
//    // Uses Keycloak token exchange (admin impersonation) to issue a real
//    // Keycloak JWT for the OAuth2 user without needing their password.
//    // Sets kc_refresh_token HttpOnly cookie on the response.
//    @PostMapping("/oauth2/token")
//    public ResponseEntity<Map<String, Object>> issueOAuth2Token(
//            @RequestBody Map<String, String> req,
//            HttpServletResponse httpResponse) {
//
//        String username = req.get("username");
//        if (username == null || username.isBlank()) {
//            return ResponseEntity.badRequest()
//                    .body(Map.of("message", "username is required"));
//        }
//
//        System.out.printf("[AUTH] OAuth2 token exchange for user=%s%n", username);
//
//        try {
//            // Step 1: Get admin access token using client_credentials
//            String tokenUrl = authServerUrl + "/realms/" + realm
//                    + "/protocol/openid-connect/token";
//
//            @SuppressWarnings("unchecked")
//            Map<String, Object> adminTokenResult = WebClient.create().post()
//                    .uri(tokenUrl)
//                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
//                    .bodyValue("grant_type=client_credentials"
//                            + "&client_id=" + encode(adminClientId)
//                            + "&client_secret=" + encode(adminClientSecret))
//                    .retrieve()
//                    .bodyToMono(Map.class)
//                    .block();
//
//            if (adminTokenResult == null || !adminTokenResult.containsKey("access_token")) {
//                System.err.println("[AUTH] Failed to get admin token for OAuth2 exchange");
//                return ResponseEntity.status(500)
//                        .body(Map.of("message", "Admin token exchange failed"));
//            }
//
//            String adminToken = (String) adminTokenResult.get("access_token");
//            System.out.println("[AUTH] Admin token obtained for OAuth2 exchange");
//
//            // Step 2: Use admin token + token exchange to get user token
//            // This is Keycloak's Token Exchange feature:
//            // subject_token = admin token
//            // requested_subject = target username
//            // Result = a real Keycloak JWT as if the user logged in
//            @SuppressWarnings("unchecked")
//            Map<String, Object> userTokenResult = WebClient.create().post()
//                    .uri(tokenUrl)
//                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
//                    .bodyValue("grant_type=urn:ietf:params:oauth:grant-type:token-exchange"
//                            + "&client_id=" + encode(adminClientId)
//                            + "&client_secret=" + encode(adminClientSecret)
//                            + "&subject_token=" + encode(adminToken)
//                            + "&subject_token_type=urn:ietf:params:oauth:token-type:access_token"
//                            + "&requested_token_type=urn:ietf:params:oauth:token-type:refresh_token"
//                            + "&requested_subject=" + encode(username))
//                    .retrieve()
//                    .onStatus(
//                        s -> s.is4xxClientError() || s.is5xxServerError(),
//                        cr -> cr.bodyToMono(String.class).map(err -> {
//                            System.err.println("[AUTH] Token exchange error: " + err);
//                            return new RuntimeException("Token exchange failed: " + err);
//                        })
//                    )
//                    .bodyToMono(Map.class)
//                    .block();
//
//            if (userTokenResult == null || !userTokenResult.containsKey("access_token")) {
//                System.err.println("[AUTH] Token exchange returned no access_token");
//                return ResponseEntity.status(500)
//                        .body(Map.of("message", "Token exchange returned empty result"));
//            }
//
//            String accessToken  = (String) userTokenResult.get("access_token");
//            String refreshToken = (String) userTokenResult.get("refresh_token");
//
//            System.out.printf("[AUTH] ✓ OAuth2 token exchange SUCCESS for user=%s%n", username);
//
//            // Set refresh token as HttpOnly cookie — same as regular login
//            setRefreshTokenCookie(httpResponse, refreshToken);
//
//            Map<String, Object> response = new HashMap<>();
//            response.put("accessToken", accessToken);
//            response.put("username",    username);
//            response.put("expiresIn",   userTokenResult.get("expires_in"));
//            response.put("role",        "ROLE_USER");
//            response.put("loginMethod", "oauth2");
//            return ResponseEntity.ok(response);
//
//        } catch (Exception e) {
//            System.err.println("[AUTH] OAuth2 token exchange error: " + e.getMessage());
//            return ResponseEntity.status(500)
//                    .body(Map.of("message", "OAuth2 token exchange error: " + e.getMessage()));
//        }
//    }
//
//    // ── Cookie helpers ────────────────────────────────────────────────────
//    private void setRefreshTokenCookie(HttpServletResponse response, String token) {
//        if (token == null) return;
//        response.addHeader("Set-Cookie",
//                "kc_refresh_token=" + token
//                + "; Path=/"
//                + "; HttpOnly"
//                + "; Max-Age=604800"
//                + "; SameSite=Lax");
//    }
//
//    private void clearRefreshTokenCookie(HttpServletResponse response) {
//        response.addHeader("Set-Cookie",
//                "kc_refresh_token=; Path=/; HttpOnly; Max-Age=0; SameSite=Lax");
//    }
//
//    private String extractRefreshCookie(HttpServletRequest request) {
//        if (request.getCookies() == null) return null;
//        for (var cookie : request.getCookies()) {
//            if ("kc_refresh_token".equals(cookie.getName())) return cookie.getValue();
//        }
//        return null;
//    }
//
//    private String encode(String v) {
//        try {
//            return java.net.URLEncoder.encode(v != null ? v : "",
//                    java.nio.charset.StandardCharsets.UTF_8);
//        } catch (Exception e) { return v; }
//    }
//}
