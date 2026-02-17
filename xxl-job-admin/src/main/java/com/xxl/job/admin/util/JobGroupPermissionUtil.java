package com.xxl.job.admin.util;

import java.util.Collections;
import java.util.List;

import com.xxl.job.admin.model.XxlJobGroup;
import com.xxl.job.admin.web.security.JwtUserInfo;
import com.xxl.job.admin.web.security.SecurityContext;
import com.xxl.tool.core.StringTool;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Job group permission validation utility for Orth admin.
 *
 * <p>Provides centralized permission checks for job group access control. Users with ADMIN role
 * have access to all job groups, while regular users only have access to explicitly assigned
 * groups.
 *
 * <p>Job group permissions are stored as a comma-separated list of group IDs in the user's
 * permission field.
 *
 * @author xuxueli 2025-08-24
 */
public class JobGroupPermissionUtil {

    private static final int ADMIN_ROLE = 1;

    private JobGroupPermissionUtil() {
        // Utility class - prevent instantiation
    }

    /**
     * Checks if user has permission to access the specified job group.
     *
     * <p>Administrators have access to all groups. Regular users only have access to groups
     * explicitly listed in their permissions.
     *
     * @param userInfo JWT user information
     * @param jobGroup job group ID to check
     * @return true if user has access, false otherwise
     */
    public static boolean hasJobGroupPermission(JwtUserInfo userInfo, int jobGroup) {
        if (userInfo.getRole() == ADMIN_ROLE) {
            return true;
        }

        List<String> allowedGroups = extractJobGroups(userInfo);
        return allowedGroups.contains(String.valueOf(jobGroup));
    }

    /**
     * Validates job group permission and returns user info if valid.
     *
     * <p>Throws RuntimeException if the user does not have permission to access the specified job
     * group.
     *
     * @param request HTTP request containing authenticated user
     * @param jobGroup job group ID to validate
     * @return user info if permission check passes
     * @throws RuntimeException if permission check fails
     */
    public static JwtUserInfo validJobGroupPermission(HttpServletRequest request, int jobGroup) {
        JwtUserInfo userInfo = SecurityContext.getCurrentUser(request);

        if (userInfo == null) {
            throw new RuntimeException(I18nUtil.getString("system_permission_limit"));
        }

        if (!hasJobGroupPermission(userInfo, jobGroup)) {
            throw new RuntimeException(
                    I18nUtil.getString("system_permission_limit")
                            + "[username="
                            + userInfo.getUsername()
                            + "]");
        }

        return userInfo;
    }

    /**
     * Filters job group list based on user permissions.
     *
     * <p>Administrators receive the full list. Regular users receive only groups they have
     * permission to access.
     *
     * @param request HTTP request containing authenticated user
     * @param jobGroupListTotal complete list of job groups to filter
     * @return filtered list based on user permissions
     */
    public static List<XxlJobGroup> filterJobGroupByPermission(
            HttpServletRequest request, List<XxlJobGroup> jobGroupListTotal) {
        JwtUserInfo userInfo = SecurityContext.getCurrentUser(request);

        if (userInfo == null) {
            return Collections.emptyList();
        }

        if (userInfo.getRole() == ADMIN_ROLE) {
            return jobGroupListTotal;
        }

        List<String> allowedGroups = extractJobGroups(userInfo);
        return jobGroupListTotal.stream()
                .filter(jobGroup -> allowedGroups.contains(String.valueOf(jobGroup.getId())))
                .toList();
    }

    /**
     * Extracts job group IDs from user's permission field.
     *
     * @param userInfo JWT user information
     * @return list of job group IDs the user has access to
     */
    private static List<String> extractJobGroups(JwtUserInfo userInfo) {
        if (userInfo.getPermission() == null || userInfo.getPermission().isEmpty()) {
            return Collections.emptyList();
        }

        return StringTool.split(userInfo.getPermission(), ",");
    }
}
