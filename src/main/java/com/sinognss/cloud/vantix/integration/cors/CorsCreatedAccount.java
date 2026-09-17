package com.sinognss.cloud.vantix.integration.cors;

/** Account identity returned by CORS /BaseUser/userInfo/add. */
public record CorsCreatedAccount(Long id, String name) {
}
