package com.pragna.regularAuthentication;

import jakarta.ws.rs.core.Response;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class KeycloakAdminService {

    @Value("${keycloak.auth-server-url}")
    private String authServerUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.admin.client-id}")
    private String adminClientId;

    @Value("${keycloak.admin.client-secret}")
    private String adminClientSecret;

    @Value("${keycloak.client-id}")
    private String clientId;

    private Keycloak getAdminKeycloak() {
        return KeycloakBuilder.builder()
                .serverUrl(authServerUrl)
                .realm(realm)
                .clientId(adminClientId)
                .clientSecret(adminClientSecret)
                .grantType("client_credentials")
                .build();
    }

    // ── Test admin connection ─────────────────────────────────────────────
    public boolean testAdminConnection() {
        try (Keycloak kc = getAdminKeycloak()) {
            int count = kc.realm(realm).users().count();
            System.out.printf("[KC-ADMIN] ✓ Connection OK — realm '%s' has %d users%n",
                    realm, count);
            return true;
        } catch (Exception e) {
            System.err.printf("[KC-ADMIN] ✗ Connection FAILED: %s%n", e.getMessage());
            return false;
        }
    }

    // ── Create user in Keycloak ───────────────────────────────────────────
    public String createKeycloakUser(String username, String password,
                                     String email, String roleName) {
        System.out.printf("[KC-ADMIN] Creating user: username=%s email=%s role=%s%n",
                username, email, roleName);
        try (Keycloak kc = getAdminKeycloak()) {
            RealmResource realmResource = kc.realm(realm);
            UsersResource usersResource = realmResource.users();

            UserRepresentation user = new UserRepresentation();
            user.setUsername(username);
            user.setEmail((email != null && !email.isBlank()) ? email : null);
            user.setEmailVerified(true);
            user.setEnabled(true);
            user.setRequiredActions(Collections.emptyList());

            try (Response response = usersResource.create(user)) {
                int status = response.getStatus();
                if (status == 201) {
                    String location = response.getHeaderString("Location");
                    String userId = location.substring(location.lastIndexOf("/") + 1);
                    setPasswordById(usersResource, userId, password);
                    assignRealmRole(realmResource, userId, roleName);
                    System.out.printf("[KC-ADMIN] ✓ User created: %s (id=%s)%n",
                            username, userId);
                    return userId;
                } else if (status == 409) {
                    List<UserRepresentation> existing = usersResource.search(username, true);
                    if (!existing.isEmpty()) {
                        String existingId = existing.get(0).getId();
                        setPasswordById(usersResource, existingId, password);
                        return existingId;
                    }
                    return "existing-user";
                } else {
                    String body = response.readEntity(String.class);
                    throw new RuntimeException("Failed HTTP " + status + ": " + body);
                }
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Keycloak create error: " + e.getMessage(), e);
        }
    }

    // ── Set temp password for OAuth2 users ───────────────────────────────
    // Used by the OAuth2 token issuance flow:
    //   1. Set UUID temp password
    //   2. Get token via password grant
    //   3. Rotate to new UUID (invalidate temp)
    public void setUserTempPassword(String username, String tempPassword) {
        try (Keycloak kc = getAdminKeycloak()) {
            List<UserRepresentation> users = kc.realm(realm).users()
                    .search(username, true);
            if (users.isEmpty()) {
                throw new RuntimeException("User not found in Keycloak: " + username);
            }
            String userId = users.get(0).getId();
            setPasswordById(kc.realm(realm).users(), userId, tempPassword);
            System.out.printf("[KC-ADMIN] Temp password set for user=%s%n", username);
        } catch (Exception e) {
            throw new RuntimeException("setUserTempPassword failed: " + e.getMessage(), e);
        }
    }

    // ── Sync DB role → Keycloak role ──────────────────────────────────────
    public void syncUserRole(String username, String dbRoleName) {
        try (Keycloak kc = getAdminKeycloak()) {
            RealmResource realmResource = kc.realm(realm);
            List<UserRepresentation> users = realmResource.users()
                    .search(username, true);
            if (users.isEmpty()) return;

            String userId = users.get(0).getId();
            UserResource userResource = realmResource.users().get(userId);

            List<String> currentRoleNames = userResource.roles()
                    .realmLevel().listEffective().stream()
                    .map(RoleRepresentation::getName)
                    .collect(Collectors.toList());

            String targetKcRole   = dbRoleName != null && dbRoleName.contains("ADMIN")
                    ? "admin" : "user";
            String oppositeKcRole = "admin".equals(targetKcRole) ? "user" : "admin";

            System.out.printf("[KC-SYNC] %s → DB=%s KC=%s target=%s%n",
                    username, dbRoleName, currentRoleNames, targetKcRole);

            if (currentRoleNames.contains(oppositeKcRole)) {
                try {
                    RoleRepresentation opp = realmResource.roles()
                            .get(oppositeKcRole).toRepresentation();
                    userResource.roles().realmLevel().remove(List.of(opp));
                    System.out.printf("[KC-SYNC] ✓ Removed '%s' from %s%n",
                            oppositeKcRole, username);
                } catch (Exception e) {
                    System.err.printf("[KC-SYNC] Remove role error: %s%n", e.getMessage());
                }
            }
            if (!currentRoleNames.contains(targetKcRole)) {
                try {
                    RoleRepresentation target = realmResource.roles()
                            .get(targetKcRole).toRepresentation();
                    userResource.roles().realmLevel().add(List.of(target));
                    System.out.printf("[KC-SYNC] ✓ Assigned '%s' to %s%n",
                            targetKcRole, username);
                } catch (Exception e) {
                    System.err.printf("[KC-SYNC] Assign role error: %s%n", e.getMessage());
                }
            } else {
                System.out.printf("[KC-SYNC] ✓ Role '%s' already correct for %s%n",
                        targetKcRole, username);
            }
        } catch (Exception e) {
            System.err.printf("[KC-SYNC] Sync failed for %s: %s%n",
                    username, e.getMessage());
        }
    }

    // ── Set password by userId ────────────────────────────────────────────
    private void setPasswordById(UsersResource usersResource, String userId,
                                  String password) {
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(false);
        usersResource.get(userId).resetPassword(credential);
    }

    // ── Assign realm role ─────────────────────────────────────────────────
    private void assignRealmRole(RealmResource realmResource, String userId,
                                  String roleName) {
        try {
            String kcRoleName = roleName != null && roleName.contains("ADMIN")
                    ? "admin" : "user";
            RoleRepresentation role = realmResource.roles()
                    .get(kcRoleName).toRepresentation();
            realmResource.users().get(userId).roles()
                    .realmLevel().add(List.of(role));
            System.out.printf("[KC-ADMIN] Role '%s' assigned to userId=%s%n",
                    kcRoleName, userId);
        } catch (Exception e) {
            System.err.println("[KC-ADMIN] Role assignment error: " + e.getMessage());
        }
    }

    // ── Obtain token via password grant ───────────────────────────────────
    public Map<String, Object> obtainToken(String username, String password) {
        String tokenUrl = authServerUrl + "/realms/" + realm
                + "/protocol/openid-connect/token";
        String body = "grant_type=password"
                + "&client_id=" + encode(clientId)
                + "&username="  + encode(username)
                + "&password="  + encode(password);
        System.out.printf("[KEYCLOAK] Requesting token for user=%s%n", username);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = WebClient.create().post()
                    .uri(tokenUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(
                        s -> s.is4xxClientError() || s.is5xxServerError(),
                        cr -> cr.bodyToMono(String.class).map(err -> {
                            System.err.println("[KEYCLOAK] Token error: " + err);
                            return new RuntimeException("Keycloak token error: " + err);
                        })
                    )
                    .bodyToMono(Map.class)
                    .block();
            if (result != null && result.containsKey("access_token")) {
                System.out.println("[KEYCLOAK] Token obtained successfully");
                return result;
            }
            return Map.of("error", "No access_token in response");
        } catch (Exception e) {
            throw new RuntimeException("Authentication failed: " + e.getMessage());
        }
    }

    // ── Refresh token ─────────────────────────────────────────────────────
    public Map<String, Object> refreshToken(String refreshToken) {
        String tokenUrl = authServerUrl + "/realms/" + realm
                + "/protocol/openid-connect/token";
        String body = "grant_type=refresh_token"
                + "&client_id="    + encode(clientId)
                + "&refresh_token=" + encode(refreshToken);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = WebClient.create().post()
                    .uri(tokenUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            return result != null ? result : Map.of("error", "No response");
        } catch (Exception e) {
            throw new RuntimeException("Token refresh failed: " + e.getMessage());
        }
    }

    // ── Check if user exists ──────────────────────────────────────────────
    public boolean userExistsInKeycloak(String username) {
        try (Keycloak kc = getAdminKeycloak()) {
            return !kc.realm(realm).users().search(username, true).isEmpty();
        } catch (Exception e) {
            System.err.println("[KC-ADMIN] userExists error: " + e.getMessage());
            return false;
        }
    }

    private String encode(String v) {
        return URLEncoder.encode(v != null ? v : "", StandardCharsets.UTF_8);
    }
}

