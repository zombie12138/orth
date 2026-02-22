package com.abyss.orth.admin.model.dto;

import lombok.Data;

/** Login request DTO containing user credentials. */
@Data
public class LoginRequest {

    private String username;
    private String password;
}
