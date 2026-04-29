package com.spantag.order_service;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * GatewayAuthFilter — reads X-Auth-* headers injected by gateway's KeycloakJwtFilter
 * and establishes the Spring Security authentication context.
 *
 * FIX for Spring Security 7.x (Spring Boot 4.x):
 *   OLD pattern (BROKEN in Spring Security 7):
 *     SecurityContextHolder.getContext().setAuthentication(auth)
 *   
 *   In Spring Security 7, SecurityContextHolderFilter uses DeferredSecurityContext.
 *   Calling getContext() evaluates the deferred context and returns a SecurityContextImpl,
 *   but that instance is NOT stored back in the holder's ThreadLocal. Subsequent calls
 *   to getContext() from AuthorizationFilter re-evaluate the deferred context and get
 *   a FRESH empty SecurityContext — losing the authentication we just set.
 *
 *   NEW pattern (CORRECT for Spring Security 6.x/7.x):
 *     SecurityContext ctx = SecurityContextHolder.createEmptyContext();
 *     ctx.setAuthentication(auth);
 *     SecurityContextHolder.setContext(ctx);  // ← explicitly stores in ThreadLocal
 *
 *   setContext() bypasses the deferred mechanism and stores directly in ThreadLocal,
 *   so ALL subsequent getContext() calls in this request thread return the same
 *   context with authentication set.
 */
@Component
public class GatewayAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String username = request.getHeader("X-Auth-Username");
        String role     = request.getHeader("X-Auth-Role");

        System.out.printf("[GatewayAuthFilter] %s %s → X-Auth-Username=%s X-Auth-Role=%s%n",
                request.getMethod(), request.getRequestURI(), username, role);

        if (username != null && !username.isBlank()) {
            List<SimpleGrantedAuthority> authorities = role != null && !role.isBlank()
                    ? List.of(new SimpleGrantedAuthority(role))
                    : List.of();

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(username, null, authorities);

            // ── Spring Security 7.x FIX ───────────────────────────────────
            // Create a fresh SecurityContext and set it explicitly on the holder.
            // This stores it in the ThreadLocal directly, bypassing DeferredSecurityContext.
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
            // ─────────────────────────────────────────────────────────────

            System.out.printf("[GatewayAuthFilter] auth set for user=%s role=%s%n",
                    username, role);
        } else {
            System.err.printf("[GatewayAuthFilter] %s %s → no X-Auth-Username header!%n",
                    request.getMethod(), request.getRequestURI());
        }

        filterChain.doFilter(request, response);
    }
}

//package com.spantag.order_service;
//
//import jakarta.servlet.FilterChain;
//import jakarta.servlet.ServletException;
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpServletResponse;
//import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
//import org.springframework.security.core.authority.SimpleGrantedAuthority;
//import org.springframework.security.core.context.SecurityContextHolder;
//import org.springframework.stereotype.Component;
//import org.springframework.web.filter.OncePerRequestFilter;
//
//import java.io.IOException;
//import java.util.List;
//
//@Component
//public class GatewayAuthFilter extends OncePerRequestFilter {
//
//    @Override
//    protected void doFilterInternal(HttpServletRequest request,
//                                    HttpServletResponse response,
//                                    FilterChain filterChain)
//            throws ServletException, IOException {
//
//        String username = request.getHeader("X-Auth-Username");
//        String role     = request.getHeader("X-Auth-Role");
//
//        if (username != null && !username.isBlank()) {
//            List<SimpleGrantedAuthority> authorities = role != null && !role.isBlank()
//                    ? List.of(new SimpleGrantedAuthority(role))
//                    : List.of();
//            SecurityContextHolder.getContext().setAuthentication(
//                    new UsernamePasswordAuthenticationToken(username, null, authorities));
//        }
//
//        filterChain.doFilter(request, response);
//    }
//}