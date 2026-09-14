package com.sinognss.cloud.vantix.application.cors.account;

import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.integration.cors.CorsOutcome;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountSnapshot;
import com.sinognss.cloud.vantix.integration.cors.account.CorsForceActivationResult;
import com.sinognss.cloud.vantix.infrastructure.mapper.CorsOperationMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

public class AccountForceActivationFinalizeService {
    private final CorsOperationMapper operationMapper;
    private final ServiceAccountMapper accountMapper;
    private final CorsAccountStateApplyService applyService;
    private final AccountStatusSyncScheduleService scheduleService;
    private final Clock clock;

    public AccountForceActivationFinalizeService(CorsOperationMapper operationMapper,
                                                  ServiceAccountMapper accountMapper,
                                                  CorsAccountStateApplyService applyService,
                                                  AccountStatusSyncScheduleService scheduleService,
                                                  Clock clock) {
        this.operationMapper = operationMapper;
        this.accountMapper = accountMapper;
        this.applyService = applyService;
        this.scheduleService = scheduleService;
        this.clock = clock;
    }

    @Transactional
    public void finalizeSuccess(Long operationId, Long expectedVersion,
                                CorsForceActivationResult result) {
        CorsOperation operation = operationMapper.selectById(operationId);
        if (operation == null || expectedVersion == null || operation.getVersion() == null
                || !expectedVersion.equals(operation.getVersion())
                || !AccountForceActivationConstants.CLAIMED.equals(operation.getStatus())
                || !AccountForceActivationConstants.isOperationForAccount(operation,
                operation.getServiceAccountId())) {
            throw new IllegalStateException("Claimed force-activation operation changed before finalize");
        }

        ServiceAccount account = accountMapper.selectByIdForUpdate(operation.getServiceAccountId());
        if (!AccountForceActivationConstants.hasIdentity(account, operation)
                || !isValidSuccess(operation, account, result)) {
            throw new IllegalStateException("Force-activation success response is inconsistent");
        }

        CorsAccountStateApplyOutcome applyOutcome = applyService.apply(
                account, result.account(), scheduleService.successSchedule());
        if (applyOutcome != CorsAccountStateApplyOutcome.UPDATED
                && applyOutcome != CorsAccountStateApplyOutcome.IDEMPOTENT_NOOP
                && applyOutcome != CorsAccountStateApplyOutcome.STALE_IGNORED) {
            throw new IllegalStateException("CORS snapshot could not be applied");
        }

        ServiceAccount finalized = accountMapper.selectById(operation.getServiceAccountId());
        if (!isLocallyActive(finalized)) {
            throw new IllegalStateException("Local account did not reach the active state");
        }
        if (operationMapper.markSucceeded(operation.getId(), operation.getVersion(),
                LocalDateTime.now(clock)) != 1) {
            throw new IllegalStateException("Force-activation operation could not be marked succeeded");
        }
    }

    private static boolean isValidSuccess(CorsOperation operation, ServiceAccount account,
                                          CorsForceActivationResult result) {
        if (result == null || result.outcome() != CorsOutcome.SUCCESS
                || !operation.getRequestId().equals(result.requestId())
                || result.account() == null) {
            return false;
        }
        CorsAccountSnapshot snapshot = result.account();
        return account.getCorsAccountId().equals(snapshot.accountId())
                && account.getAccount().equals(snapshot.account())
                && AccountForceActivationConstants.ACTIVE.equals(snapshot.activationStatus())
                && snapshot.activatedAt() != null
                && snapshot.expireAt() != null
                && !snapshot.activatedAt().isAfter(snapshot.expireAt())
                && snapshot.updatedAt() != null;
    }

    private static boolean isLocallyActive(ServiceAccount account) {
        return account != null
                && AccountForceActivationConstants.ACTIVE.equals(account.getCorsActivationStatus())
                && account.getActivatedAt() != null
                && account.getExpireAt() != null;
    }
}
