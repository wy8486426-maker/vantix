package com.sinognss.cloud.vantix.application.password.reset;

import com.sinognss.cloud.vantix.config.CorsAccountPasswordProperties;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.domain.password.AccountPasswordAction;
import com.sinognss.cloud.vantix.infrastructure.mapper.AccountPasswordActionMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.CorsOperationMapper;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

public class AccountPasswordResetClaimService {
    private static final int STALE_BATCH_SIZE = 100;
    private static final int MAX_BATCH_SIZE = 500;

    private final CorsOperationMapper operationMapper;
    private final AccountPasswordActionMapper actionMapper;
    private final AccountPasswordResetStateService stateService;
    private final CorsAccountPasswordProperties properties;
    private final Clock clock;

    public AccountPasswordResetClaimService(CorsOperationMapper operationMapper,
                                           AccountPasswordActionMapper actionMapper,
                                           AccountPasswordResetStateService stateService,
                                           CorsAccountPasswordProperties properties,
                                           Clock clock) {
        this.operationMapper = operationMapper;
        this.actionMapper = actionMapper;
        this.stateService = stateService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public ClaimedAccountPasswordReset claim(Long operationId) {
        if (operationId == null) {
            return null;
        }
        CorsOperation current = operationMapper.selectById(operationId);
        if (!isResetOperation(current)
                || !(AccountPasswordResetConstants.PENDING.equals(current.getStatus())
                || AccountPasswordResetConstants.RETRY_WAIT.equals(current.getStatus()))) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (AccountPasswordResetConstants.RETRY_WAIT.equals(current.getStatus())
                && current.getNextRetryAt() != null && current.getNextRetryAt().isAfter(now)) {
            return null;
        }
        boolean queryFirst = AccountPasswordResetConstants.RETRY_WAIT.equals(current.getStatus());
        if (operationMapper.claim(current.getId(), current.getStatus(), current.getVersion(), now) != 1) {
            return null;
        }

        CorsOperation claimed = operationMapper.selectByIdForUpdate(operationId);
        if (!AccountPasswordResetConstants.isOperation(claimed, current.getBizId(), current.getServiceAccountId())
                || !AccountPasswordResetConstants.CLAIMED.equals(claimed.getStatus())) {
            return null;
        }
        AccountPasswordAction action = actionMapper.selectByIdForUpdate(claimed.getBizId());
        return new ClaimedAccountPasswordReset(claimed, action, queryFirst);
    }

    public List<Long> findDueOperationIds(int limit) {
        if (limit < 1) {
            return List.of();
        }
        return operationMapper.selectDuePasswordResetIds(LocalDateTime.now(clock), Math.min(limit, MAX_BATCH_SIZE));
    }

    @Transactional
    public int recoverStaleClaims() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<CorsOperation> stale = operationMapper.selectStalePasswordResetClaimed(
                now.minus(properties.getClaimTimeout()), STALE_BATCH_SIZE);
        if (stale == null || stale.isEmpty()) {
            return 0;
        }
        int recovered = 0;
        for (CorsOperation operation : stale) {
            if (operation != null && stateService.recoverStaleClaim(operation)) {
                recovered++;
            }
        }
        return recovered;
    }

    private static boolean isResetOperation(CorsOperation operation) {
        return operation != null
                && AccountPasswordResetConstants.OPERATION_TYPE.equals(operation.getOperationType())
                && AccountPasswordResetConstants.BIZ_TYPE.equals(operation.getBizType())
                && operation.getBizId() != null
                && operation.getServiceAccountId() != null
                && operation.getRequestId() != null && !operation.getRequestId().isBlank()
                && operation.getRequestId().length() <= 128
                && operation.getVersion() != null;
    }
}
