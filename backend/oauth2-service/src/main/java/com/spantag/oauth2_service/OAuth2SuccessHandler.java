package com.spantag.oauth2_service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Component
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final WebClient authClient = WebClient.builder()
            .baseUrl("http://localhost:9090")
            .build();

    private static final String FRONTEND_REDIRECT = "http://localhost:5173/oauth2/callback";
    private static final String OAUTH2_DEFAULT_ROLE = "ROLE_USER";

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oauthUser = oauthToken.getPrincipal();
        String provider      = oauthToken.getAuthorizedClientRegistrationId();

        String name       = extractName(oauthUser, provider);
        String email      = extractEmail(oauthUser, provider);
        String avatarUrl  = extractAvatar(oauthUser, provider);
        String providerId = extractProviderId(oauthUser, provider);

        System.out.printf("[OAUTH2] Provider=%s name=%s email=%s%n", provider, name, email);

        Map<String, Object> savedUser = saveUserToDb(
                provider, providerId, name, email, avatarUrl);
        String resolvedUsername = savedUser != null
                ? str(savedUser.get("username"))
                : provider + "_" + sanitize(name);

        System.out.printf("[OAUTH2] Resolved username=%s%n", resolvedUsername);

        Map<String, Object> tokenData = getOAuth2Token(
                resolvedUsername, response);

        String accessToken = tokenData != null ? str(tokenData.get("accessToken")) : "";

        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();

        String redirectUrl = FRONTEND_REDIRECT
                + "?username="    + encode(resolvedUsername)
                + "&displayName=" + encode(name)
                + "&email="       + encode(email)
                + "&avatar="      + encode(avatarUrl)
                + "&role="        + encode(OAUTH2_DEFAULT_ROLE)
                + "&provider="    + encode(provider)
                + "&loginMethod=" + encode("oauth2")
                + "&roleLabel="   + encode("USER")
                + "&methodLabel=" + encode(cap(provider) + " OAuth2");

        System.out.printf("[OAUTH2] ✓ Login complete → username=%s provider=%s hasToken=%s%n",
                resolvedUsername, provider, !accessToken.isEmpty());

        response.sendRedirect(redirectUrl);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> saveUserToDb(String provider, String providerId,
                                              String name, String email, String avatarUrl) {
        try {
            Map<String, String> body = new HashMap<>();
            body.put("provider",    provider);
            body.put("providerId",  providerId);
            body.put("username",    provider + "_" + sanitize(name));
            body.put("displayName", name);
            body.put("email",       email);
            body.put("avatar",      avatarUrl);

            Map<String, Object> result = authClient.post()
                    .uri("/api/auth/oauth2/save-user")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            System.out.printf("[OAUTH2] save-user result: %s%n", result);
            return result;
        } catch (Exception e) {
            System.err.println("[OAUTH2] save-user failed: " + e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getOAuth2Token(String username,
                                                HttpServletResponse httpResponse) {
        try {
            var result = authClient.post()
                    .uri("/api/auth/oauth2/token")
                    .bodyValue(Map.of("username", username))
                    .exchangeToMono(clientResponse -> {
                        // Forward Set-Cookie (kc_refresh_token) from auth-service to browser
                        clientResponse.headers().asHttpHeaders()
                                .getValuesAsList("Set-Cookie")
                                .forEach(v -> {
                                    httpResponse.addHeader("Set-Cookie", v);
                                    System.out.println("[OAUTH2] Forwarded cookie: " +
                                            v.substring(0, Math.min(v.indexOf(";") + 1, v.length())));
                                });
                        return clientResponse.bodyToMono(Map.class);
                    })
                    .block();

            System.out.printf("[OAUTH2] Token result: %s%n",
                    result != null ? "accessToken length=" +
                    str(result.get("accessToken")).length() : "null");
            return result != null ? new HashMap<>(result) : Map.of();

        } catch (Exception e) {
            System.err.println("[OAUTH2] getOAuth2Token failed: " + e.getMessage());
            return Map.of();
        }
    }

    private String extractName(OAuth2User user, String provider) {
        String name = user.getAttribute("name");
        if (name == null || name.isBlank()) name = user.getAttribute("login"); // GitHub
        return name != null ? name : "User";
    }

    private String extractEmail(OAuth2User user, String provider) {
        String email = user.getAttribute("email");
        return email != null ? email : provider + "@oauth2.user";
    }

    private String extractAvatar(OAuth2User user, String provider) {
        String pic = "google".equals(provider)
                ? user.getAttribute("picture")
                : user.getAttribute("avatar_url");
        return pic != null ? pic : "";
    }

    private String extractProviderId(OAuth2User user, String provider) {
        Object id = "google".equals(provider)
                ? user.getAttribute("sub")
                : user.getAttribute("id");
        return id != null ? id.toString() : user.getName();
    }

    private String sanitize(String name) {
        if (name == null) return "user";
        return name.toLowerCase()
                   .replaceAll("[^a-z0-9]", "_")
                   .replaceAll("_+", "_")
                   .replaceAll("^_|_$", "");
    }

    private String encode(String v) {
        return URLEncoder.encode(v != null ? v : "", StandardCharsets.UTF_8);
    }

    private String str(Object o) { return o != null ? o.toString() : ""; }

    private String cap(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}

