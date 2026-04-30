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

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);

            System.out.printf("[GatewayAuthFilter] auth set for user=%s role=%s%n",
                    username, role);
        } else {
            System.err.printf("[GatewayAuthFilter] %s %s → no X-Auth-Username header!%n",
                    request.getMethod(), request.getRequestURI());
        }

        filterChain.doFilter(request, response);
    }
}

