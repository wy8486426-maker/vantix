package com.sinognss.cloud.vantix.integration.cors;

/** Account identity returned by CORS /userInfo/add. */
public record CorsCreatedAccount(Long id, String name) {
}
