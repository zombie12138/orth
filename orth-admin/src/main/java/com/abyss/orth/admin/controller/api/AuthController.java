package com.abyss.orth.admin.controller.api;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.abyss.orth.admin.mapper.JobUserMapper;
import com.abyss.orth.admin.model.JobUser;
import com.abyss.orth.admin.model.dto.LoginRequest;
import com.abyss.orth.admin.model.dto.LoginResponse;
import com.abyss.orth.admin.model.dto.PasswordUpdateRequest;
import com.abyss.orth.admin.model.dto.RefreshRequest;
import com.abyss.orth.admin.util.I18nUtil;
import com.abyss.orth.admin.util.PasswordEncoderUtil;
import com.abyss.orth.admin.web.security.JwtProperties;
import com.abyss.orth.admin.web.security.JwtTokenProvider;
import com.abyss.orth.admin.web.security.JwtUserInfo;
import com.abyss.orth.admin.web.security.LoginRateLimiter;
import com.abyss.orth.admin.web.security.SecurityContext;
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

    @Resource private JobUserMapper jobUserMapper;
    @Resource private JwtTokenProvider jwtTokenProvider;
    @Resource private JwtProperties jwtProperties;
    @Resource private LoginRateLimiter loginRateLimiter;

    /**
     * Authenticates user and returns JWT tokens.
     *
     * <p>Supports transparent migration from legacy SHA256 hashes to BCrypt: if the stored password
     * is a SHA256 hash and the password matches, it is automatically re-hashed with BCrypt.
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

        String username = request.getUsername();

        // Rate limit check
        if (loginRateLimiter.isLockedOut(username)) {
            return Response.ofFail(I18nUtil.getString("login_rate_limit"));
        }

        JobUser user = jobUserMapper.loadByUserName(username);
        if (user == null) {
            loginRateLimiter.recordFailure(username);
            return Response.ofFail(I18nUtil.getString("login_param_unvalid"));
        }

        if (!verifyPassword(request.getPassword(), user)) {
            loginRateLimiter.recordFailure(username);
            return Response.ofFail(I18nUtil.getString("login_param_unvalid"));
        }

        loginRateLimiter.recordSuccess(username);

        JwtUserInfo userInfo =
                new JwtUserInfo(
                        user.getId(), user.getUsername(), user.getRole(), user.getPermission());

        String accessToken = jwtTokenProvider.generateAccessToken(userInfo);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userInfo);

        // Store refresh token hash in DB for validation (SHA256 is fine for token hashing)
        String refreshTokenHash = SHA256Tool.sha256(refreshToken);
        jobUserMapper.updateToken(user.getId(), refreshTokenHash);

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
        JobUser user = jobUserMapper.loadById(userInfo.getUserId());
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
        jobUserMapper.updateToken(user.getId(), newRefreshTokenHash);

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
            jobUserMapper.updateToken(userInfo.getUserId(), "");
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
        JobUser existingUser = jobUserMapper.loadByUserName(userInfo.getUsername());

        if (!verifyPassword(passwordRequest.getOldPassword(), existingUser)) {
            return Response.ofFail(
                    I18nUtil.getString("change_pwd_field_oldpwd")
                            + I18nUtil.getString("system_unvalid"));
        }

        existingUser.setPassword(PasswordEncoderUtil.encode(passwordRequest.getNewPassword()));
        jobUserMapper.update(existingUser);

        return Response.ofSuccess();
    }

    /**
     * Verifies a raw password against the stored hash, supporting both BCrypt and legacy SHA256. If
     * the stored hash is legacy SHA256 and matches, it is transparently migrated to BCrypt.
     */
    private boolean verifyPassword(String rawPassword, JobUser user) {
        String storedHash = user.getPassword();

        if (PasswordEncoderUtil.isLegacySha256(storedHash)) {
            // Legacy SHA256 path: verify, then migrate to BCrypt
            String sha256Hash = SHA256Tool.sha256(rawPassword);
            if (!sha256Hash.equals(storedHash)) {
                return false;
            }
            // Migrate to BCrypt on successful login
            String bcryptHash = PasswordEncoderUtil.encode(rawPassword);
            user.setPassword(bcryptHash);
            jobUserMapper.update(user);
            return true;
        }

        return PasswordEncoderUtil.matches(rawPassword, storedHash);
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
