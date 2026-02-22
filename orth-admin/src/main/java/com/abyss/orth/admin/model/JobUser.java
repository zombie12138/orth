package com.abyss.orth.admin.model;

/**
 * User account entity for authentication and authorization.
 *
 * <p>Represents system users with role-based access control (RBAC) and executor group permissions.
 */
public class JobUser {

    /** User role: Normal user with limited permissions */
    public static final int ROLE_USER = 0;

    /** User role: Administrator with full system access */
    public static final int ROLE_ADMIN = 1;

    private int id;
    private String username; // Account username
    private String password; // Hashed password
    private String token; // Login session token
    private int role; // User role (ROLE_USER or ROLE_ADMIN)
    private String permission; // Executor group IDs (comma-separated)

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
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
