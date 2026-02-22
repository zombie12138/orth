package com.abyss.orth.admin.model.dto;

import com.abyss.orth.admin.web.security.JwtUserInfo;

import lombok.Data;

/** Login response DTO containing JWT tokens and user information. */
@Data
public class LoginResponse {

    private String accessToken;
    private String refreshToken;
    private long expiresIn;
    private JwtUserInfo userInfo;
}
