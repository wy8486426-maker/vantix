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
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TestAccountIssueClaimService {
    private static final int STALE_BATCH_SIZE = 100;
    private final CorsOperationMapper operationMapper;
    private final TestAccountIssueBatchMapper batchMapper;
    private final CorsOperationProperties properties;
    private final Clock clock;

    public TestAccountIssueClaimService(CorsOperationMapper operationMapper,
                                        TestAccountIssueBatchMapper batchMapper,
                                        CorsOperationProperties properties,
                                        Clock clock) {
        this.operationMapper = operationMapper;
        this.batchMapper = batchMapper;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public ClaimedTestAccountIssue claim(Long operationId) {
        CorsOperation current = operationMapper.selectById(operationId);
        if (!isPending(current)) return null;
        LocalDateTime now = LocalDateTime.now(clock);
        if (TestAccountIssueConstants.RETRY_WAIT.equals(current.getStatus())
                && current.getNextRetryAt() != null && current.getNextRetryAt().isAfter(now)) return null;
        if (isOutsideResultWindow(current, now)) {
            String message = "CORS Redis 回传窗口已超过 2 分钟，停止自动重试，需要人工复核";
            if (operationMapper.markPendingManualReview(current.getId(), current.getVersion(),
                    "CORS_RESULT_WINDOW_EXPIRED", message, now) != 1) return null;
            if (batchMapper.markManualReview(current.getBizId(), "CORS_RESULT_WINDOW_EXPIRED", message, now) != 1) {
                throw new BusinessException(ErrorCode.TEST_ACCOUNT_ISSUE_STATE_INCONSISTENT,
                        "测试账号下发批次转人工复核失败");
            }
            return null;
        }
        if (operationMapper.claim(current.getId(), current.getStatus(), current.getVersion(), now) != 1) return null;
        CorsOperation claimed = operationMapper.selectById(operationId);
        if (claimed == null || !TestAccountIssueConstants.CLAIMED.equals(claimed.getStatus())) {
            throw new BusinessException(ErrorCode.TEST_ACCOUNT_ISSUE_STATE_INCONSISTENT,
                    "测试账号 CORS 操作抢占后状态异常");
        }
        return new ClaimedTestAccountIssue(claimed);
    }

    public List<Long> findDueOperationIds(int limit) {
        return operationMapper.selectDueTestAccountIssueIds(LocalDateTime.now(clock), limit);
    }

    @Transactional
    public int recoverStaleClaims() {
        LocalDateTime now = LocalDateTime.now(clock);
        int recovered = 0;
        for (CorsOperation operation : operationMapper.selectStaleTestAccountIssueClaimed(
                now.minus(properties.getClaimTimeout()), STALE_BATCH_SIZE)) {
            int retryCount = (operation.getRetryCount() == null ? 0 : operation.getRetryCount()) + 1;
            boolean expired = isOutsideResultWindow(operation, now);
            boolean exhausted = expired || retryCount > properties.getMaxRetries();
            String code = expired ? "CORS_RESULT_WINDOW_EXPIRED" : "CLAIM_TIMEOUT";
            String message = expired
                    ? "CORS Redis 回传窗口已超过 2 分钟，停止自动重试，需要人工复核"
                    : "测试账号 CORS 操作执行超时，后续将在结果窗口内复用原 requestId 重试";
            if (exhausted && batchMapper.markManualReview(operation.getBizId(), code, message, now) != 1) {
                throw new BusinessException(ErrorCode.TEST_ACCOUNT_ISSUE_STATE_INCONSISTENT,
                        "测试账号下发超时转人工复核失败");
            }
            if (operationMapper.recoverClaimed(operation.getId(), operation.getVersion(),
                    exhausted ? TestAccountIssueConstants.MANUAL_REVIEW : TestAccountIssueConstants.RETRY_WAIT,
                    retryCount, exhausted ? null : now, code, message, now) != 1) {
                throw new BusinessException(ErrorCode.TEST_ACCOUNT_ISSUE_STATE_INCONSISTENT,
                        "测试账号 CORS 操作恢复时发生并发状态变化");
            }
            recovered++;
        }
        return recovered;
    }

    private static boolean isPending(CorsOperation operation) {
        return operation != null
                && TestAccountIssueConstants.OPERATION_TYPE.equals(operation.getOperationType())
                && TestAccountIssueConstants.BIZ_TYPE.equals(operation.getBizType())
                && (TestAccountIssueConstants.PENDING.equals(operation.getStatus())
                || TestAccountIssueConstants.RETRY_WAIT.equals(operation.getStatus()));
    }

    private boolean isOutsideResultWindow(CorsOperation operation, LocalDateTime now) {
        LocalDateTime start = operation.getFirstAttemptAt() == null
                ? operation.getCreatedAt() : operation.getFirstAttemptAt();
        if (start == null) return false;
        try {
            return !now.isBefore(start.plus(properties.getResultWindow()));
        } catch (ArithmeticException exception) {
            return true;
        }
    }
}
