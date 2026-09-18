package com.sinognss.cloud.vantix.integration.cors.account;

/** Gateway for the confirmed CORS password operations. */
public interface CorsPasswordGateway {
    CorsPasswordResult resetPassword(CorsResetPasswordRequest request);

    CorsPasswordResult customPassword(CorsCustomPasswordRequest request);
}
