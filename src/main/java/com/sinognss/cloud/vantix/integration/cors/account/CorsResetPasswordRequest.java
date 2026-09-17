package com.sinognss.cloud.vantix.integration.cors.account;

/** Request body for POST /BaseUser/userInfo/resetPass. */
public record CorsResetPasswordRequest(Long id) {
    public CorsResetPasswordRequest {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
    }
}
