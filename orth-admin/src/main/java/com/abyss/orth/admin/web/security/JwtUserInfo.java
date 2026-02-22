package com.abyss.orth.admin.web.security;

/**
 * JWT user information extracted from token claims.
 *
 * <p>Replaces {@code LoginInfo} from xxl-sso-core as the authenticated user representation.
 */
public class JwtUserInfo {

    private int userId;
    private String username;
    private int role;
    private String permission;

    public JwtUserInfo() {}

    public JwtUserInfo(int userId, String username, int role, String permission) {
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.permission = permission;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public int getRole() {
        return role;
    }

    public void setRole(int role) {
        this.role = role;
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }
}
