package com.sinognss.cloud.vantix.integration.cors.account;

/** Gateway for the three confirmed CORS account side effects. */
public interface CorsPasswordGateway {
    CorsPasswordResult resetPassword(CorsResetPasswordRequest request);

    CorsPasswordResult customPassword(CorsCustomPasswordRequest request);
}
