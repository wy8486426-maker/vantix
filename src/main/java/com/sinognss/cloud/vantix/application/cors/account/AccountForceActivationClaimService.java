package com.sinognss.cloud.vantix.application.cors.account;

import com.sinognss.cloud.vantix.config.CorsForceActivationProperties;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.infrastructure.mapper.CorsOperationMapper;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

public class AccountForceActivationClaimService {
    private static final int STALE_BATCH_SIZE = 100;

    private final CorsOperationMapper operationMapper;
    private final CorsForceActivationProperties properties;
    private final Clock clock;

    public AccountForceActivationClaimService(CorsOperationMapper operationMapper,
                                              CorsForceActivationProperties properties,
                                              Clock clock) {
        this.operationMapper = operationMapper;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public ClaimedAccountForceActivation claim(Long operationId) {
        CorsOperation current = operationMapper.selectById(operationId);
        if (!AccountForceActivationConstants.isOperationForAccount(current,
                current == null ? null : current.getServiceAccountId())
                || !(AccountForceActivationConstants.PENDING.equals(current.getStatus())
                || AccountForceActivationConstants.RETRY_WAIT.equals(current.getStatus()))
                || current.getVersion() == null) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (AccountForceActivationConstants.RETRY_WAIT.equals(current.getStatus())
                && current.getNextRetryAt() != null && current.getNextRetryAt().isAfter(now)) {
            return null;
        }
        boolean queryFirst = AccountForceActivationConstants.RETRY_WAIT.equals(current.getStatus());
        if (operationMapper.claim(current.getId(), current.getStatus(), current.getVersion(), now) != 1) {
            return null;
        }
        CorsOperation claimed = operationMapper.selectById(operationId);
        if (!AccountForceActivationConstants.isOperationForAccount(claimed,
                current.getServiceAccountId())
                || !AccountForceActivationConstants.CLAIMED.equals(claimed.getStatus())) {
            return null;
        }
        return new ClaimedAccountForceActivation(claimed, queryFirst);
    }

    public List<Long> findDueOperationIds(int limit) {
        if (limit < 1) {
            return List.of();
        }
        return operationMapper.selectDueForceActivationIds(LocalDateTime.now(clock), Math.min(limit, 500));
    }

    @Transactional
    public int recoverStaleClaims() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<CorsOperation> stale = operationMapper.selectStaleForceActivationClaimed(
                now.minus(properties.getClaimTimeout()), STALE_BATCH_SIZE);
        if (stale == null || stale.isEmpty()) {
            return 0;
        }

        int recovered = 0;
        for (CorsOperation operation : stale) {
            if (operation == null
                    || !AccountForceActivationConstants.isOperationForAccount(operation,
                    operation.getServiceAccountId())
                    || operation.getVersion() == null) {
                continue;
            }
            int currentCount = operation.getRetryCount() == null ? 0 : Math.max(0, operation.getRetryCount());
            int retryCount = currentCount == Integer.MAX_VALUE ? Integer.MAX_VALUE : currentCount + 1;
            boolean exhausted = retryCount > properties.getMaxRetries();
            int changed = operationMapper.recoverClaimed(operation.getId(), operation.getVersion(),
                    exhausted ? AccountForceActivationConstants.MANUAL_REVIEW
                            : AccountForceActivationConstants.RETRY_WAIT,
                    retryCount, exhausted ? null : now,
                    exhausted ? "CLAIM_TIMEOUT_EXHAUSTED" : "CLAIM_TIMEOUT",
                    exhausted ? "Force activation requires manual review after claim timeout"
                            : "Stale force-activation claim recovered; next attempt will query requestId",
                    now);
            if (changed == 1) {
                recovered++;
            }
        }
        return recovered;
    }
}
