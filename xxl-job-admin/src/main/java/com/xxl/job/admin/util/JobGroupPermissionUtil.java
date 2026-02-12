package com.xxl.job.admin.util;

import java.util.Collections;
import java.util.List;

import com.xxl.job.admin.constant.Consts;
import com.xxl.job.admin.model.XxlJobGroup;
import com.xxl.sso.core.helper.XxlSsoHelper;
import com.xxl.sso.core.model.LoginInfo;
import com.xxl.tool.core.StringTool;
import com.xxl.tool.response.Response;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Job group permission validation utility for Orth admin.
 *
 * <p>Provides centralized permission checks for job group access control. Users with ADMIN role
 * have access to all job groups, while regular users only have access to explicitly assigned
 * groups.
 *
 * <p>Job group permissions are stored in the user's extra info as a comma-separated list of group
 * IDs under the "jobGroups" key.
 *
 * @author xuxueli 2025-08-24
 */
public class JobGroupPermissionUtil {

    private static final String JOB_GROUPS_KEY = "jobGroups";

    private JobGroupPermissionUtil() {
        // Utility class - prevent instantiation
    }

    /**
     * Checks if user has permission to access the specified job group.
     *
     * <p>Administrators have access to all groups. Regular users only have access to groups
     * explicitly listed in their permissions.
     *
     * @param loginInfo user login information
     * @param jobGroup job group ID to check
     * @return true if user has access, false otherwise
     */
    public static boolean hasJobGroupPermission(LoginInfo loginInfo, int jobGroup) {
        // Admin has access to all job groups
        if (XxlSsoHelper.hasRole(loginInfo, Consts.ADMIN_ROLE).isSuccess()) {
            return true;
        }

        // Check if job group is in user's permission list
        List<String> allowedGroups = extractJobGroups(loginInfo);
        return allowedGroups.contains(String.valueOf(jobGroup));
    }

    /**
     * Validates job group permission and returns login info if valid.
     *
     * <p>Throws RuntimeException if the user does not have permission to access the specified job
     * group.
     *
     * @param request HTTP request containing login credentials
     * @param jobGroup job group ID to validate
     * @return login info if permission check passes
     * @throws RuntimeException if permission check fails
     */
    public static LoginInfo validJobGroupPermission(HttpServletRequest request, int jobGroup) {
        Response<LoginInfo> loginInfoResponse = XxlSsoHelper.loginCheckWithAttr(request);

        if (!loginInfoResponse.isSuccess()) {
            throw new RuntimeException(I18nUtil.getString("system_permission_limit"));
        }

        LoginInfo loginInfo = loginInfoResponse.getData();
        if (!hasJobGroupPermission(loginInfo, jobGroup)) {
            throw new RuntimeException(
                    I18nUtil.getString("system_permission_limit")
                            + "[username="
                            + loginInfo.getUserName()
                            + "]");
        }

        return loginInfo;
    }

    /**
     * Filters job group list based on user permissions.
     *
     * <p>Administrators receive the full list. Regular users receive only groups they have
     * permission to access.
     *
     * @param request HTTP request containing login credentials
     * @param jobGroupListTotal complete list of job groups to filter
     * @return filtered list based on user permissions
     */
    public static List<XxlJobGroup> filterJobGroupByPermission(
            HttpServletRequest request, List<XxlJobGroup> jobGroupListTotal) {
        Response<LoginInfo> loginInfoResponse = XxlSsoHelper.loginCheckWithAttr(request);

        if (!loginInfoResponse.isSuccess()) {
            return Collections.emptyList();
        }

        LoginInfo loginInfo = loginInfoResponse.getData();

        // Admin has access to all groups
        if (XxlSsoHelper.hasRole(loginInfo, Consts.ADMIN_ROLE).isSuccess()) {
            return jobGroupListTotal;
        }

        // Filter by user's allowed groups
        List<String> allowedGroups = extractJobGroups(loginInfo);
        return jobGroupListTotal.stream()
                .filter(jobGroup -> allowedGroups.contains(String.valueOf(jobGroup.getId())))
                .toList();
    }

    /**
     * Extracts job group IDs from user's extra info.
     *
     * @param loginInfo user login information
     * @return list of job group IDs the user has access to
     */
    private static List<String> extractJobGroups(LoginInfo loginInfo) {
        if (loginInfo.getExtraInfo() == null
                || !loginInfo.getExtraInfo().containsKey(JOB_GROUPS_KEY)) {
            return Collections.emptyList();
        }

        String jobGroupsStr = loginInfo.getExtraInfo().get(JOB_GROUPS_KEY);
        return StringTool.split(jobGroupsStr, ",");
    }
}
