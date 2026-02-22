package com.abyss.orth.admin.model;

import lombok.Getter;
import lombok.Setter;

/**
 * User account entity for authentication and authorization.
 *
 * <p>Represents system users with role-based access control (RBAC) and executor group permissions.
 */
@Getter
@Setter
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
}
