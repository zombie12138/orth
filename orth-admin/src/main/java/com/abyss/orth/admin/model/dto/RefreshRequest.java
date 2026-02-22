package com.abyss.orth.admin.model.dto;

import lombok.Data;

/** Refresh token request DTO. */
@Data
public class RefreshRequest {

    private String refreshToken;
}
