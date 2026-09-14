package com.sinognss.cloud.vantix.integration.cors.account;

public interface CorsForceActivationGateway {
    CorsForceActivationResult forceActivate(CorsForceActivationRequest request);

    CorsForceActivationResult queryForceActivation(String requestId);
}
