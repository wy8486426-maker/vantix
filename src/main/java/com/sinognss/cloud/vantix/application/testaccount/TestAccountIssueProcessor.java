package com.sinognss.cloud.vantix.application.testaccount;

import com.sinognss.cloud.vantix.domain.account.TestAccountIssueBatch;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.infrastructure.mapper.TestAccountIssueBatchMapper;
import com.sinognss.cloud.vantix.integration.cors.CorsAccountGateway;
import com.sinognss.cloud.vantix.integration.cors.CorsAddAccountData;
import com.sinognss.cloud.vantix.integration.cors.CorsBatchCreateRequest;
import com.sinognss.cloud.vantix.integration.cors.CorsBatchResult;
import com.sinognss.cloud.vantix.integration.cors.CorsOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TestAccountIssueProcessor {
    private static final Logger log = LoggerFactory.getLogger(TestAccountIssueProcessor.class);
    private final TestAccountIssueClaimService claimService;
    private final TestAccountIssueStateService stateService;
    private final TestAccountIssueFinalizeService finalizeService;
    private final TestAccountIssueBatchMapper batchMapper;
    private final CorsAccountGateway corsGateway;

    public TestAccountIssueProcessor(TestAccountIssueClaimService claimService,
                                     TestAccountIssueStateService stateService,
                                     TestAccountIssueFinalizeService finalizeService,
                                     TestAccountIssueBatchMapper batchMapper,
                                     CorsAccountGateway corsGateway) {
        this.claimService = claimService;
        this.stateService = stateService;
        this.finalizeService = finalizeService;
        this.batchMapper = batchMapper;
        this.corsGateway = corsGateway;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void process(Long operationId) {
        ClaimedTestAccountIssue claim = claimService.claim(operationId);
        if (claim == null) return;
        CorsOperation operation = claim.operation();
        TestAccountIssueBatch batch = batchMapper.selectById(operation.getBizId());
        if (batch == null || !operation.getBizId().equals(batch.getId())) {
            stateService.markManualReview(operation, "TEST_ACCOUNT_ISSUE_BATCH_MISSING",
                    "CORS 操作关联的测试账号下发批次不存在");
            return;
        }
        if (TestAccountIssueConstants.COMPLETED.equals(batch.getStatus())) return;
        if (!TestAccountIssueConstants.PROCESSING.equals(batch.getStatus())) {
            stateService.markManualReview(operation, "TEST_ACCOUNT_ISSUE_STATE_INCONSISTENT",
                    "测试账号下发批次状态与待执行 CORS 操作不一致");
            return;
        }

        CorsBatchCreateRequest request;
        try {
            request = new CorsBatchCreateRequest(operation.getRequestId(), requirePositive(batch.getQuantity()),
                    0, requirePositive(batch.getDurationDays()), requirePrefix(batch.getAccountPrefix()), 0,
                    requireNonNegative(batch.getAccountSilenceDays()), 1, "", batch.getOwnerCompanyId(), 0);
        } catch (IllegalArgumentException exception) {
            stateService.markManualReview(operation, "TEST_ACCOUNT_ISSUE_SNAPSHOT_INVALID",
                    "测试账号下发批次的 CORS 请求快照无效");
            return;
        }
        CorsBatchResult result = corsGateway.createBatch(request);
        log.info("CORS test-account result operationId={} batchId={} requestId={} outcome={} errorCode={}",
                operation.getId(), batch.getId(), operation.getRequestId(),
                result == null ? null : result.outcome(), result == null ? null : result.errorCode());
        if (result == null || result.outcome() == null) {
            stateService.retryOrMarkManualReview(operation, "CORS_CREATE_UNKNOWN", "CORS 创建账号结果不明确");
            return;
        }
        switch (result.outcome()) {
            case SUCCESS -> handleSuccess(operation, batch, result);
            case DEFINITIVE_REJECT -> stateService.failDefinitively(operation,
                    safe(result.errorCode(), "CORS_REJECTED"), safe(result.errorMessage(), "CORS 明确拒绝创建测试账号"));
            case IDEMPOTENCY_CONFLICT -> stateService.markManualReview(operation,
                    "CORS_IDEMPOTENCY_CONFLICT", "CORS requestId 已对应不同请求参数");
            case UNKNOWN, NOT_FOUND -> stateService.retryOrMarkManualReview(operation,
                    safe(result.errorCode(), "CORS_CREATE_UNKNOWN"),
                    safe(result.errorMessage(), "CORS 创建账号结果不明确"));
        }
    }

    private void handleSuccess(CorsOperation operation, TestAccountIssueBatch batch, CorsBatchResult result) {
        CorsAddAccountData data = result.data();
        if (data == null) {
            stateService.retryOrMarkManualReview(operation, "CORS_RESULT_PENDING",
                    "CORS 创建请求已成功接受，但结果详情暂未获取到");
            return;
        }
        if (!data.hasValidAccounts(batch.getQuantity())) {
            stateService.retryOrMarkManualReview(operation, "CORS_RESULT_INCOMPLETE",
                    "CORS 返回的 accounts 不完整或包含无效账号");
            return;
        }
        try {
            finalizeService.finalizeSuccess(operation.getId(), operation.getVersion(), result);
        } catch (RuntimeException exception) {
            stateService.retryOrMarkManualReview(operation, "LOCAL_FINALIZE_FAILED",
                    "CORS 账号已创建，但 Vantix 本地入账失败，将在结果窗口内复用原 requestId 重试");
        }
    }

    private static int requirePositive(Integer value) {
        if (value == null || value <= 0) throw new IllegalArgumentException("positive value required");
        return value;
    }

    private static int requireNonNegative(Integer value) {
        if (value == null || value < 0) throw new IllegalArgumentException("non-negative value required");
        return value;
    }

    private static String requirePrefix(String value) {
        if (value == null || !value.matches("^[A-Za-z0-9]{4}$")) {
            throw new IllegalArgumentException("accountPrefix is invalid");
        }
        return value;
    }

    private static String safe(String value, String fallback) {
        String result = value == null || value.isBlank() ? fallback : value.trim();
        return result.length() <= 1024 ? result : result.substring(0, 1024);
    }
}
