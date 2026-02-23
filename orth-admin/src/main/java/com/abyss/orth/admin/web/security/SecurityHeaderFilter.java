package com.abyss.orth.admin.web.security;

import java.io.IOException;

import org.springframework.stereotype.Component;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Adds standard security response headers to all HTTP responses.
 *
 * <ul>
 *   <li>{@code X-Content-Type-Options: nosniff} — prevents MIME-type sniffing
 *   <li>{@code X-Frame-Options: DENY} — prevents clickjacking via iframes
 *   <li>{@code X-XSS-Protection: 0} — disables legacy XSS auditor (modern CSP is preferred)
 *   <li>{@code Cache-Control: no-store} — prevents caching of API responses
 * </ul>
 */
@Component
public class SecurityHeaderFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (response instanceof HttpServletResponse httpResponse) {
            httpResponse.setHeader("X-Content-Type-Options", "nosniff");
            httpResponse.setHeader("X-Frame-Options", "DENY");
            httpResponse.setHeader("X-XSS-Protection", "0");
            httpResponse.setHeader("Cache-Control", "no-store");
        }
        chain.doFilter(request, response);
    }
}
