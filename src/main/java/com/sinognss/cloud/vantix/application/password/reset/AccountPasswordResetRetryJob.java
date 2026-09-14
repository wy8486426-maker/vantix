package com.sinognss.cloud.vantix.application.password.reset;

import com.sinognss.cloud.vantix.config.CorsAccountPasswordProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class AccountPasswordResetRetryJob {
    private static final Logger log = LoggerFactory.getLogger(AccountPasswordResetRetryJob.class);

    private final AccountPasswordResetClaimService claimService;
    private final AccountPasswordResetProcessor processor;
    private final CorsAccountPasswordProperties properties;
    private final AtomicBoolean running = new AtomicBoolean();

    public AccountPasswordResetRetryJob(AccountPasswordResetClaimService claimService,
                                        AccountPasswordResetProcessor processor,
                                        CorsAccountPasswordProperties properties) {
        this.claimService = claimService;
        this.processor = processor;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${vantix.cors.password.poll-interval:5s}")
    public void poll() {
        if (!running.compareAndSet(false, true)) {
            log.warn("Password reset poll skipped because the previous run is still active");
            return;
        }
        try {
            claimService.recoverStaleClaims();
            List<Long> due = claimService.findDueOperationIds(properties.getWorkerBatchSize());
            if (due == null) {
                return;
            }
            for (Long operationId : due) {
                if (operationId == null) {
                    continue;
                }
                try {
                    processor.process(operationId);
                } catch (RuntimeException exception) {
                    log.error("Password reset operation processing failed; operationId={} errorCode={}",
                            operationId, exception.getClass().getSimpleName());
                }
            }
        } catch (RuntimeException exception) {
            log.error("Password reset poll failed; errorCode={}", exception.getClass().getSimpleName());
        } finally {
            running.set(false);
        }
    }
}
