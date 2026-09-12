package com.sinognss.cloud.vantix.application.servicecode.generation;

import com.sinognss.cloud.vantix.common.user.OperatorIdentity;

public enum IntegrationActor {
    B2B("B2B integration");

    private final String displayName;

    IntegrationActor(String displayName) {
        this.displayName = displayName;
    }

    public OperatorIdentity operatorIdentity() {
        return new OperatorIdentity(null, displayName);
    }
}