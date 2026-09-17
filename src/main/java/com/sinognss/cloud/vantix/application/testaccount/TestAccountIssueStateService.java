package com.sinognss.cloud.vantix.application.testaccount;

import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.config.CorsOperationProperties;
import com.sinognss.cloud.vantix.domain.account.TestAccountIssueBatch;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.infrastructure.mapper.CorsOperationMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.TestAccountIssueBatchMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class TestAccountIssueStateService {
    private static final Duration MAX_RETRY_DELAY = Duration.ofHours(1);
    private final CorsOperationMapper operationMapper;
    private final TestAccountIssueBatchMapper batchMapper;
    private final CorsOperationProperties properties;
    private final Clock clock;

    public TestAccountIssueStateService(CorsOperationMapper operationMapper,
                                        TestAccountIssueBatchMapper batchMapper,
                                        CorsOperationProperties properties,
                                        Clock clock) {
        this.operationMapper = operationMapper;
        this.batchMapper = batchMapper;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public boolean retryOrMarkManualReview(CorsOperation claimed, String errorCode, String message) {
        CorsOperation current = currentClaim(claimed);
        if (current == null) return false;
        LocalDateTime now = LocalDateTime.now(clock);
        int retryCount = (current.getRetryCount() == null ? 0 : current.getRetryCount()) + 1;
        String code = safe(errorCode, "CORS_OUTCOME_UNKNOWN");
        String safeMessage = safe(message, "CORS 结果无法确认，已达到自动重试上限");
        LocalDateTime next = now.plus(retryDelay(retryCount));
        boolean expired = outsideWindow(current, now) || atDeadline(current, next);
        if (retryCount > properties.getMaxRetries() || expired) {
            if (expired) {
                code = "CORS_RESULT_WINDOW_EXPIRED";
                safeMessage = "CORS Redis 回传窗口已超过 2 分钟，停止自动重试，需要人工复核";
            }
            markBatchManual(current, code, safeMessage, now);
            requireOne(operationMapper.markManualReview(current.getId(), current.getVersion(), code, safeMessage, now),
                    "测试账号 CORS 操作转人工复核失败");
            return true;
        }
        requireOne(operationMapper.scheduleRetry(current.getId(), current.getVersion(), retryCount, next,
                code, safeMessage, now), "测试账号 CORS 操作进入重试状态失败");
        return true;
    }

    @Transactional
    public boolean markManualReview(CorsOperation claimed, String errorCode, String message) {
        CorsOperation current = currentClaim(claimed);
        if (current == null) return false;
        LocalDateTime now = LocalDateTime.now(clock);
        String code = safe(errorCode, "TEST_ACCOUNT_ISSUE_STATE_INCONSISTENT");
        String text = safe(message, "测试账号下发需要人工复核");
        markBatchManual(current, code, text, now);
        requireOne(operationMapper.markManualReview(current.getId(), current.getVersion(), code, text, now),
                "测试账号 CORS 操作转人工复核失败");
        return true;
    }

    @Transactional
    public boolean failDefinitively(CorsOperation claimed, String errorCode, String message) {
        CorsOperation current = currentClaim(claimed);
        if (current == null) return false;
        TestAccountIssueBatch batch = batchMapper.selectByIdForUpdate(current.getBizId());
        if (batch == null || !current.getBizId().equals(batch.getId())
                || !TestAccountIssueConstants.PROCESSING.equals(batch.getStatus())) {
            throw new BusinessException(ErrorCode.TEST_ACCOUNT_ISSUE_STATE_INCONSISTENT,
                    "测试账号明确拒绝时批次状态不一致");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        String code = safe(errorCode, "CORS_REJECTED");
        String text = safe(message, "CORS 明确拒绝创建测试账号");
        requireOne(batchMapper.fail(batch.getId(), code, text, now), "测试账号批次失败状态更新失败");
        requireOne(operationMapper.markFailed(current.getId(), current.getVersion(), code, text, now),
                "测试账号 CORS 操作失败状态更新失败");
        return true;
    }

    private CorsOperation currentClaim(CorsOperation claimed) {
        if (claimed == null || claimed.getId() == null || claimed.getVersion() == null) return null;
        CorsOperation current = operationMapper.selectById(claimed.getId());
        return current != null && TestAccountIssueConstants.CLAIMED.equals(current.getStatus())
                && claimed.getVersion().equals(current.getVersion()) ? current : null;
    }

    private void markBatchManual(CorsOperation operation, String code, String message, LocalDateTime now) {
        TestAccountIssueBatch batch = batchMapper.selectByIdForUpdate(operation.getBizId());
        if (batch == null || !operation.getBizId().equals(batch.getId())
                || !TestAccountIssueConstants.PROCESSING.equals(batch.getStatus())) {
            throw new BusinessException(ErrorCode.TEST_ACCOUNT_ISSUE_STATE_INCONSISTENT,
                    "测试账号批次无法转入人工复核");
        }
        requireOne(batchMapper.markManualReview(batch.getId(), code, message, now),
                "测试账号批次转人工复核失败");
    }

    private Duration retryDelay(int retryCount) {
        long multiplier = 1L << Math.min(Math.max(retryCount - 1, 0), 20);
        try {
            Duration delay = properties.getRetryBaseDelay().multipliedBy(multiplier);
            return delay.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : delay;
        } catch (ArithmeticException exception) {
            return MAX_RETRY_DELAY;
        }
    }

    private boolean outsideWindow(CorsOperation operation, LocalDateTime now) {
        LocalDateTime start = operation.getFirstAttemptAt() == null
                ? operation.getCreatedAt() : operation.getFirstAttemptAt();
        if (start == null) return false;
        try { return !now.isBefore(start.plus(properties.getResultWindow())); }
        catch (ArithmeticException exception) { return true; }
    }

    private boolean atDeadline(CorsOperation operation, LocalDateTime next) {
        LocalDateTime start = operation.getFirstAttemptAt() == null
                ? operation.getCreatedAt() : operation.getFirstAttemptAt();
        if (start == null) return false;
        try { return !next.isBefore(start.plus(properties.getResultWindow())); }
        catch (ArithmeticException exception) { return true; }
    }

    private static String safe(String value, String fallback) {
        String result = value == null || value.isBlank() ? fallback : value.trim();
        return result.length() <= 1024 ? result : result.substring(0, 1024);
    }

    private static void requireOne(int actual, String message) {
        if (actual != 1) throw new BusinessException(ErrorCode.TEST_ACCOUNT_ISSUE_STATE_INCONSISTENT, message);
    }
}
