package com.sinognss.cloud.vantix.application.cors.account;

import com.sinognss.cloud.vantix.config.CorsForceActivationProperties;
import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class AccountForceActivationJob {
    private static final Logger log = LoggerFactory.getLogger(AccountForceActivationJob.class);

    private final ServiceAccountMapper accountMapper;
    private final AccountStatusReconcileService reconcileService;
    private final AccountForceActivationReserveService reserveService;
    private final CorsForceActivationProperties properties;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean();

    public AccountForceActivationJob(ServiceAccountMapper accountMapper,
                                     AccountStatusReconcileService reconcileService,
                                     AccountForceActivationReserveService reserveService,
                                     CorsForceActivationProperties properties,
                                     Clock clock) {
        this.accountMapper = accountMapper;
        this.reconcileService = reconcileService;
        this.reserveService = reserveService;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${vantix.cors.force-activation.scan-interval:1m}")
    public void scan() {
        if (!running.compareAndSet(false, true)) {
            log.warn("Force-activation scan skipped because the previous run is still active");
            return;
        }
        try {
            LocalDateTime now = LocalDateTime.now(clock);
            List<Long> candidates = accountMapper.selectDueForceActivationCandidateIds(
                    now, properties.getCandidateBatchSize());
            if (candidates == null) {
                return;
            }
            for (Long id : candidates) {
                if (id != null) {
                    processCandidate(id);
                }
            }
        } catch (RuntimeException exception) {
            log.error("Force-activation candidate scan failed; errorCode={}",
                    exception.getClass().getSimpleName());
        } finally {
            running.set(false);
        }
    }

    private void processCandidate(Long serviceAccountId) {
        try {
            AccountStatusReconcileOutcome preflight = reconcileService.reconcileOne(serviceAccountId);
            if (preflight != AccountStatusReconcileOutcome.UPDATED
                    && preflight != AccountStatusReconcileOutcome.IDEMPOTENT_NOOP) {
                return;
            }

            ServiceAccount refreshed = accountMapper.selectById(serviceAccountId);
            LocalDateTime now = LocalDateTime.now(clock);
            if (!AccountForceActivationConstants.isEligibleAccount(refreshed, now)) {
                return;
            }
            AccountForceActivationReserveOutcome outcome = reserveService.reserve(serviceAccountId);
            if (outcome == AccountForceActivationReserveOutcome.CONFLICT) {
                log.error("Force-activation reservation identity conflict; serviceAccountId={} outcome=MANUAL_REVIEW",
                        serviceAccountId);
            }
        } catch (RuntimeException exception) {
            log.warn("Force-activation candidate processing failed; serviceAccountId={} outcome=UNKNOWN errorCode={}",
                    serviceAccountId, exception.getClass().getSimpleName());
        }
    }
}
