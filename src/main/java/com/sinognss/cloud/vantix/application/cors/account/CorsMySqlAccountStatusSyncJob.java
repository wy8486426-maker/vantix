package com.sinognss.cloud.vantix.application.cors.account;

import com.sinognss.cloud.vantix.config.CorsDbProperties;
import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import com.sinognss.cloud.vantix.integration.cors.account.CorsUserInfoRepository;
import com.sinognss.cloud.vantix.integration.cors.account.CorsUserInfoSnapshotMapper;
import com.sinognss.cloud.vantix.integration.cors.account.CorsUserInfoStatusRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class CorsMySqlAccountStatusSyncJob {
    private static final Logger log = LoggerFactory.getLogger(CorsMySqlAccountStatusSyncJob.class);
    private final ServiceAccountMapper accountMapper;
    private final CorsUserInfoRepository repository;
    private final CorsUserInfoSnapshotMapper snapshotMapper;
    private final CorsAccountStateApplyService applyService;
    private final AccountStatusSyncScheduleService scheduleService;
    private final CorsDbProperties properties;
    private final AtomicBoolean running = new AtomicBoolean();

    public CorsMySqlAccountStatusSyncJob(ServiceAccountMapper accountMapper,
                                         CorsUserInfoRepository repository,
                                         CorsUserInfoSnapshotMapper snapshotMapper,
                                         CorsAccountStateApplyService applyService,
                                         AccountStatusSyncScheduleService scheduleService,
                                         CorsDbProperties properties) {
        this.accountMapper = accountMapper;
        this.repository = repository;
        this.snapshotMapper = snapshotMapper;
        this.applyService = applyService;
        this.scheduleService = scheduleService;
        this.properties = properties;
    }

    @Scheduled(cron = "${vantix.cors-db.status-sync.cron:0 0 2 * * ?}",
            zone = "${vantix.cors-db.status-sync.zone:Asia/Shanghai}")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void sync() {
        if (!running.compareAndSet(false, true)) {
            log.warn("CORS MySQL account status sync skipped because the previous run is still active");
            return;
        }
        Summary summary = new Summary();
        try {
            long lastId = 0;
            int batchSize = properties.getStatusSync().getBatchSize();
            while (true) {
                List<ServiceAccount> candidates = accountMapper.selectCorsStatusSyncCandidatesAfterId(lastId, batchSize);
                if (candidates == null || candidates.isEmpty()) break;
                summary.scanned += candidates.size();
                lastId = candidates.get(candidates.size() - 1).getId();
                syncBatch(candidates, summary);
                if (candidates.size() < batchSize) break;
            }
        } catch (RuntimeException exception) {
            summary.failed++;
            log.error("CORS MySQL account status sync failed; errorCode={}",
                    exception.getClass().getSimpleName());
        } finally {
            log.info("CORS MySQL account status sync summary; scanned={} remoteFound={} updated={} "
                            + "idempotent={} staleIgnored={} missing={} inconsistent={} "
                            + "concurrentModification={} failed={}", summary.scanned, summary.remoteFound,
                    summary.updated, summary.idempotent, summary.staleIgnored, summary.missing,
                    summary.inconsistent, summary.concurrentModification, summary.failed);
            running.set(false);
        }
    }

    private void syncBatch(List<ServiceAccount> candidates, Summary summary) {
        Map<Long, Long> remoteIds = new HashMap<>();
        for (ServiceAccount candidate : candidates) {
            try {
                long id = Long.parseLong(candidate.getCorsAccountId());
                if (id <= 0) throw new NumberFormatException();
                remoteIds.put(candidate.getId(), id);
            } catch (RuntimeException invalidLocalId) {
                summary.inconsistent++;
            }
        }
        Map<Long, CorsUserInfoStatusRow> remoteRows = new HashMap<>();
        if (!remoteIds.isEmpty()) {
            List<CorsUserInfoStatusRow> rows = repository.findByIds(remoteIds.values());
            for (CorsUserInfoStatusRow row : rows) remoteRows.put(row.id(), row);
        }
        for (ServiceAccount candidate : candidates) {
            Long remoteId = remoteIds.get(candidate.getId());
            if (remoteId == null) continue;
            CorsUserInfoStatusRow row = remoteRows.get(remoteId);
            if (row == null) {
                summary.missing++;
                continue;
            }
            summary.remoteFound++;
            try {
                CorsAccountStateApplyOutcome outcome = applyService.apply(candidate, snapshotMapper.map(row),
                        scheduleService.successSchedule());
                switch (outcome) {
                    case UPDATED -> summary.updated++;
                    case IDEMPOTENT_NOOP -> summary.idempotent++;
                    case STALE_IGNORED -> summary.staleIgnored++;
                    case INCONSISTENT -> summary.inconsistent++;
                    case CONCURRENT_MODIFICATION -> summary.concurrentModification++;
                }
            } catch (RuntimeException invalidOrApplyFailure) {
                summary.failed++;
            }
        }
    }

    private static final class Summary {
        int scanned;
        int remoteFound;
        int updated;
        int idempotent;
        int staleIgnored;
        int missing;
        int inconsistent;
        int concurrentModification;
        int failed;
    }
}
