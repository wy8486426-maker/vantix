package com.sinognss.cloud.vantix.integration.cors.account;

/** In-memory secret holder. Never log or persist instances of this type. */
public final class PasswordRevealSecret {
    private final String password;

    public PasswordRevealSecret(String password) {
        this.password = password;
    }

    public String getPassword() {
        return password;
    }

    @Override
    public String toString() {
        return "PasswordRevealSecret[REDACTED]";
    }
}
