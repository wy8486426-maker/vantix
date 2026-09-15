package com.sinognss.cloud.vantix.application.cors.account;

import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import com.sinognss.cloud.vantix.integration.cors.account.CorsUserInfoRepository;
import com.sinognss.cloud.vantix.integration.cors.account.CorsUserInfoSnapshotMapper;
import com.sinognss.cloud.vantix.integration.cors.account.CorsUserInfoStatusRow;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class CorsAccountRealtimeRefreshService {
    private static final Logger log = LoggerFactory.getLogger(CorsAccountRealtimeRefreshService.class);
    private final ServiceAccountMapper accountMapper;
    private final CorsUserInfoRepository repository;
    private final CorsUserInfoSnapshotMapper snapshotMapper;
    private final CorsAccountStateApplyService applyService;
    private final AccountStatusSyncScheduleService scheduleService;

    public CorsAccountRealtimeRefreshService(ServiceAccountMapper accountMapper,
                                             CorsUserInfoRepository repository,
                                             CorsUserInfoSnapshotMapper snapshotMapper,
                                             CorsAccountStateApplyService applyService,
                                             AccountStatusSyncScheduleService scheduleService) {
        this.accountMapper = accountMapper;
        this.repository = repository;
        this.snapshotMapper = snapshotMapper;
        this.applyService = applyService;
        this.scheduleService = scheduleService;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CorsAccountRealtimeRefreshOutcome refresh(String userName, String action) {
        ServiceAccount local = accountMapper.selectForCorsRealtimeRefreshByAccount(userName);
        if (local == null) {
            log.warn("CORS realtime refresh skipped; errorCode=LOCAL_ACCOUNT_NOT_FOUND");
            return CorsAccountRealtimeRefreshOutcome.LOCAL_NOT_FOUND;
        }
        if (!userName.equals(local.getAccount())) {
            log.warn("CORS realtime refresh skipped; serviceAccountId={} errorCode=LOCAL_ACCOUNT_IDENTITY_MISMATCH",
                    local.getId());
            return CorsAccountRealtimeRefreshOutcome.INCONSISTENT;
        }
        long corsAccountId;
        try {
            if (local.getCorsAccountId() == null || !local.getCorsAccountId().matches("[1-9][0-9]*")) {
                throw new NumberFormatException();
            }
            corsAccountId = Long.parseLong(local.getCorsAccountId());
        } catch (RuntimeException invalidId) {
            log.warn("CORS realtime refresh skipped; serviceAccountId={} errorCode=INVALID_CORS_ACCOUNT_ID",
                    local.getId());
            return CorsAccountRealtimeRefreshOutcome.INCONSISTENT;
        }

        CorsUserInfoStatusRow row;
        try {
            row = repository.findById(corsAccountId);
        } catch (RuntimeException databaseFailure) {
            log.warn("CORS realtime refresh failed; serviceAccountId={} corsAccountId={} action={} errorCode=CORS_DB_UNAVAILABLE",
                    local.getId(), corsAccountId, action);
            return CorsAccountRealtimeRefreshOutcome.REMOTE_UNAVAILABLE;
        }
        if (row == null) {
            log.warn("CORS realtime refresh skipped; serviceAccountId={} corsAccountId={} action={} errorCode=CORS_ACCOUNT_NOT_FOUND",
                    local.getId(), corsAccountId, action);
            return CorsAccountRealtimeRefreshOutcome.REMOTE_NOT_FOUND;
        }
        if (row.id() != corsAccountId || !userName.equals(row.name()) || !userName.equals(local.getAccount())) {
            log.warn("CORS realtime refresh skipped; serviceAccountId={} corsAccountId={} action={} errorCode=REMOTE_IDENTITY_MISMATCH",
                    local.getId(), corsAccountId, action);
            return CorsAccountRealtimeRefreshOutcome.INCONSISTENT;
        }
        try {
            CorsAccountSnapshot snapshot = snapshotMapper.map(row);
            return CorsAccountRealtimeRefreshOutcome.valueOf(
                    applyService.apply(local, snapshot, scheduleService.successSchedule()).name());
        } catch (RuntimeException invalidRemoteState) {
            log.warn("CORS realtime refresh failed; serviceAccountId={} corsAccountId={} action={} errorCode=INVALID_REMOTE_STATE",
                    local.getId(), corsAccountId, action);
            return CorsAccountRealtimeRefreshOutcome.INCONSISTENT;
        }
    }
}
