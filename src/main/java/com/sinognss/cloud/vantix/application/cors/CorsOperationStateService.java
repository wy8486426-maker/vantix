package com.sinognss.cloud.vantix.application.cors;

import com.sinognss.cloud.vantix.config.CorsOperationProperties;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.domain.exchange.ExchangeBatch;
import com.sinognss.cloud.vantix.domain.exchange.ExchangeDetail;
import com.sinognss.cloud.vantix.domain.exchange.ExchangeStatus;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.infrastructure.mapper.CorsOperationMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ExchangeBatchMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ExchangeDetailMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class CorsOperationStateService {
    private static final String OPERATION_BIZ_TYPE = "EXCHANGE_BATCH";
    private static final Duration MAX_RETRY_DELAY = Duration.ofHours(1);

    private final CorsOperationMapper operationMapper;
    private final ExchangeBatchMapper batchMapper;
    private final ExchangeDetailMapper detailMapper;
    private final ServiceCodeMapper serviceCodeMapper;
    private final CorsOperationProperties properties;
    private final Clock clock;

    public CorsOperationStateService(CorsOperationMapper operationMapper,
                                     ExchangeBatchMapper batchMapper,
                                     ExchangeDetailMapper detailMapper,
                                     ServiceCodeMapper serviceCodeMapper,
                                     CorsOperationProperties properties,
                                     Clock clock) {
        this.operationMapper = operationMapper;
        this.batchMapper = batchMapper;
        this.detailMapper = detailMapper;
        this.serviceCodeMapper = serviceCodeMapper;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public boolean retryOrMarkManualReview(CorsOperation claimed, String errorCode, String message) {
        CorsOperation current = currentClaim(claimed);
        if (current == null) return false;
        LocalDateTime now = LocalDateTime.now(clock);
        int retryCount = (current.getRetryCount() == null ? 0 : current.getRetryCount()) + 1;
        String safeCode = safe(errorCode, "CORS_OUTCOME_UNKNOWN");
        String safeMessage = safe(message, "CORS 结果无法确认，已达到自动重试上限");
        LocalDateTime nextRetryAt = now.plus(retryDelay(retryCount));
        boolean windowExpired = isOutsideResultWindow(current, now)
                || isAtOrAfterResultDeadline(current, nextRetryAt);
        if (retryCount > properties.getMaxRetries() || windowExpired) {
            if (windowExpired) {
                safeCode = "CORS_RESULT_WINDOW_EXPIRED";
                safeMessage = "CORS Redis 回传窗口已超过 2 分钟，停止自动重试，需要人工复核";
            }
            markBatchManualReview(current, safeCode, safeMessage, now);
            requireOne(operationMapper.markManualReview(current.getId(), current.getVersion(),
                    safeCode, safeMessage, now),
                    "CORS 操作转人工复核失败");
            return true;
        }
        requireOne(operationMapper.scheduleRetry(current.getId(), current.getVersion(), retryCount,
                nextRetryAt, safeCode, safeMessage, now),
                "CORS 操作进入重试状态失败");
        return true;
    }

    @Transactional
    public boolean markManualReview(CorsOperation claimed, String errorCode, String message) {
        CorsOperation current = currentClaim(claimed);
        if (current == null) return false;
        LocalDateTime now = LocalDateTime.now(clock);
        markBatchManualReview(current, safe(errorCode, "EXCHANGE_STATE_INCONSISTENT"),
                safe(message, "兑换需要人工复核"), now);
        requireOne(operationMapper.markManualReview(current.getId(), current.getVersion(),
                safe(errorCode, "EXCHANGE_STATE_INCONSISTENT"),
                safe(message, "兑换需要人工复核"), now),
                "CORS 操作转人工复核失败");
        return true;
    }

    @Transactional
    public boolean failDefinitively(CorsOperation claimed, String errorCode, String message) {
        CorsOperation current = currentClaim(claimed);
        if (current == null) return false;
        ExchangeBatch batch = batchMapper.selectByIdForUpdate(current.getBizId());
        if (batch == null || batch.getStatus() != ExchangeStatus.PROCESSING
                || !batch.getId().equals(current.getBizId())
                || !OPERATION_BIZ_TYPE.equals(current.getBizType())) {
            throw new BusinessException(ErrorCode.EXCHANGE_STATE_INCONSISTENT,
                    "明确拒绝时兑换批次不存在或状态不一致");
        }
        List<ExchangeDetail> details = detailMapper.selectByBatchId(batch.getId());
        if (details.size() != batch.getQuantity()
                || details.stream().anyMatch(detail -> detail.getStatus() != ExchangeStatus.PROCESSING)) {
            throw new BusinessException(ErrorCode.EXCHANGE_STATE_INCONSISTENT,
                    "明确拒绝时兑换明细不完整");
        }
        List<Long> ids = details.stream().map(ExchangeDetail::getServiceCodeId).toList();
        LocalDateTime now = LocalDateTime.now(clock);
        String code = safe(errorCode, "CORS_REJECTED");
        String safeMessage = safe(message, "CORS 明确拒绝创建账号");
        requireCount(serviceCodeMapper.releaseExchangeCodes(ids, batch.getRequestId(), now),
                batch.getQuantity(), "明确拒绝后释放服务码数量不一致");
        requireCount(detailMapper.failByBatchId(batch.getId(), code, safeMessage, now),
                batch.getQuantity(), "明确拒绝后更新兑换明细数量不一致");
        requireOne(batchMapper.fail(batch.getId(), code, safeMessage, now),
                "明确拒绝后更新兑换批次失败");
        requireOne(operationMapper.markFailed(current.getId(), current.getVersion(), code, safeMessage, now),
                "明确拒绝后更新 CORS 操作失败");
        return true;
    }

    private CorsOperation currentClaim(CorsOperation claimed) {
        CorsOperation current = operationMapper.selectById(claimed.getId());
        if (current == null || !"CLAIMED".equals(current.getStatus())
                || !claimed.getVersion().equals(current.getVersion())) {
            return null;
        }
        return current;
    }

    private void markBatchManualReview(CorsOperation operation, String errorCode, String message,
                                      LocalDateTime now) {
        ExchangeBatch batch = batchMapper.selectByIdForUpdate(operation.getBizId());
        if (batch == null || !batch.getId().equals(operation.getBizId())
                || batch.getStatus() != ExchangeStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.EXCHANGE_STATE_INCONSISTENT,
                    "兑换批次无法转入人工复核");
        }
        requireOne(batchMapper.markManualReview(batch.getId(), errorCode, message, now),
                "兑换批次转人工复核失败");
    }

    private Duration retryDelay(int retryCount) {
        long multiplier = 1L << Math.min(Math.max(retryCount - 1, 0), 20);
        Duration delay;
        try {
            delay = properties.getRetryBaseDelay().multipliedBy(multiplier);
        } catch (ArithmeticException exception) {
            delay = MAX_RETRY_DELAY;
        }
        return delay.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : delay;
    }

    private boolean isOutsideResultWindow(CorsOperation operation, LocalDateTime now) {
        LocalDateTime windowStart = resultWindowStart(operation);
        if (windowStart == null) return false;
        try {
            return !now.isBefore(windowStart.plus(properties.getResultWindow()));
        } catch (ArithmeticException exception) {
            return true;
        }
    }

    private boolean isAtOrAfterResultDeadline(CorsOperation operation, LocalDateTime retryAt) {
        LocalDateTime windowStart = resultWindowStart(operation);
        if (windowStart == null) return false;
        try {
            return !retryAt.isBefore(windowStart.plus(properties.getResultWindow()));
        } catch (ArithmeticException exception) {
            return true;
        }
    }

    private static LocalDateTime resultWindowStart(CorsOperation operation) {
        return operation.getFirstAttemptAt() == null
                ? operation.getCreatedAt() : operation.getFirstAttemptAt();
    }

    private static String safe(String value, String fallback) {
        String result = value == null || value.isBlank() ? fallback : value.trim();
        return result.length() <= 1024 ? result : result.substring(0, 1024);
    }

    private static void requireCount(int actual, int expected, String message) {
        if (actual != expected) throw new BusinessException(ErrorCode.EXCHANGE_STATE_INCONSISTENT, message);
    }

    private static void requireOne(int actual, String message) {
        if (actual != 1) throw new BusinessException(ErrorCode.EXCHANGE_STATE_INCONSISTENT, message);
    }
}
