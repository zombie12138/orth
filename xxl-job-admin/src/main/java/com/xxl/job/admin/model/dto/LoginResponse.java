package com.xxl.job.admin.model.dto;

import com.xxl.job.admin.web.security.JwtUserInfo;

/** Login response DTO containing JWT tokens and user information. */
public class LoginResponse {

    private String accessToken;
    private String refreshToken;
    private long expiresIn;
    private JwtUserInfo userInfo;

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public JwtUserInfo getUserInfo() {
        return userInfo;
    }

    public void setUserInfo(JwtUserInfo userInfo) {
        this.userInfo = userInfo;
    }
}
