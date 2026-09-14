package com.sinognss.cloud.vantix.application.renewal;

import com.sinognss.cloud.vantix.config.CorsAccountRenewalProperties;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.domain.renewal.AccountRenewal;
import com.sinognss.cloud.vantix.domain.renewal.AccountRenewalStatus;
import com.sinognss.cloud.vantix.infrastructure.mapper.AccountRenewalMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.CorsOperationMapper;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

public class AccountRenewalClaimService {
    private static final int STALE_BATCH_SIZE = 100;
    private static final int MAX_BATCH_SIZE = 500;

    private final CorsOperationMapper operationMapper;
    private final AccountRenewalMapper renewalMapper;
    private final AccountRenewalStateService stateService;
    private final CorsAccountRenewalProperties properties;
    private final Clock clock;

    public AccountRenewalClaimService(CorsOperationMapper operationMapper,
                                      AccountRenewalMapper renewalMapper,
                                      AccountRenewalStateService stateService,
                                      CorsAccountRenewalProperties properties,
                                      Clock clock) {
        this.operationMapper = operationMapper;
        this.renewalMapper = renewalMapper;
        this.stateService = stateService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public ClaimedAccountRenewal claim(Long operationId) {
        if (operationId == null) {
            return null;
        }
        CorsOperation current = operationMapper.selectById(operationId);
        if (!isRenewalOperation(current) || current.getVersion() == null
                || !(AccountRenewalConstants.PENDING.equals(current.getStatus())
                || AccountRenewalConstants.RETRY_WAIT.equals(current.getStatus()))) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (AccountRenewalConstants.RETRY_WAIT.equals(current.getStatus())
                && current.getNextRetryAt() != null && current.getNextRetryAt().isAfter(now)) {
            return null;
        }
        boolean queryFirst = AccountRenewalConstants.RETRY_WAIT.equals(current.getStatus());
        if (operationMapper.claim(current.getId(), current.getStatus(), current.getVersion(), now) != 1) {
            return null;
        }

        CorsOperation claimed = operationMapper.selectByIdForUpdate(operationId);
        if (!isRenewalOperation(claimed) || !AccountRenewalConstants.CLAIMED.equals(claimed.getStatus())) {
            return null;
        }
        AccountRenewal renewal = claimed.getBizId() == null ? null
                : renewalMapper.selectByIdForUpdate(claimed.getBizId());
        return new ClaimedAccountRenewal(claimed, renewal, queryFirst);
    }

    public List<Long> findDueOperationIds(int limit) {
        if (limit < 1) {
            return List.of();
        }
        return operationMapper.selectDueRenewalIds(LocalDateTime.now(clock), Math.min(limit, MAX_BATCH_SIZE));
    }

    @Transactional
    public int recoverStaleClaims() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<CorsOperation> stale = operationMapper.selectStaleRenewalClaimed(
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

    private static boolean isRenewalOperation(CorsOperation operation) {
        return operation != null
                && AccountRenewalConstants.OPERATION_TYPE.equals(operation.getOperationType())
                && AccountRenewalConstants.BIZ_TYPE.equals(operation.getBizType())
                && operation.getBizId() != null
                && operation.getServiceAccountId() != null
                && operation.getRequestId() != null
                && !operation.getRequestId().isBlank()
                && operation.getRequestId().length() <= 128;
    }
}
