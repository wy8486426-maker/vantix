package com.sinognss.cloud.vantix.application.cors.account;

import com.sinognss.cloud.vantix.config.CorsForceActivationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class AccountForceActivationRetryJob {
    private static final Logger log = LoggerFactory.getLogger(AccountForceActivationRetryJob.class);

    private final AccountForceActivationClaimService claimService;
    private final AccountForceActivationProcessor processor;
    private final CorsForceActivationProperties properties;
    private final AtomicBoolean running = new AtomicBoolean();

    public AccountForceActivationRetryJob(AccountForceActivationClaimService claimService,
                                          AccountForceActivationProcessor processor,
                                          CorsForceActivationProperties properties) {
        this.claimService = claimService;
        this.processor = processor;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${vantix.cors.force-activation.poll-interval:5s}")
    public void poll() {
        if (!running.compareAndSet(false, true)) {
            log.warn("Force-activation retry poll skipped because the previous run is still active");
            return;
        }
        try {
            claimService.recoverStaleClaims();
            List<Long> due = claimService.findDueOperationIds(properties.getWorkerBatchSize());
            if (due == null) {
                return;
            }
            for (Long operationId : due) {
                try {
                    processor.process(operationId);
                } catch (RuntimeException exception) {
                    log.error("Force-activation operation processing failed; operationId={} errorCode={}",
                            operationId, exception.getClass().getSimpleName());
                }
            }
        } catch (RuntimeException exception) {
            log.error("Force-activation retry poll failed; errorCode={}",
                    exception.getClass().getSimpleName());
        } finally {
            running.set(false);
        }
    }
}
