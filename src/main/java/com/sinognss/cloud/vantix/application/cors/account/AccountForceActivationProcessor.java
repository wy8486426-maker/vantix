package com.sinognss.cloud.vantix.application.cors.account;

import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.integration.cors.CorsOutcome;
import com.sinognss.cloud.vantix.integration.cors.account.CorsForceActivationGateway;
import com.sinognss.cloud.vantix.integration.cors.account.CorsForceActivationRequest;
import com.sinognss.cloud.vantix.integration.cors.account.CorsForceActivationResult;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class AccountForceActivationProcessor {
    private static final Logger log = LoggerFactory.getLogger(AccountForceActivationProcessor.class);

    private final AccountForceActivationClaimService claimService;
    private final AccountForceActivationStateService stateService;
    private final AccountForceActivationFinalizeService finalizeService;
    private final ServiceAccountMapper accountMapper;
    private final CorsForceActivationGateway gateway;

    public AccountForceActivationProcessor(AccountForceActivationClaimService claimService,
                                           AccountForceActivationStateService stateService,
                                           AccountForceActivationFinalizeService finalizeService,
                                           ServiceAccountMapper accountMapper,
                                           CorsForceActivationGateway gateway) {
        this.claimService = claimService;
        this.stateService = stateService;
        this.finalizeService = finalizeService;
        this.accountMapper = accountMapper;
        this.gateway = gateway;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void process(Long operationId) {
        ClaimedAccountForceActivation claim = claimService.claim(operationId);
        if (claim == null || claim.operation() == null) {
            return;
        }
        CorsOperation operation = claim.operation();
        ServiceAccount account = accountMapper.selectById(operation.getServiceAccountId());
        if (!AccountForceActivationConstants.hasIdentity(account, operation)) {
            stateService.markManualReview(operation, "OPERATION_ACCOUNT_MISMATCH",
                    "Force-activation operation account identity is inconsistent");
            return;
        }

        if (claim.queryFirst()) {
            CorsForceActivationResult query;
            try {
                query = gateway.queryForceActivation(operation.getRequestId());
            } catch (RuntimeException exception) {
                log.warn("Force-activation query failed; operationId={} requestId={} serviceAccountId={} "
                                + "corsAccountId={} outcome=UNKNOWN errorCode={}",
                        operation.getId(), operation.getRequestId(), operation.getServiceAccountId(),
                        account.getCorsAccountId(), exception.getClass().getSimpleName());
                stateService.retryOrMarkManualReview(operation, "QUERY_UNKNOWN",
                        "Force-activation query result is unknown");
                return;
            }
            if (handleQueryResult(operation, query)) {
                return;
            }
        }
        sendForceActivation(operation);
    }

    private boolean handleQueryResult(CorsOperation operation, CorsForceActivationResult result) {
        if (result == null || result.outcome() == null) {
            stateService.retryOrMarkManualReview(operation, "QUERY_MALFORMED",
                    "Force-activation query returned no valid result");
            return true;
        }
        return switch (result.outcome()) {
            case SUCCESS -> {
                finalizeOrReview(operation, result);
                yield true;
            }
            case NOT_FOUND -> false;
            case UNKNOWN -> {
                stateService.retryOrMarkManualReview(operation, result.errorCode(),
                        "Force-activation query result is unknown");
                yield true;
            }
            case IDEMPOTENCY_CONFLICT -> {
                stateService.markManualReview(operation, "IDEMPOTENCY_CONFLICT",
                        "Force-activation requestId conflicts with a different remote request");
                yield true;
            }
            case DEFINITIVE_REJECT -> {
                stateService.markManualReview(operation, "DEFINITIVE_REJECT",
                        "CORS rejected force activation and requires manual review");
                yield true;
            }
        };
    }

    private void sendForceActivation(CorsOperation operation) {
        ServiceAccount account = accountMapper.selectById(operation.getServiceAccountId());
        if (!AccountForceActivationConstants.hasIdentity(account, operation)) {
            stateService.markManualReview(operation, "OPERATION_ACCOUNT_MISMATCH",
                    "Force-activation operation account identity is inconsistent");
            return;
        }
        if (AccountForceActivationConstants.ACTIVE.equals(account.getCorsActivationStatus())) {
            stateService.markSucceeded(operation);
            return;
        }
        if (!AccountForceActivationConstants.WAITING_ACTIVATION.equals(account.getCorsActivationStatus())) {
            stateService.markManualReview(operation, "UNKNOWN_LOCAL_ACTIVATION_STATE",
                    "Local activation state is not eligible for force activation");
            return;
        }

        CorsForceActivationResult result;
        try {
            result = gateway.forceActivate(new CorsForceActivationRequest(
                    operation.getRequestId(), account.getCorsAccountId()));
        } catch (RuntimeException exception) {
            log.warn("Force-activation request failed; operationId={} requestId={} serviceAccountId={} "
                            + "corsAccountId={} outcome=UNKNOWN errorCode={}",
                    operation.getId(), operation.getRequestId(), operation.getServiceAccountId(),
                    account.getCorsAccountId(), exception.getClass().getSimpleName());
            stateService.retryOrMarkManualReview(operation, "POST_UNKNOWN",
                    "Force-activation request result is unknown");
            return;
        }

        if (result == null || result.outcome() == null) {
            stateService.retryOrMarkManualReview(operation, "POST_MALFORMED",
                    "Force-activation request returned no valid result");
            return;
        }
        switch (result.outcome()) {
            case SUCCESS -> finalizeOrReview(operation, result);
            case UNKNOWN, NOT_FOUND -> stateService.retryOrMarkManualReview(operation, result.errorCode(),
                    "Force-activation request result is unknown");
            case IDEMPOTENCY_CONFLICT -> stateService.markManualReview(operation,
                    "IDEMPOTENCY_CONFLICT",
                    "Force-activation requestId conflicts with a different remote request");
            case DEFINITIVE_REJECT -> stateService.markManualReview(operation, "DEFINITIVE_REJECT",
                    "CORS rejected force activation and requires manual review");
        }
    }

    private void finalizeOrReview(CorsOperation operation, CorsForceActivationResult result) {
        try {
            finalizeService.finalizeSuccess(operation.getId(), operation.getVersion(), result);
        } catch (RuntimeException exception) {
            log.error("Remote force-activation success could not be finalized; operationId={} requestId={} "
                            + "serviceAccountId={} outcome=SUCCESS errorCode={}",
                    operation.getId(), operation.getRequestId(), operation.getServiceAccountId(),
                    "LOCAL_FINALIZE_FAILED");
            stateService.markManualReview(operation, "LOCAL_FINALIZE_FAILED",
                    "CORS reported success but local finalize failed; manual review is required");
        }
    }
}
