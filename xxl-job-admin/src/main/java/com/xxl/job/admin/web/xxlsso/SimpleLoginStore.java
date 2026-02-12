package com.xxl.job.admin.web.xxlsso;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.xxl.job.admin.constant.Consts;
import com.xxl.job.admin.mapper.XxlJobUserMapper;
import com.xxl.job.admin.model.XxlJobUser;
import com.xxl.sso.core.model.LoginInfo;
import com.xxl.sso.core.store.LoginStore;
import com.xxl.tool.core.MapTool;
import com.xxl.tool.response.Response;

import jakarta.annotation.Resource;

/**
 * Database-backed login session store for Orth.
 *
 * <p>Provides a simple implementation of LoginStore that persists session tokens in the database.
 * This implementation is suitable for moderate traffic scenarios. For high-performance requirements
 * or distributed deployments, consider using RedisLoginStore instead.
 *
 * <p>Session tokens are stored in the user table and validated on each request. The update
 * operation is not supported in this implementation.
 *
 * @author xuxueli 2025-08-03
 */
@Component
public class SimpleLoginStore implements LoginStore {

    private static final int ADMIN_ROLE_VALUE = 1;

    @Resource private XxlJobUserMapper xxlJobUserMapper;

    /**
     * Stores login session token for the user.
     *
     * @param loginInfo login information containing user ID and session token
     * @return success response if token stored successfully, failure otherwise
     */
    @Override
    public Response<String> set(LoginInfo loginInfo) {
        try {
            int userId = Integer.parseInt(loginInfo.getUserId());
            String tokenSignature = loginInfo.getSignature();

            int updated = xxlJobUserMapper.updateToken(userId, tokenSignature);
            return updated > 0 ? Response.ofSuccess() : Response.ofFail("Failed to store token");
        } catch (NumberFormatException e) {
            return Response.ofFail("Invalid user ID format");
        }
    }

    /**
     * Updates existing login session (not supported in this implementation).
     *
     * @param loginInfo login information to update
     * @return failure response indicating operation not supported
     */
    @Override
    public Response<String> update(LoginInfo loginInfo) {
        return Response.ofFail("Update operation not supported");
    }

    /**
     * Removes login session token for the user.
     *
     * @param userId user ID whose session should be removed
     * @return success response if token removed successfully, failure otherwise
     */
    @Override
    public Response<String> remove(String userId) {
        try {
            int userIdInt = Integer.parseInt(userId);
            int updated = xxlJobUserMapper.updateToken(userIdInt, "");
            return updated > 0 ? Response.ofSuccess() : Response.ofFail("Failed to remove token");
        } catch (NumberFormatException e) {
            return Response.ofFail("Invalid user ID format");
        }
    }

    /**
     * Retrieves login information by validating stored session token.
     *
     * <p>Loads user data from database and constructs LoginInfo with role and job group
     * permissions.
     *
     * @param userId user ID to retrieve
     * @return login information if valid, failure response if user not found
     */
    @Override
    public Response<LoginInfo> get(String userId) {
        try {
            int userIdInt = Integer.parseInt(userId);
            XxlJobUser user = xxlJobUserMapper.loadById(userIdInt);

            if (user == null) {
                return Response.ofFail("User not found");
            }

            LoginInfo loginInfo = buildLoginInfo(userId, user);
            return Response.ofSuccess(loginInfo);

        } catch (NumberFormatException e) {
            return Response.ofFail("Invalid user ID format");
        }
    }

    /**
     * Builds LoginInfo object from user data.
     *
     * @param userId user ID
     * @param user user entity
     * @return populated LoginInfo
     */
    private LoginInfo buildLoginInfo(String userId, XxlJobUser user) {
        LoginInfo loginInfo = new LoginInfo(userId, user.getToken());
        loginInfo.setUserName(user.getUsername());
        loginInfo.setRoleList(extractRoles(user));
        loginInfo.setExtraInfo(extractExtraInfo(user));
        return loginInfo;
    }

    /**
     * Extracts role list from user data.
     *
     * @param user user entity
     * @return list containing admin role if user is admin, empty list otherwise
     */
    private List<String> extractRoles(XxlJobUser user) {
        return user.getRole() == ADMIN_ROLE_VALUE
                ? List.of(Consts.ADMIN_ROLE)
                : Collections.emptyList();
    }

    /**
     * Extracts extra information from user data (job group permissions).
     *
     * @param user user entity
     * @return map containing job groups the user has access to
     */
    private Map<String, String> extractExtraInfo(XxlJobUser user) {
        return MapTool.newMap("jobGroups", user.getPermission());
    }
}
