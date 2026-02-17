package com.xxl.job.admin.web.security;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.xxl.tool.gson.GsonTool;
import com.xxl.tool.response.Response;

import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * JWT authentication filter.
 *
 * <p>Intercepts requests, extracts Bearer token from Authorization header, validates it, and stores
 * the authenticated user info as a request attribute. Excludes auth endpoints and executor OpenAPI
 * paths.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String JSON_CONTENT_TYPE = "application/json;charset=UTF-8";

    @Resource private JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            sendUnauthorized(response, "Missing or invalid Authorization header");
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        JwtUserInfo userInfo = jwtTokenProvider.validateAccessToken(token);

        if (userInfo == null) {
            sendUnauthorized(response, "Invalid or expired token");
            return;
        }

        SecurityContext.setCurrentUser(request, userInfo);
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        // Auth endpoints don't need JWT
        if (path.equals("/api/v1/auth/login") || path.equals("/api/v1/auth/refresh")) {
            return true;
        }
        // Executor OpenAPI uses its own access token validation
        if (path.startsWith("/api/") && !path.startsWith("/api/v1/")) {
            return true;
        }
        // Actuator endpoints
        if (path.startsWith("/actuator")) {
            return true;
        }
        // Only filter /api/v1/ paths (REST API)
        return !path.startsWith("/api/v1/");
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(JSON_CONTENT_TYPE);
        response.getWriter().write(GsonTool.toJson(Response.ofFail(message)));
    }
}
