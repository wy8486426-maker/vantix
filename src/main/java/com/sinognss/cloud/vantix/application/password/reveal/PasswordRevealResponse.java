package com.sinognss.cloud.vantix.application.password.reveal;

/** HTTP-only response value. Never store, log, or attach this object to asynchronous work. */
public final class PasswordRevealResponse {
    private final Long serviceAccountId;
    private final String account;
    private final String password;

    public PasswordRevealResponse(Long serviceAccountId, String account, String password) {
        this.serviceAccountId = serviceAccountId;
        this.account = account;
        this.password = password;
    }

    public Long getServiceAccountId() { return serviceAccountId; }
    public String getAccount() { return account; }
    public String getPassword() { return password; }

    @Override
    public String toString() {
        return "PasswordRevealResponse[serviceAccountId=" + serviceAccountId + ", password=REDACTED]";
    }
}
