package com.sinognss.cloud.vantix.integration.cors.account;

/** Capability port for CORS password operations. No production HTTP adapter is defined yet. */
public interface CorsAccountPasswordGateway {
    CorsPasswordRevealResult revealPassword(CorsPasswordRevealRequest request);

    CorsPasswordResetResult resetPassword(CorsPasswordResetRequest request);

    CorsPasswordResetResult queryPasswordReset(String requestId);
}
