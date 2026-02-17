package com.xxl.job.admin.controller.api;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xxl.job.admin.mapper.XxlJobUserMapper;
import com.xxl.job.admin.model.XxlJobUser;
import com.xxl.job.admin.model.dto.LoginRequest;
import com.xxl.job.admin.model.dto.LoginResponse;
import com.xxl.job.admin.model.dto.PasswordUpdateRequest;
import com.xxl.job.admin.model.dto.RefreshRequest;
import com.xxl.job.admin.util.I18nUtil;
import com.xxl.job.admin.web.security.JwtProperties;
import com.xxl.job.admin.web.security.JwtTokenProvider;
import com.xxl.job.admin.web.security.JwtUserInfo;
import com.xxl.job.admin.web.security.SecurityContext;
import com.xxl.tool.core.StringTool;
import com.xxl.tool.encrypt.SHA256Tool;
import com.xxl.tool.response.Response;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Authentication controller for JWT-based login, logout, token refresh, and password management.
 *
 * @author xuxueli 2015-12-19 16:13:16
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final int MIN_PASSWORD_LENGTH = 4;
    private static final int MAX_PASSWORD_LENGTH = 20;

    @Resource private XxlJobUserMapper xxlJobUserMapper;
    @Resource private JwtTokenProvider jwtTokenProvider;
    @Resource private JwtProperties jwtProperties;

    /**
     * Authenticates user and returns JWT tokens.
     *
     * @param request login credentials
     * @return access token, refresh token, and user info
     */
    @PostMapping("/login")
    public Response<LoginResponse> login(@RequestBody LoginRequest request) {
        if (StringTool.isBlank(request.getUsername())
                || StringTool.isBlank(request.getPassword())) {
            return Response.ofFail(I18nUtil.getString("login_param_empty"));
        }

        XxlJobUser user = xxlJobUserMapper.loadByUserName(request.getUsername());
        if (user == null) {
            return Response.ofFail(I18nUtil.getString("login_param_unvalid"));
        }

        String passwordHash = SHA256Tool.sha256(request.getPassword());
        if (!passwordHash.equals(user.getPassword())) {
            return Response.ofFail(I18nUtil.getString("login_param_unvalid"));
        }

        JwtUserInfo userInfo =
                new JwtUserInfo(
                        user.getId(), user.getUsername(), user.getRole(), user.getPermission());

        String accessToken = jwtTokenProvider.generateAccessToken(userInfo);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userInfo);

        // Store refresh token hash in DB for validation
        String refreshTokenHash = SHA256Tool.sha256(refreshToken);
        xxlJobUserMapper.updateToken(user.getId(), refreshTokenHash);

        LoginResponse loginResponse = new LoginResponse();
        loginResponse.setAccessToken(accessToken);
        loginResponse.setRefreshToken(refreshToken);
        loginResponse.setExpiresIn(jwtProperties.getAccessTokenExpiration());
        loginResponse.setUserInfo(userInfo);

        return Response.ofSuccess(loginResponse);
    }

    /**
     * Refreshes access token using a valid refresh token.
     *
     * @param request refresh token
     * @return new access token and refresh token
     */
    @PostMapping("/refresh")
    public Response<LoginResponse> refresh(@RequestBody RefreshRequest request) {
        if (StringTool.isBlank(request.getRefreshToken())) {
            return Response.ofFail("Refresh token is required");
        }

        JwtUserInfo userInfo = jwtTokenProvider.validateRefreshToken(request.getRefreshToken());
        if (userInfo == null) {
            return Response.ofFail("Invalid or expired refresh token");
        }

        // Verify refresh token hash matches DB
        String refreshTokenHash = SHA256Tool.sha256(request.getRefreshToken());
        XxlJobUser user = xxlJobUserMapper.loadById(userInfo.getUserId());
        if (user == null || !refreshTokenHash.equals(user.getToken())) {
            return Response.ofFail("Refresh token has been revoked");
        }

        // Re-read user data for latest role/permission
        JwtUserInfo freshUserInfo =
                new JwtUserInfo(
                        user.getId(), user.getUsername(), user.getRole(), user.getPermission());

        String newAccessToken = jwtTokenProvider.generateAccessToken(freshUserInfo);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(freshUserInfo);

        // Update stored refresh token hash
        String newRefreshTokenHash = SHA256Tool.sha256(newRefreshToken);
        xxlJobUserMapper.updateToken(user.getId(), newRefreshTokenHash);

        LoginResponse loginResponse = new LoginResponse();
        loginResponse.setAccessToken(newAccessToken);
        loginResponse.setRefreshToken(newRefreshToken);
        loginResponse.setExpiresIn(jwtProperties.getAccessTokenExpiration());
        loginResponse.setUserInfo(freshUserInfo);

        return Response.ofSuccess(loginResponse);
    }

    /**
     * Logs out user by clearing stored refresh token.
     *
     * @param request HTTP request with authenticated user
     * @return success response
     */
    @PostMapping("/logout")
    public Response<String> logout(HttpServletRequest request) {
        JwtUserInfo userInfo = SecurityContext.getCurrentUser(request);
        if (userInfo != null) {
            xxlJobUserMapper.updateToken(userInfo.getUserId(), "");
        }
        return Response.ofSuccess();
    }

    /**
     * Updates password for the currently authenticated user.
     *
     * @param request HTTP request with authenticated user
     * @param passwordRequest old and new passwords
     * @return success or error response
     */
    @PutMapping("/password")
    public Response<String> updatePassword(
            HttpServletRequest request, @RequestBody PasswordUpdateRequest passwordRequest) {

        Response<String> validationResult =
                validatePasswordUpdate(
                        passwordRequest.getOldPassword(), passwordRequest.getNewPassword());
        if (!validationResult.isSuccess()) {
            return validationResult;
        }

        JwtUserInfo userInfo = SecurityContext.getCurrentUser(request);
        XxlJobUser existingUser = xxlJobUserMapper.loadByUserName(userInfo.getUsername());

        String oldPasswordHash = SHA256Tool.sha256(passwordRequest.getOldPassword());
        if (!oldPasswordHash.equals(existingUser.getPassword())) {
            return Response.ofFail(
                    I18nUtil.getString("change_pwd_field_oldpwd")
                            + I18nUtil.getString("system_unvalid"));
        }

        String newPasswordHash = SHA256Tool.sha256(passwordRequest.getNewPassword());
        existingUser.setPassword(newPasswordHash);
        xxlJobUserMapper.update(existingUser);

        return Response.ofSuccess();
    }

    private Response<String> validatePasswordUpdate(String oldPassword, String newPassword) {
        if (StringTool.isBlank(oldPassword)) {
            return Response.ofFail(
                    I18nUtil.getString("system_please_input")
                            + I18nUtil.getString("change_pwd_field_oldpwd"));
        }

        if (StringTool.isBlank(newPassword)) {
            return Response.ofFail(
                    I18nUtil.getString("system_please_input")
                            + I18nUtil.getString("change_pwd_field_oldpwd"));
        }

        String trimmedPassword = newPassword.trim();
        int passwordLength = trimmedPassword.length();

        if (passwordLength < MIN_PASSWORD_LENGTH || passwordLength > MAX_PASSWORD_LENGTH) {
            return Response.ofFail(I18nUtil.getString("system_lengh_limit") + "[4-20]");
        }

        return Response.ofSuccess();
    }
}
