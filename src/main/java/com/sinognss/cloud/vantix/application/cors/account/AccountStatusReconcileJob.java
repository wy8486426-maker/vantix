package com.sinognss.cloud.vantix.application.cors.account;

import com.sinognss.cloud.vantix.config.CorsAccountStatusSyncProperties;
import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class AccountStatusReconcileJob {
    private static final Logger log = LoggerFactory.getLogger(AccountStatusReconcileJob.class);

    private final ServiceAccountMapper accountMapper;
    private final AccountStatusReconcileService reconcileService;
    private final CorsAccountStatusSyncProperties properties;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean();

    public AccountStatusReconcileJob(ServiceAccountMapper accountMapper,
                                     AccountStatusReconcileService reconcileService,
                                     CorsAccountStatusSyncProperties properties,
                                     Clock clock) {
        this.accountMapper = accountMapper;
        this.reconcileService = reconcileService;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${vantix.cors.account-status-sync.fixed-delay:1m}")
    public void reconcile() {
        if (!running.compareAndSet(false, true)) {
            log.warn("Account status reconciliation skipped because the previous run is still active");
            return;
        }
        try {
            LocalDateTime now = LocalDateTime.now(clock);
            int batchSize = properties.getBatchSize();
            Set<Long> candidates = new LinkedHashSet<>();
            append(accountMapper.selectDueWaitingActivationIds(now, batchSize), candidates, batchSize);
            int remaining = batchSize - candidates.size();
            if (remaining > 0) {
                append(accountMapper.selectDueOtherIds(now, remaining), candidates, batchSize);
            }
            for (Long id : candidates) {
                try {
                    reconcileService.reconcileOne(id);
                } catch (RuntimeException exception) {
                    log.warn("Account status reconciliation failed; serviceAccountId={} corsAccountId={} errorCode={}",
                            id, findCorsAccountId(id), exception.getClass().getSimpleName());
                }
            }
        } catch (RuntimeException exception) {
            log.error("Account status reconciliation batch failed; errorCode={}",
                    exception.getClass().getSimpleName());
        } finally {
            running.set(false);
        }
    }

    private static void append(List<Long> ids, Set<Long> candidates, int limit) {
        if (ids == null) {
            return;
        }
        for (Long id : ids) {
            if (id != null && candidates.size() < limit) {
                candidates.add(id);
            }
        }
    }

    private String findCorsAccountId(Long serviceAccountId) {
        try {
            ServiceAccount account = accountMapper.selectById(serviceAccountId);
            return account == null ? null : account.getCorsAccountId();
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
