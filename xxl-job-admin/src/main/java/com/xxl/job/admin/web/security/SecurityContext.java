package com.xxl.job.admin.web.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Security context utility for accessing authenticated user information.
 *
 * <p>Stores and retrieves the current user from request attributes. Replaces {@code XxlSsoHelper}
 * from xxl-sso-core.
 */
public class SecurityContext {

    private static final String USER_ATTRIBUTE = "JWT_USER_INFO";
    private static final int ADMIN_ROLE = 1;

    private SecurityContext() {
        // Utility class
    }

    public static void setCurrentUser(HttpServletRequest request, JwtUserInfo userInfo) {
        request.setAttribute(USER_ATTRIBUTE, userInfo);
    }

    public static JwtUserInfo getCurrentUser(HttpServletRequest request) {
        return (JwtUserInfo) request.getAttribute(USER_ATTRIBUTE);
    }

    public static boolean isAdmin(JwtUserInfo userInfo) {
        return userInfo != null && userInfo.getRole() == ADMIN_ROLE;
    }
}