//package com.pragna.regularAuthentication;
//
//import jakarta.ws.rs.core.Response;
//import org.keycloak.admin.client.Keycloak;
//import org.keycloak.admin.client.KeycloakBuilder;
//import org.keycloak.admin.client.resource.RealmResource;
//import org.keycloak.admin.client.resource.UserResource;
//import org.keycloak.admin.client.resource.UsersResource;
//import org.keycloak.representations.idm.CredentialRepresentation;
//import org.keycloak.representations.idm.RoleRepresentation;
//import org.keycloak.representations.idm.UserRepresentation;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.http.MediaType;
//import org.springframework.stereotype.Service;
//import org.springframework.web.reactive.function.client.WebClient;
//import org.springframework.web.reactive.function.client.WebClientResponseException;
//
//import java.net.URLEncoder;
//import java.nio.charset.StandardCharsets;
//import java.util.Collections;
//import java.util.List;
//import java.util.Map;
//import java.util.stream.Collectors;
//
//@Service
//public class KeycloakAdminService {
//
//    @Value("${keycloak.auth-server-url}")
//    private String authServerUrl;
//
//    @Value("${keycloak.realm}")
//    private String realm;
//
//    @Value("${keycloak.admin.client-id}")
//    private String adminClientId;
//
//    @Value("${keycloak.admin.client-secret}")
//    private String adminClientSecret;
//
//    @Value("${keycloak.client-id}")
//    private String clientId;
//
//    private Keycloak getAdminKeycloak() {
//        return KeycloakBuilder.builder()
//                .serverUrl(authServerUrl)
//                .realm(realm)
//                .clientId(adminClientId)
//                .clientSecret(adminClientSecret)
//                .grantType("client_credentials")
//                .build();
//    }
//
//    // ── Test admin connection ─────────────────────────────────────────────
//    public boolean testAdminConnection() {
//        try (Keycloak kc = getAdminKeycloak()) {
//            int count = kc.realm(realm).users().count();
//            System.out.printf("[KC-ADMIN] ✓ Connection OK — realm '%s' has %d users%n", realm, count);
//            return true;
//        } catch (Exception e) {
//            System.err.printf("[KC-ADMIN] ✗ Connection FAILED: %s%n", e.getMessage());
//            return false;
//        }
//    }
//
//    // ── Create user in Keycloak ───────────────────────────────────────────
//    public String createKeycloakUser(String username, String password,
//                                     String email, String roleName) {
//        System.out.printf("[KC-ADMIN] Creating user: username=%s email=%s role=%s%n",
//                username, email, roleName);
//        try (Keycloak kc = getAdminKeycloak()) {
//            RealmResource realmResource = kc.realm(realm);
//            UsersResource usersResource = realmResource.users();
//
//            UserRepresentation user = new UserRepresentation();
//            user.setUsername(username);
//            user.setEmail((email != null && !email.isBlank()) ? email : null);
//            user.setEmailVerified(true);
//            user.setEnabled(true);
//            user.setRequiredActions(Collections.emptyList());
//
//            try (Response response = usersResource.create(user)) {
//                int status = response.getStatus();
//                if (status == 201) {
//                    String location = response.getHeaderString("Location");
//                    String userId = location.substring(location.lastIndexOf("/") + 1);
//                    setPassword(usersResource, userId, password);
//                    assignRealmRole(realmResource, userId, roleName);
//                    System.out.printf("[KC-ADMIN] ✓ User created: %s (id=%s)%n", username, userId);
//                    return userId;
//                } else if (status == 409) {
//                    System.out.printf("[KC-ADMIN] User already exists in Keycloak: %s%n", username);
//                    List<UserRepresentation> existing = usersResource.search(username, true);
//                    if (!existing.isEmpty()) {
//                        String existingId = existing.get(0).getId();
//                        setPassword(usersResource, existingId, password);
//                        assignRealmRole(realmResource, existingId, roleName);
//                        return existingId;
//                    }
//                    return "existing-user";
//                } else {
//                    String body = response.readEntity(String.class);
//                    throw new RuntimeException("Failed to create user HTTP " + status + ": " + body);
//                }
//            }
//        } catch (RuntimeException e) {
//            throw e;
//        } catch (Exception e) {
//            throw new RuntimeException("Keycloak user creation error: " + e.getMessage(), e);
//        }
//    }
//
//    // ── SYNC ROLE — called on every login ────────────────────────────────
//    /**
//     * Ensures the user's Keycloak realm role matches their MySQL DB role.
//     * Called automatically during login so Keycloak is always in sync with DB.
//     *
//     * DB role ROLE_ADMIN → Keycloak role "admin"
//     * DB role ROLE_USER  → Keycloak role "user"
//     *
//     * If user has the wrong role in Keycloak (e.g. user was promoted to admin
//     * in MySQL), this method removes the old role and assigns the correct one.
//     */
//    public void syncUserRole(String username, String dbRoleName) {
//        try (Keycloak kc = getAdminKeycloak()) {
//            RealmResource realmResource = kc.realm(realm);
//
//            // Find user in Keycloak
//            List<UserRepresentation> users = realmResource.users().search(username, true);
//            if (users.isEmpty()) {
//                System.out.printf("[KC-SYNC] User not found in Keycloak: %s — skipping sync%n", username);
//                return;
//            }
//
//            String userId = users.get(0).getId();
//            UserResource userResource = realmResource.users().get(userId);
//
//            // Get current Keycloak realm roles for this user
//            List<RoleRepresentation> currentRoles = userResource.roles()
//                    .realmLevel().listEffective();
//            List<String> currentRoleNames = currentRoles.stream()
//                    .map(RoleRepresentation::getName)
//                    .collect(Collectors.toList());
//
//            // Determine what role SHOULD be in Keycloak
//            String targetKcRole = (dbRoleName != null && dbRoleName.contains("ADMIN"))
//                    ? "admin" : "user";
//            String oppositeKcRole = "admin".equals(targetKcRole) ? "user" : "admin";
//
//            boolean hasTarget   = currentRoleNames.contains(targetKcRole);
//            boolean hasOpposite = currentRoleNames.contains(oppositeKcRole);
//
//            System.out.printf("[KC-SYNC] %s → DB role=%s | KC roles=%s | target=%s%n",
//                    username, dbRoleName, currentRoleNames, targetKcRole);
//
//            // Remove opposite role if present
//            if (hasOpposite) {
//                try {
//                    RoleRepresentation oppositeRole = realmResource.roles()
//                            .get(oppositeKcRole).toRepresentation();
//                    userResource.roles().realmLevel().remove(List.of(oppositeRole));
//                    System.out.printf("[KC-SYNC] ✓ Removed role '%s' from %s%n",
//                            oppositeKcRole, username);
//                } catch (Exception e) {
//                    System.err.printf("[KC-SYNC] Could not remove role '%s': %s%n",
//                            oppositeKcRole, e.getMessage());
//                }
//            }
//
//            // Assign target role if not already present
//            if (!hasTarget) {
//                try {
//                    RoleRepresentation targetRole = realmResource.roles()
//                            .get(targetKcRole).toRepresentation();
//                    userResource.roles().realmLevel().add(List.of(targetRole));
//                    System.out.printf("[KC-SYNC] ✓ Assigned role '%s' to %s%n",
//                            targetKcRole, username);
//                } catch (Exception e) {
//                    System.err.printf("[KC-SYNC] Could not assign role '%s': %s%n",
//                            targetKcRole, e.getMessage());
//                }
//            } else {
//                System.out.printf("[KC-SYNC] ✓ Role '%s' already correct for %s%n",
//                        targetKcRole, username);
//            }
//
//        } catch (Exception e) {
//            // Non-fatal — log and continue. Login still proceeds.
//            System.err.printf("[KC-SYNC] Role sync failed for %s: %s%n",
//                    username, e.getMessage());
//        }
//    }
//
//    // ── Set password ──────────────────────────────────────────────────────
//    private void setPassword(UsersResource usersResource, String userId, String password) {
//        CredentialRepresentation credential = new CredentialRepresentation();
//        credential.setType(CredentialRepresentation.PASSWORD);
//        credential.setValue(password);
//        credential.setTemporary(false);
//        usersResource.get(userId).resetPassword(credential);
//    }
//
//    // ── Assign realm role ─────────────────────────────────────────────────
//    private void assignRealmRole(RealmResource realmResource, String userId, String roleName) {
//        try {
//            String kcRoleName = (roleName != null && roleName.contains("ADMIN")) ? "admin" : "user";
//            RoleRepresentation role = realmResource.roles().get(kcRoleName).toRepresentation();
//            realmResource.users().get(userId).roles().realmLevel().add(List.of(role));
//            System.out.printf("[KC-ADMIN] Role '%s' assigned to userId=%s%n", kcRoleName, userId);
//        } catch (Exception e) {
//            System.err.println("[KC-ADMIN] Role assignment error: " + e.getMessage());
//        }
//    }
//
//    // ── Obtain token via password grant ───────────────────────────────────
//    public Map<String, Object> obtainToken(String username, String password) {
//        String tokenUrl = authServerUrl + "/realms/" + realm
//                + "/protocol/openid-connect/token";
//        String body = "grant_type=password"
//                + "&client_id=" + encode(clientId)
//                + "&username="  + encode(username)
//                + "&password="  + encode(password);
//        System.out.printf("[KEYCLOAK] Requesting token for user=%s at %s%n", username, tokenUrl);
//        try {
//            @SuppressWarnings("unchecked")
//            Map<String, Object> result = WebClient.create().post()
//                    .uri(tokenUrl)
//                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
//                    .bodyValue(body)
//                    .retrieve()
//                    .onStatus(
//                        s -> s.is4xxClientError() || s.is5xxServerError(),
//                        cr -> cr.bodyToMono(String.class).map(err -> {
//                            System.err.println("[KEYCLOAK] Token error: " + err);
//                            return new RuntimeException("Keycloak token error: " + err);
//                        })
//                    )
//                    .bodyToMono(Map.class)
//                    .block();
//            if (result != null && result.containsKey("access_token")) {
//                System.out.println("[KEYCLOAK] Token obtained successfully");
//                return result;
//            }
//            return Map.of("error", "No access_token in response");
//        } catch (Exception e) {
//            throw new RuntimeException("Authentication failed: " + e.getMessage());
//        }
//    }
//
//    // ── Refresh token ─────────────────────────────────────────────────────
//    public Map<String, Object> refreshToken(String refreshToken) {
//        String tokenUrl = authServerUrl + "/realms/" + realm
//                + "/protocol/openid-connect/token";
//        String body = "grant_type=refresh_token"
//                + "&client_id="    + encode(clientId)
//                + "&refresh_token=" + encode(refreshToken);
//        try {
//            @SuppressWarnings("unchecked")
//            Map<String, Object> result = WebClient.create().post()
//                    .uri(tokenUrl)
//                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
//                    .bodyValue(body)
//                    .retrieve()
//                    .bodyToMono(Map.class)
//                    .block();
//            return result != null ? result : Map.of("error", "No response");
//        } catch (Exception e) {
//            throw new RuntimeException("Token refresh failed: " + e.getMessage());
//        }
//    }
//
//    // ── Check if user exists ──────────────────────────────────────────────
//    public boolean userExistsInKeycloak(String username) {
//        try (Keycloak kc = getAdminKeycloak()) {
//            List<UserRepresentation> users = kc.realm(realm).users().search(username, true);
//            return !users.isEmpty();
//        } catch (Exception e) {
//            System.err.println("[KC-ADMIN] userExists error: " + e.getMessage());
//            return false;
//        }
//    }
//
//    private String encode(String v) {
//        return URLEncoder.encode(v != null ? v : "", StandardCharsets.UTF_8);
//    }
//}
//
