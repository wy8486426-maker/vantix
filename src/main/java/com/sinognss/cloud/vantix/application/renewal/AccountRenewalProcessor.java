package com.sinognss.cloud.vantix.application.renewal;

import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.domain.renewal.AccountRenewal;
import com.sinognss.cloud.vantix.domain.renewal.AccountRenewalStatus;
import com.sinognss.cloud.vantix.domain.config.DurationUnit;
import com.sinognss.cloud.vantix.integration.cors.CorsOutcome;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountQueryOutcome;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountRenewalGateway;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountRenewalRequest;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountRenewalResult;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountSnapshot;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountStatusGateway;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountStatusResult;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class AccountRenewalProcessor {
    private static final Logger log = LoggerFactory.getLogger(AccountRenewalProcessor.class);
    private static final String ACTIVE = "ACTIVE";

    private final AccountRenewalClaimService claimService;
    private final AccountRenewalStateService stateService;
    private final AccountRenewalFinalizeService finalizeService;
    private final ServiceAccountMapper accountMapper;
    private final CorsAccountStatusGateway statusGateway;
    private final CorsAccountRenewalGateway renewalGateway;

    public AccountRenewalProcessor(AccountRenewalClaimService claimService,
                                   AccountRenewalStateService stateService,
                                   AccountRenewalFinalizeService finalizeService,
                                   ServiceAccountMapper accountMapper,
                                   CorsAccountStatusGateway statusGateway,
                                   CorsAccountRenewalGateway renewalGateway) {
        this.claimService = claimService;
        this.stateService = stateService;
        this.finalizeService = finalizeService;
        this.accountMapper = accountMapper;
        this.statusGateway = statusGateway;
        this.renewalGateway = renewalGateway;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void process(Long operationId) {
        ClaimedAccountRenewal claim = claimService.claim(operationId);
        if (claim == null || claim.operation() == null) {
            return;
        }
        CorsOperation operation = claim.operation();
        AccountRenewal renewal = claim.renewal();
        if (!hasRenewalIdentity(operation, renewal)) {
            stateService.markManualReview(operation, renewal, "OPERATION_RENEWAL_MISMATCH",
                    "Renewal operation identity is inconsistent");
            return;
        }
        ServiceAccount account = accountMapper.selectById(renewal.getServiceAccountId());
        if (!hasAccountIdentity(account, operation, renewal)) {
            stateService.markManualReview(operation, renewal, "OPERATION_ACCOUNT_MISMATCH",
                    "Renewal operation account identity is inconsistent");
            return;
        }

        if (claim.queryFirst() && queryRenewalFirst(operation, renewal, account)) {
            return;
        }
        if (!preflightAndRenew(operation, renewal, account)) {
            return;
        }
    }

    /** Returns true when this retry has been completely handled by its query result. */
    private boolean queryRenewalFirst(CorsOperation operation, AccountRenewal renewal, ServiceAccount account) {
        CorsAccountRenewalResult result;
        try {
            result = renewalGateway.queryRenewal(operation.getRequestId());
        } catch (RuntimeException exception) {
            logUnknown("renewal-query", operation, exception);
            stateService.retryOrMarkManualReview(operation, renewal, "QUERY_UNKNOWN",
                    "CORS renewal query result is unknown");
            return true;
        }
        if (result == null || result.outcome() == null) {
            stateService.retryOrMarkManualReview(operation, renewal, "QUERY_MALFORMED",
                    "CORS renewal query returned no valid result");
            return true;
        }
        if (result.requestId() != null && !operation.getRequestId().equals(result.requestId())) {
            stateService.markManualReview(operation, renewal, "QUERY_REQUEST_ID_MISMATCH",
                    "CORS renewal query response requestId does not match the original request");
            return true;
        }
        return switch (result.outcome()) {
            case SUCCESS -> {
                finalizeOrReview(operation, renewal, account, result);
                yield true;
            }
            case NOT_FOUND -> false;
            case UNKNOWN -> {
                stateService.retryOrMarkManualReview(operation, renewal,
                        errorCode(result.errorCode(), "QUERY_UNKNOWN"),
                        "CORS renewal query result is unknown");
                yield true;
            }
            case IDEMPOTENCY_CONFLICT -> {
                stateService.markManualReview(operation, renewal, "IDEMPOTENCY_CONFLICT",
                        "CORS renewal requestId conflicts with a different remote request");
                yield true;
            }
            case DEFINITIVE_REJECT -> {
                if (!operation.getRequestId().equals(result.requestId())) {
                    stateService.markManualReview(operation, renewal, "REJECT_REQUEST_ID_MISMATCH",
                            "CORS definitive rejection is not correlated to the original request");
                    yield true;
                }
                stateService.definitiveFail(operation, renewal,
                        errorCode(result.errorCode(), "CORS_RENEWAL_REJECTED"),
                        "CORS definitively rejected the renewal without side effects");
                yield true;
            }
        };
    }

    private boolean preflightAndRenew(CorsOperation operation, AccountRenewal renewal, ServiceAccount account) {
        CorsAccountStatusResult result;
        try {
            result = statusGateway.getAccount(account.getCorsAccountId());
        } catch (RuntimeException exception) {
            logUnknown("account-preflight", operation, exception);
            stateService.retryOrMarkManualReview(operation, renewal, "PREFLIGHT_UNKNOWN",
                    "CORS account preflight result is unknown");
            return false;
        }
        if (result == null || result.outcome() == null) {
            stateService.retryOrMarkManualReview(operation, renewal, "PREFLIGHT_MALFORMED",
                    "CORS account preflight returned no valid result");
            return false;
        }
        if (result.outcome() == CorsAccountQueryOutcome.UNKNOWN) {
            stateService.retryOrMarkManualReview(operation, renewal,
                    errorCode(result.errorCode(), "PREFLIGHT_UNKNOWN"),
                    "CORS account preflight result is unknown");
            return false;
        }
        if (result.outcome() == CorsAccountQueryOutcome.NOT_FOUND) {
            stateService.definitiveFail(operation, renewal, "CORS_ACCOUNT_NOT_FOUND",
                    "CORS account was not found before renewal");
            return false;
        }

        CorsAccountSnapshot snapshot = result.snapshot();
        if (snapshot == null) {
            stateService.retryOrMarkManualReview(operation, renewal, "PREFLIGHT_MALFORMED",
                    "CORS account preflight returned no account snapshot");
            return false;
        }
        if (!same(account.getCorsAccountId(), snapshot.accountId())
                || !same(account.getAccount(), snapshot.account())) {
            stateService.markManualReview(operation, renewal, "PREFLIGHT_IDENTITY_MISMATCH",
                    "CORS account preflight identity does not match the local account");
            return false;
        }
        if (!ACTIVE.equals(snapshot.activationStatus())) {
            stateService.definitiveFail(operation, renewal, "ACCOUNT_NOT_ACTIVATED",
                    "CORS account is not active for renewal");
            return false;
        }
        if ("DISABLED".equalsIgnoreCase(snapshot.accountStatus())) {
            stateService.definitiveFail(operation, renewal, "ACCOUNT_DISABLED",
                    "CORS account is disabled and cannot be renewed");
            return false;
        }
        if (snapshot.activatedAt() == null || snapshot.expireAt() == null || snapshot.updatedAt() == null
                || snapshot.activatedAt().isAfter(snapshot.expireAt())) {
            stateService.retryOrMarkManualReview(operation, renewal, "PREFLIGHT_MALFORMED",
                    "CORS account preflight snapshot is incomplete or invalid");
            return false;
        }

        CorsAccountRenewalRequest request;
        try {
            request = new CorsAccountRenewalRequest(operation.getRequestId(), account.getCorsAccountId(),
                    renewal.getDurationValue(), DurationUnit.valueOf(renewal.getDurationUnit()));
        } catch (RuntimeException exception) {
            stateService.markManualReview(operation, renewal, "RENEWAL_SNAPSHOT_INVALID",
                    "Stored renewal request snapshot is invalid");
            return false;
        }
        sendRenewal(operation, renewal, account, request);
        return true;
    }

    private void sendRenewal(CorsOperation operation, AccountRenewal renewal, ServiceAccount account,
                             CorsAccountRenewalRequest request) {
        CorsAccountRenewalResult result;
        try {
            result = renewalGateway.renew(request);
        } catch (RuntimeException exception) {
            logUnknown("renewal-post", operation, exception);
            stateService.retryOrMarkManualReview(operation, renewal, "POST_UNKNOWN",
                    "CORS renewal request result is unknown");
            return;
        }
        if (result == null || result.outcome() == null) {
            stateService.retryOrMarkManualReview(operation, renewal, "POST_MALFORMED",
                    "CORS renewal request returned no valid result");
            return;
        }
        if (result.requestId() != null && !operation.getRequestId().equals(result.requestId())) {
            stateService.markManualReview(operation, renewal, "POST_REQUEST_ID_MISMATCH",
                    "CORS renewal response requestId does not match the original request");
            return;
        }
        switch (result.outcome()) {
            case SUCCESS -> finalizeOrReview(operation, renewal, account, result);
            case UNKNOWN, NOT_FOUND -> stateService.retryOrMarkManualReview(operation, renewal,
                    errorCode(result.errorCode(), "POST_UNKNOWN"),
                    "CORS renewal request result is unknown");
            case IDEMPOTENCY_CONFLICT -> stateService.markManualReview(operation, renewal,
                    "IDEMPOTENCY_CONFLICT",
                    "CORS renewal requestId conflicts with a different remote request");
            case DEFINITIVE_REJECT -> {
                if (!operation.getRequestId().equals(result.requestId())) {
                    stateService.markManualReview(operation, renewal, "REJECT_REQUEST_ID_MISMATCH",
                            "CORS definitive rejection is not correlated to the original request");
                } else {
                    stateService.definitiveFail(operation, renewal,
                            errorCode(result.errorCode(), "CORS_RENEWAL_REJECTED"),
                            "CORS definitively rejected the renewal without side effects");
                }
            }
        }
    }

    private void finalizeOrReview(CorsOperation operation, AccountRenewal renewal,
                                  ServiceAccount account, CorsAccountRenewalResult result) {
        if (!isValidSuccess(operation, renewal, account, result)) {
            stateService.markManualReview(operation, renewal, "SUCCESS_RESPONSE_MISMATCH",
                    "CORS reported renewal success with an inconsistent response");
            return;
        }
        try {
            finalizeService.finalizeSuccess(operation.getId(), operation.getVersion(), result);
        } catch (RuntimeException exception) {
            log.error("Remote renewal success could not be finalized; operationId={} requestId={} "
                            + "serviceAccountId={} outcome=MANUAL_REVIEW errorCode={}",
                    operation.getId(), operation.getRequestId(), operation.getServiceAccountId(),
                    "LOCAL_FINALIZE_FAILED");
            stateService.markManualReviewAfterFinalizeFailure(operation, renewal,
                    "LOCAL_FINALIZE_FAILED",
                    "CORS reported renewal success but local finalize failed; manual review is required");
        }
    }

    private static boolean isValidSuccess(CorsOperation operation, AccountRenewal renewal,
                                          ServiceAccount account, CorsAccountRenewalResult result) {
        if (result == null || result.outcome() != CorsOutcome.SUCCESS || result.account() == null
                || !same(operation.getRequestId(), result.requestId())
                || !same(renewal.getRequestId(), result.requestId())) {
            return false;
        }
        CorsAccountSnapshot snapshot = result.account();
        return same(account.getCorsAccountId(), snapshot.accountId())
                && same(account.getAccount(), snapshot.account())
                && ACTIVE.equals(snapshot.activationStatus())
                && !"DISABLED".equalsIgnoreCase(snapshot.accountStatus())
                && snapshot.activatedAt() != null && snapshot.expireAt() != null
                && snapshot.updatedAt() != null
                && !snapshot.activatedAt().isAfter(snapshot.expireAt());
    }

    private static boolean hasRenewalIdentity(CorsOperation operation, AccountRenewal renewal) {
        return renewal != null && renewal.getId() != null
                && AccountRenewalConstants.isOperation(operation, renewal.getId(), renewal.getServiceAccountId())
                && operation.getRequestId() != null
                && operation.getRequestId().equals(renewal.getRequestId())
                && AccountRenewalStatus.PROCESSING.equals(renewal.getStatus())
                && renewal.getServiceCodeId() != null;
    }

    private static boolean hasAccountIdentity(ServiceAccount account, CorsOperation operation,
                                              AccountRenewal renewal) {
        return account != null && account.getId() != null && account.getVersion() != null
                && account.getId().equals(operation.getServiceAccountId())
                && account.getId().equals(renewal.getServiceAccountId())
                && !blank(account.getCorsAccountId()) && !blank(account.getAccount());
    }

    private static void logUnknown(String operationName, CorsOperation operation, RuntimeException exception) {
        log.warn("CORS renewal call failed; step={} operationId={} requestId={} serviceAccountId={} "
                        + "outcome=UNKNOWN errorCode={}", operationName, operation.getId(),
                operation.getRequestId(), operation.getServiceAccountId(), exception.getClass().getSimpleName());
    }

    private static String errorCode(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static boolean same(String left, String right) {
        return left != null && left.equals(right);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
