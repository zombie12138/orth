package com.abyss.orth.admin.model.dto;

import lombok.Data;

/** Password update request DTO. */
@Data
public class PasswordUpdateRequest {

    private String oldPassword;
    private String newPassword;
}
