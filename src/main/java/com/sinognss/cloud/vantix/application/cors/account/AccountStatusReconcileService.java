package com.sinognss.cloud.vantix.application.cors.account;

import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountQueryOutcome;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountStatusGateway;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountStatusResult;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

public class AccountStatusReconcileService {
    private static final Logger log = LoggerFactory.getLogger(AccountStatusReconcileService.class);
    private static final Pattern SAFE_ERROR_CODE = Pattern.compile("[A-Za-z0-9_.-]{1,64}");

    private final ServiceAccountMapper accountMapper;
    private final CorsAccountStatusGateway gateway;
    private final CorsAccountStateApplyService applyService;
    private final AccountStatusSyncScheduleService scheduleService;

    public AccountStatusReconcileService(ServiceAccountMapper accountMapper,
                                         CorsAccountStatusGateway gateway,
                                         CorsAccountStateApplyService applyService,
                                         AccountStatusSyncScheduleService scheduleService) {
        this.accountMapper = accountMapper;
        this.gateway = gateway;
        this.applyService = applyService;
        this.scheduleService = scheduleService;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AccountStatusReconcileOutcome reconcileOne(Long serviceAccountId) {
        if (serviceAccountId == null) {
            return AccountStatusReconcileOutcome.SKIPPED;
        }
        ServiceAccount local = accountMapper.selectById(serviceAccountId);
        if (local == null || blank(local.getCorsAccountId()) || local.getVersion() == null) {
            return AccountStatusReconcileOutcome.SKIPPED;
        }

        CorsAccountStatusResult result;
        try {
            result = gateway.getAccount(local.getCorsAccountId());
        } catch (RuntimeException exception) {
            log.warn("CORS account status query failed; serviceAccountId={} corsAccountId={} errorCode={}",
                    serviceAccountId, local.getCorsAccountId(), exception.getClass().getSimpleName());
            return markFailure(local, AccountStatusReconcileOutcome.UNKNOWN);
        }
        if (result == null) {
            log.warn("CORS account status query returned no result; serviceAccountId={} corsAccountId={} errorCode={}",
                    serviceAccountId, local.getCorsAccountId(), "MALFORMED_RESPONSE");
            return markFailure(local, AccountStatusReconcileOutcome.UNKNOWN);
        }

        if (result.outcome() == CorsAccountQueryOutcome.NOT_FOUND) {
            log.warn("CORS account is missing; serviceAccountId={} corsAccountId={} errorCode={}",
                    serviceAccountId, local.getCorsAccountId(), safeErrorCode(result.errorCode(), "NOT_FOUND"));
            return markFailure(local, AccountStatusReconcileOutcome.NOT_FOUND);
        }
        if (result.outcome() == CorsAccountQueryOutcome.UNKNOWN) {
            log.warn("CORS account status is unknown; serviceAccountId={} corsAccountId={} errorCode={}",
                    serviceAccountId, local.getCorsAccountId(), safeErrorCode(result.errorCode(), "UNKNOWN"));
            return markFailure(local, AccountStatusReconcileOutcome.UNKNOWN);
        }
        if (result.snapshot() == null) {
            log.warn("CORS account status response is malformed; serviceAccountId={} corsAccountId={} errorCode={}",
                    serviceAccountId, local.getCorsAccountId(), "MALFORMED_RESPONSE");
            return markFailure(local, AccountStatusReconcileOutcome.UNKNOWN);
        }

        CorsAccountStateApplyOutcome applyOutcome;
        try {
            applyOutcome = applyService.apply(local, result.snapshot(), scheduleService.successSchedule());
        } catch (RuntimeException exception) {
            log.warn("CORS account snapshot apply failed; serviceAccountId={} corsAccountId={} errorCode={}",
                    serviceAccountId, local.getCorsAccountId(), exception.getClass().getSimpleName());
            return markFailure(local, AccountStatusReconcileOutcome.UNKNOWN);
        }
        if (applyOutcome == CorsAccountStateApplyOutcome.INCONSISTENT) {
            log.warn("CORS account snapshot is inconsistent; serviceAccountId={} corsAccountId={} errorCode={}",
                    serviceAccountId, local.getCorsAccountId(), "SNAPSHOT_INCONSISTENT");
            return markFailure(local, AccountStatusReconcileOutcome.INCONSISTENT);
        }
        return AccountStatusReconcileOutcome.valueOf(applyOutcome.name());
    }

    private AccountStatusReconcileOutcome markFailure(ServiceAccount local,
                                                       AccountStatusReconcileOutcome failureOutcome) {
        return scheduleService.markFailure(local)
                ? failureOutcome : AccountStatusReconcileOutcome.CONCURRENT_MODIFICATION;
    }

    private static String safeErrorCode(String value, String fallback) {
        return value != null && SAFE_ERROR_CODE.matcher(value).matches() ? value : fallback;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
