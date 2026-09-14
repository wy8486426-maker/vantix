package com.sinognss.cloud.vantix.integration.cors.account;

/**
 * Capability port for renewing a CORS account. Implementations must preserve the
 * supplied requestId across retries so a repeated renew call is idempotent.
 */
public interface CorsAccountRenewalGateway {
    CorsAccountRenewalResult renew(CorsAccountRenewalRequest request);

    CorsAccountRenewalResult queryRenewal(String requestId);
}
