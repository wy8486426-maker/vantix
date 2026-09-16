package com.sinognss.cloud.vantix.application.company;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(prefix = "vantix.company-sync", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class DealerCompanySyncScheduler {
    private static final Logger log = LoggerFactory.getLogger(DealerCompanySyncScheduler.class);

    private final DealerCompanySyncService syncService;
    private final AtomicBoolean running = new AtomicBoolean();

    public DealerCompanySyncScheduler(DealerCompanySyncService syncService) {
        this.syncService = syncService;
    }

    @Scheduled(cron = "${vantix.company-sync.cron:0 0 */6 * * ?}",
            zone = "${vantix.company-sync.zone:Asia/Shanghai}")
    public void sync() {
        if (!running.compareAndSet(false, true)) {
            log.warn("Dealer company sync skipped because the previous run is still active");
            return;
        }
        try {
            DealerCompanySyncService.SyncSummary summary = syncService.syncAllCompanies();
            log.info("Dealer company sync completed; pages={} companies={}",
                    summary.pageCount(), summary.companyCount());
        } catch (RuntimeException exception) {
            log.error("Dealer company sync failed; errorType={}",
                    exception.getClass().getSimpleName(), exception);
        } finally {
            running.set(false);
        }
    }
}
