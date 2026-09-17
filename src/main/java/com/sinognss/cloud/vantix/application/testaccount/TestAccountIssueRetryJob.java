package com.sinognss.cloud.vantix.application.testaccount;

import com.sinognss.cloud.vantix.config.CorsOperationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "vantix.cors-operation", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TestAccountIssueRetryJob {
    private static final Logger log = LoggerFactory.getLogger(TestAccountIssueRetryJob.class);
    private final TestAccountIssueClaimService claimService;
    private final TestAccountIssueProcessor processor;
    private final CorsOperationProperties properties;

    public TestAccountIssueRetryJob(TestAccountIssueClaimService claimService,
                                    TestAccountIssueProcessor processor,
                                    CorsOperationProperties properties) {
        this.claimService = claimService;
        this.processor = processor;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${vantix.cors-operation.poll-interval:PT5S}")
    public void poll() {
        try {
            claimService.recoverStaleClaims();
        } catch (RuntimeException exception) {
            log.error("Failed to recover test account issue CORS operations", exception);
        }
        for (Long operationId : claimService.findDueOperationIds(properties.getWorkerBatchSize())) {
            try {
                processor.process(operationId);
            } catch (RuntimeException exception) {
                log.error("Test account issue CORS operation processing failed for id={}", operationId, exception);
            }
        }
    }
}
