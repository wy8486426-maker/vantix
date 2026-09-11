package com.sinognss.cloud.vantix.common.user;

public record OperatorIdentity(Long userId, String userName) {
    public static OperatorIdentity system() {
        return new OperatorIdentity(null, "system");
    }
}
