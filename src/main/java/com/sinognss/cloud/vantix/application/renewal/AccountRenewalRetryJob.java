package com.sinognss.cloud.vantix.application.renewal;

import com.sinognss.cloud.vantix.config.CorsAccountRenewalProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class AccountRenewalRetryJob {
    private static final Logger log = LoggerFactory.getLogger(AccountRenewalRetryJob.class);

    private final AccountRenewalClaimService claimService;
    private final AccountRenewalProcessor processor;
    private final CorsAccountRenewalProperties properties;
    private final AtomicBoolean running = new AtomicBoolean();

    public AccountRenewalRetryJob(AccountRenewalClaimService claimService,
                                  AccountRenewalProcessor processor,
                                  CorsAccountRenewalProperties properties) {
        this.claimService = claimService;
        this.processor = processor;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${vantix.cors.renewal.poll-interval:5s}")
    public void poll() {
        if (!running.compareAndSet(false, true)) {
            log.warn("Account renewal poll skipped because the previous run is still active");
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
                    log.error("Account renewal operation processing failed; operationId={} errorCode={}",
                            operationId, exception.getClass().getSimpleName());
                }
            }
        } catch (RuntimeException exception) {
            log.error("Account renewal poll failed; errorCode={}", exception.getClass().getSimpleName());
        } finally {
            running.set(false);
        }
    }
}
