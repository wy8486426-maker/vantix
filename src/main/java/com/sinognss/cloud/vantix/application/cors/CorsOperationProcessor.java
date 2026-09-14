package com.sinognss.cloud.vantix.application.cors;

import com.sinognss.cloud.vantix.config.CorsOperationProperties;
import com.sinognss.cloud.vantix.domain.config.DurationUnit;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.application.exchange.ServiceCodeExchangeFinalizeService;
import com.sinognss.cloud.vantix.domain.exchange.ExchangeBatch;
import com.sinognss.cloud.vantix.domain.exchange.ExchangeStatus;
import com.sinognss.cloud.vantix.infrastructure.mapper.ExchangeBatchMapper;
import com.sinognss.cloud.vantix.integration.cors.CorsAccountGateway;
import com.sinognss.cloud.vantix.integration.cors.CorsBatchCreateRequest;
import com.sinognss.cloud.vantix.integration.cors.CorsBatchResult;
import com.sinognss.cloud.vantix.integration.cors.CorsOutcome;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CorsOperationProcessor {
    private final CorsOperationClaimService claimService;
    private final CorsOperationStateService stateService;
    private final ServiceCodeExchangeFinalizeService finalizeService;
    private final ExchangeBatchMapper batchMapper;
    private final CorsAccountGateway corsGateway;
    private final CorsOperationProperties properties;

    public CorsOperationProcessor(CorsOperationClaimService claimService,
                                  CorsOperationStateService stateService,
                                  ServiceCodeExchangeFinalizeService finalizeService,
                                  ExchangeBatchMapper batchMapper,
                                  CorsAccountGateway corsGateway,
                                  CorsOperationProperties properties) {
        this.claimService = claimService;
        this.stateService = stateService;
        this.finalizeService = finalizeService;
        this.batchMapper = batchMapper;
        this.corsGateway = corsGateway;
        this.properties = properties;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void process(Long operationId) {
        ClaimedCorsOperation claim = claimService.claim(operationId);
        if (claim == null) return;

        CorsOperation operation = claim.operation();
        ExchangeBatch batch = batchMapper.selectByRequestId(operation.getRequestId());
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

        if (claim.queryFirst()) {
            CorsBatchResult query = corsGateway.queryBatch(batch.getRequestId());
            if (handleQueryResult(operation, query)) return;
        }
        sendCreate(operation, batch);
    }

    private boolean handleQueryResult(CorsOperation operation, CorsBatchResult result) {
        if (result == null || result.outcome() == null) {
            stateService.retryOrMarkManualReview(operation, "CORS_QUERY_UNKNOWN",
                    "查询 CORS 操作结果时未获得有效响应");
            return true;
        }
        return switch (result.outcome()) {
            case SUCCESS -> {
                finalizeOrReview(operation, result);
                yield true;
            }
            case NOT_FOUND -> {
                if (operation.getRetryCount() != null
                        && operation.getRetryCount() > properties.getMaxRetries()) {
                    stateService.markManualReview(operation, "CORS_RETRY_EXHAUSTED",
                            "CORS requestId 尚未创建账号，已达到自动重试上限");
                    yield true;
                }
                yield false;
            }
            case IDEMPOTENCY_CONFLICT -> {
                stateService.markManualReview(operation, "CORS_IDEMPOTENCY_CONFLICT",
                        "CORS requestId 已对应不同请求参数");
                yield true;
            }
            case UNKNOWN, DEFINITIVE_REJECT -> {
                stateService.retryOrMarkManualReview(operation,
                        safeCode(result.errorCode(), "CORS_QUERY_UNKNOWN"),
                        safeMessage(result.errorMessage(), "查询 CORS 操作结果不明确"));
                yield true;
            }
        };
    }

    private void sendCreate(CorsOperation operation, ExchangeBatch batch) {
        DurationUnit durationUnit;
        try {
            durationUnit = DurationUnit.valueOf(batch.getDurationUnit());
        } catch (Exception exception) {
            stateService.markManualReview(operation, "EXCHANGE_DURATION_INVALID",
                    "兑换批次时长单位无效");
            return;
        }
        CorsBatchCreateRequest request = new CorsBatchCreateRequest(batch.getRequestId(),
                batch.getDurationValue(), durationUnit, batch.getQuantity(), batch.getAccountPrefix());
        CorsBatchResult result = corsGateway.createBatch(request);
        if (result == null || result.outcome() == null) {
            stateService.retryOrMarkManualReview(operation, "CORS_CREATE_UNKNOWN",
                    "CORS 创建账号结果不明确");
            return;
        }
        switch (result.outcome()) {
            case SUCCESS -> finalizeOrReview(operation, result);
            case DEFINITIVE_REJECT -> failOrReview(operation, result);
            case IDEMPOTENCY_CONFLICT -> stateService.markManualReview(operation,
                    "CORS_IDEMPOTENCY_CONFLICT", "CORS requestId 已对应不同请求参数");
            case UNKNOWN, NOT_FOUND -> stateService.retryOrMarkManualReview(operation,
                    safeCode(result.errorCode(), "CORS_CREATE_UNKNOWN"),
                    safeMessage(result.errorMessage(), "CORS 创建账号结果不明确"));
        }
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
