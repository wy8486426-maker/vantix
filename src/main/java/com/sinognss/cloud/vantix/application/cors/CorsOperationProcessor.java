package com.sinognss.cloud.vantix.application.cors;

import com.sinognss.cloud.vantix.application.exchange.ServiceCodeExchangeFinalizeService;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.domain.exchange.ExchangeBatch;
import com.sinognss.cloud.vantix.domain.exchange.ExchangeStatus;
import com.sinognss.cloud.vantix.infrastructure.mapper.ExchangeBatchMapper;
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
public class CorsOperationProcessor {
    private static final Logger log = LoggerFactory.getLogger(CorsOperationProcessor.class);

    private final CorsOperationClaimService claimService;
    private final CorsOperationStateService stateService;
    private final ServiceCodeExchangeFinalizeService finalizeService;
    private final ExchangeBatchMapper batchMapper;
    private final CorsAccountGateway corsGateway;

    public CorsOperationProcessor(CorsOperationClaimService claimService,
                                  CorsOperationStateService stateService,
                                  ServiceCodeExchangeFinalizeService finalizeService,
                                  ExchangeBatchMapper batchMapper,
                                  CorsAccountGateway corsGateway) {
        this.claimService = claimService;
        this.stateService = stateService;
        this.finalizeService = finalizeService;
        this.batchMapper = batchMapper;
        this.corsGateway = corsGateway;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void process(Long operationId) {
        ClaimedCorsOperation claim = claimService.claim(operationId);
        if (claim == null) return;

        CorsOperation operation = claim.operation();
        ExchangeBatch batch = batchMapper.selectById(operation.getBizId());
        if (batch == null || !batch.getId().equals(operation.getBizId())) {
            stateService.markManualReview(operation, "EXCHANGE_BATCH_MISSING",
                    "CORS 操作关联的兑换批次不存在");
            return;
        }
        if (batch.getStatus() == ExchangeStatus.COMPLETED) return;
        if (batch.getStatus() != ExchangeStatus.PROCESSING) {
            stateService.markManualReview(operation, "EXCHANGE_STATE_INCONSISTENT",
                    "兑换批次状态与待执行 CORS 操作不一致");
            return;
        }

        CorsBatchCreateRequest request;
        try {
            request = new CorsBatchCreateRequest(operation.getRequestId(), batch.getQuantity(),
                    0, requirePositive(batch.getDurationDays(), "durationDays"),
                    requireAccountName(batch.getAccountPrefix()), 0,
                    requireNonNegative(batch.getAccountSilenceDays(), "accountSilenceDays"),
                    1, "", batch.getOwnerCompanyId(), 0);
        } catch (IllegalArgumentException exception) {
            stateService.markManualReview(operation, "EXCHANGE_SNAPSHOT_INVALID",
                    "兑换批次的 CORS 请求快照无效");
            return;
        }

        CorsBatchResult result = corsGateway.createBatch(request);
        logResult(operation, batch, result);
        if (result == null || result.outcome() == null) {
            stateService.retryOrMarkManualReview(operation, "CORS_CREATE_UNKNOWN",
                    "CORS 创建账号结果不明确");
            return;
        }
        switch (result.outcome()) {
            case SUCCESS -> handleSuccess(operation, batch, result);
            case DEFINITIVE_REJECT -> failOrReview(operation, result);
            case IDEMPOTENCY_CONFLICT -> stateService.markManualReview(operation,
                    "CORS_IDEMPOTENCY_CONFLICT", "CORS requestId 已对应不同请求参数");
            case UNKNOWN, NOT_FOUND -> stateService.retryOrMarkManualReview(operation,
                    safeCode(result.errorCode(), "CORS_CREATE_UNKNOWN"),
                    safeMessage(result.errorMessage(), "CORS 创建账号结果不明确"));
        }
    }

    private void handleSuccess(CorsOperation operation, ExchangeBatch batch, CorsBatchResult result) {
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
        finalizeOrReview(operation, result);
    }

    private void finalizeOrReview(CorsOperation operation, CorsBatchResult result) {
        try {
            finalizeService.finalizeSuccess(operation.getId(), operation.getVersion(), result);
        } catch (RuntimeException exception) {
            stateService.markManualReview(operation, "LOCAL_FINALIZE_FAILED",
                    "CORS 账号已创建，但 Vantix 本地入账失败，需要人工复核");
        }
    }

    private void failOrReview(CorsOperation operation, CorsBatchResult result) {
        try {
            stateService.failDefinitively(operation,
                    safeCode(result.errorCode(), "CORS_REJECTED"),
                    safeMessage(result.errorMessage(), "CORS 明确拒绝创建账号"));
        } catch (RuntimeException exception) {
            stateService.markManualReview(operation, "LOCAL_RELEASE_FAILED",
                    "CORS 已明确拒绝，但 Vantix 未能安全释放预留服务码，需要人工复核");
        }
    }

    private void logResult(CorsOperation operation, ExchangeBatch batch, CorsBatchResult result) {
        log.info("CORS add-account result operationId={} batchId={} requestId={} code={} message={} retryCount={} outcome={}",
                operation.getId(), batch.getId(), operation.getRequestId(),
                result == null ? null : safeCode(result.errorCode(), "0"),
                result == null ? null : safeMessage(result.errorMessage(), ""),
                operation.getRetryCount(), result == null ? null : result.outcome());
    }

    private static int requirePositive(Integer value, String name) {
        if (value == null || value <= 0) throw new IllegalArgumentException(name + " is invalid");
        return value;
    }

    private static int requireNonNegative(Integer value, String name) {
        if (value == null || value < 0) throw new IllegalArgumentException(name + " is invalid");
        return value;
    }

    private static String requireAccountName(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("accountName is invalid");
        return value;
    }

    private static String safeCode(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String result = value.trim();
        return result.length() <= 64 ? result : result.substring(0, 64);
    }

    private static String safeMessage(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String result = value.trim();
        return result.length() <= 1024 ? result : result.substring(0, 1024);
    }
}
