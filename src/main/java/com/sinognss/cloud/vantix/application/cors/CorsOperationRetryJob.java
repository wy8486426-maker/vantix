package com.sinognss.cloud.vantix.application.cors;

import com.sinognss.cloud.vantix.config.CorsOperationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "vantix.cors-operation", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class CorsOperationRetryJob {
    private static final Logger log = LoggerFactory.getLogger(CorsOperationRetryJob.class);

    private final CorsOperationClaimService claimService;
    private final CorsOperationProcessor processor;
    private final CorsOperationProperties properties;

    public CorsOperationRetryJob(CorsOperationClaimService claimService,
                                 CorsOperationProcessor processor,
                                 CorsOperationProperties properties) {
        this.claimService = claimService;
        this.processor = processor;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${vantix.cors-operation.poll-interval:5s}")
    public void poll() {
        try {
            claimService.recoverStaleClaims();
        } catch (RuntimeException exception) {
            log.error("Failed to recover stale CORS operations", exception);
        }
        for (Long operationId : claimService.findDueOperationIds(properties.getWorkerBatchSize())) {
            try {
                processor.process(operationId);
            } catch (RuntimeException exception) {
                log.error("CORS operation processing failed for id={}", operationId, exception);
            }
        }
    }
}
