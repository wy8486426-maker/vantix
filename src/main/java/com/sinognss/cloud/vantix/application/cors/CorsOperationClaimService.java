package com.sinognss.cloud.vantix.application.cors;

import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.config.CorsOperationProperties;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.infrastructure.mapper.CorsOperationMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ExchangeBatchMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class CorsOperationClaimService {
    private static final int STALE_BATCH_SIZE = 100;
    private final CorsOperationMapper operationMapper;
    private final ExchangeBatchMapper batchMapper;
    private final CorsOperationProperties properties;
    private final Clock clock;

    public CorsOperationClaimService(CorsOperationMapper operationMapper,
                                     ExchangeBatchMapper batchMapper,
                                     CorsOperationProperties properties,
                                     Clock clock) {
        this.operationMapper = operationMapper;
        this.batchMapper = batchMapper;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public ClaimedCorsOperation claim(Long operationId) {
        CorsOperation current = operationMapper.selectById(operationId);
        if (current == null || !"BATCH_CREATE_ACCOUNT".equals(current.getOperationType())
                || !"EXCHANGE_BATCH".equals(current.getBizType())
                || !("PENDING".equals(current.getStatus())
                || "RETRY_WAIT".equals(current.getStatus()))) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if ("RETRY_WAIT".equals(current.getStatus())
                && current.getNextRetryAt() != null && current.getNextRetryAt().isAfter(now)) {
            return null;
        }
        boolean queryFirst = "RETRY_WAIT".equals(current.getStatus());
        if (operationMapper.claim(current.getId(), current.getStatus(), current.getVersion(), now) != 1) {
            return null;
        }
        CorsOperation claimed = operationMapper.selectById(operationId);
        if (claimed == null || !"CLAIMED".equals(claimed.getStatus())) {
            throw new BusinessException(ErrorCode.EXCHANGE_STATE_INCONSISTENT,
                    "CORS 操作抢占后状态异常");
        }
        return new ClaimedCorsOperation(claimed, queryFirst);
    }

    public List<Long> findDueOperationIds(int limit) {
        return operationMapper.selectDueIds(LocalDateTime.now(clock), limit);
    }

    @Transactional
    public int recoverStaleClaims() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<CorsOperation> stale = operationMapper.selectStaleClaimed(
                now.minus(properties.getClaimTimeout()), STALE_BATCH_SIZE);
        int recovered = 0;
        for (CorsOperation operation : stale) {
            int retryCount = (operation.getRetryCount() == null ? 0 : operation.getRetryCount()) + 1;
            boolean exhausted = retryCount > properties.getMaxRetries();
            if (exhausted && batchMapper.markManualReview(operation.getBizId(),
                    "CLAIM_TIMEOUT_EXHAUSTED", "CORS 操作执行超时，已达到自动重试上限", now) != 1) {
                throw new BusinessException(ErrorCode.EXCHANGE_STATE_INCONSISTENT,
                        "超时操作转人工复核时批次状态不一致");
            }
            int changed = operationMapper.recoverClaimed(operation.getId(), operation.getVersion(),
                    exhausted ? "MANUAL_REVIEW" : "RETRY_WAIT", retryCount,
                    exhausted ? null : now, "CLAIM_TIMEOUT",
                    "CORS 操作执行超时，后续将先查询远端 requestId", now);
            if (changed != 1) {
                throw new BusinessException(ErrorCode.EXCHANGE_STATE_INCONSISTENT,
                        "超时操作恢复时发生并发状态变化");
            }
            recovered++;
        }
        return recovered;
    }
}
