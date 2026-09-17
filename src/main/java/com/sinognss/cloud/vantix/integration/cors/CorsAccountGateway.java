package com.sinognss.cloud.vantix.integration.cors;

public interface CorsAccountGateway {
    CorsBatchResult createBatch(CorsBatchCreateRequest request);
}
