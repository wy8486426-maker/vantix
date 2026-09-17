package com.sinognss.cloud.vantix.integration.cors.account;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/** Request body for POST /BaseUser/userInfo/customPass. */
public final class CorsCustomPasswordRequest {
    @JsonProperty("id")
    private final Long id;
    @JsonProperty("password")
    private final String password;

    public CorsCustomPasswordRequest(Long id, String password) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (password == null || password.isBlank() || password.length() > 4096) {
            throw new IllegalArgumentException("password is invalid");
        }
        this.id = id;
        this.password = password;
    }

    public Long id() { return id; }
    public String password() { return password; }

    @Override
    public String toString() {
        return "CorsCustomPasswordRequest[id=" + id + ", password=REDACTED]";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CorsCustomPasswordRequest that)) return false;
        return Objects.equals(id, that.id) && Objects.equals(password, that.password);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, password);
    }
}
