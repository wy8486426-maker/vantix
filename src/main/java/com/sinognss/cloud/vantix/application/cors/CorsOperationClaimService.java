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
        if (isOutsideResultWindow(current, now)) {
            String message = "CORS Redis 回传窗口已超过 2 分钟，停止自动重试，需要人工复核";
            if (operationMapper.markPendingManualReview(current.getId(), current.getVersion(),
                    "CORS_RESULT_WINDOW_EXPIRED", message, now) != 1) {
                return null;
            }
            if (batchMapper.markManualReview(current.getBizId(), "CORS_RESULT_WINDOW_EXPIRED", message, now)
                    != 1) {
                throw new BusinessException(ErrorCode.EXCHANGE_STATE_INCONSISTENT,
                        "CORS 超过结果窗口后更新兑换批次失败");
            }
            return null;
        }
        if (operationMapper.claim(current.getId(), current.getStatus(), current.getVersion(), now) != 1) {
            return null;
        }
        CorsOperation claimed = operationMapper.selectById(operationId);
        if (claimed == null || !"CLAIMED".equals(claimed.getStatus())) {
            throw new BusinessException(ErrorCode.EXCHANGE_STATE_INCONSISTENT,
                    "CORS 操作抢占后状态异常");
        }
        return new ClaimedCorsOperation(claimed);
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
            boolean windowExpired = isOutsideResultWindow(operation, now);
            boolean exhausted = windowExpired || retryCount > properties.getMaxRetries();
            String errorCode = windowExpired ? "CORS_RESULT_WINDOW_EXPIRED" : "CLAIM_TIMEOUT";
            String errorMessage = windowExpired
                    ? "CORS Redis 回传窗口已超过 2 分钟，停止自动重试，需要人工复核"
                    : "CORS 操作执行超时，后续将在结果窗口内复用原 requestId 重试";
            if (exhausted && batchMapper.markManualReview(operation.getBizId(),
                    errorCode, errorMessage, now) != 1) {
                throw new BusinessException(ErrorCode.EXCHANGE_STATE_INCONSISTENT,
                        "超时操作转人工复核时批次状态不一致");
            }
            int changed = operationMapper.recoverClaimed(operation.getId(), operation.getVersion(),
                    exhausted ? "MANUAL_REVIEW" : "RETRY_WAIT", retryCount,
                    exhausted ? null : now, errorCode, errorMessage, now);
            if (changed != 1) {
                throw new BusinessException(ErrorCode.EXCHANGE_STATE_INCONSISTENT,
                        "超时操作恢复时发生并发状态变化");
            }
            recovered++;
        }
        return recovered;
    }

    private boolean isOutsideResultWindow(CorsOperation operation, LocalDateTime now) {
        LocalDateTime windowStart = operation.getFirstAttemptAt();
        if (windowStart == null && ("RETRY_WAIT".equals(operation.getStatus())
                || "CLAIMED".equals(operation.getStatus()))) {
            windowStart = operation.getCreatedAt();
        }
        if (windowStart == null) return false;
        try {
            return !now.isBefore(windowStart.plus(properties.getResultWindow()));
        } catch (ArithmeticException exception) {
            return true;
        }
    }
}
