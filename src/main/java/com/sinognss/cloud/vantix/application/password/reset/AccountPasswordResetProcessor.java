package com.sinognss.cloud.vantix.application.password.reset;

import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.domain.password.AccountPasswordAction;
import com.sinognss.cloud.vantix.integration.cors.CorsOutcome;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountPasswordGateway;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountQueryOutcome;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountSnapshot;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountStatusGateway;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountStatusResult;
import com.sinognss.cloud.vantix.integration.cors.account.CorsPasswordResetRequest;
import com.sinognss.cloud.vantix.integration.cors.account.CorsPasswordResetResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class AccountPasswordResetProcessor {
    private final AccountPasswordResetClaimService claimService;
    private final AccountPasswordResetStateService stateService;
    private final AccountPasswordResetFinalizeService finalizeService;
    private final CorsAccountStatusGateway statusGateway;
    private final CorsAccountPasswordGateway passwordGateway;

    public AccountPasswordResetProcessor(AccountPasswordResetClaimService claimService,
                                         AccountPasswordResetStateService stateService,
                                         AccountPasswordResetFinalizeService finalizeService,
                                         CorsAccountStatusGateway statusGateway,
                                         CorsAccountPasswordGateway passwordGateway) {
        this.claimService = claimService;
        this.stateService = stateService;
        this.finalizeService = finalizeService;
        this.statusGateway = statusGateway;
        this.passwordGateway = passwordGateway;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void process(Long operationId) {
        ClaimedAccountPasswordReset claim = claimService.claim(operationId);
        if (claim == null || claim.operation() == null) {
            return;
        }
        CorsOperation operation = claim.operation();
        AccountPasswordAction action = claim.action();
        if (!hasClaimIdentity(operation, action)) {
            stateService.markManualReview(operation, action,
                    AccountPasswordResetFailure.POST_IDENTITY_MISMATCH);
            return;
        }

        if (claim.queryFirst() && queryFirst(operation, action)) {
            return;
        }
        if (!preflight(operation, action)) {
            return;
        }
        reset(operation, action);
    }

    /** Returns true when a query result has fully handled this retry. */
    private boolean queryFirst(CorsOperation operation, AccountPasswordAction action) {
        CorsPasswordResetResult result;
        try {
            result = passwordGateway.queryPasswordReset(operation.getRequestId());
        } catch (RuntimeException ignored) {
            stateService.retryOrMarkManualReview(operation, action, AccountPasswordResetFailure.QUERY_UNKNOWN);
            return true;
        }
        if (result == null || result.outcome() == null) {
            stateService.retryOrMarkManualReview(operation, action, AccountPasswordResetFailure.QUERY_MALFORMED);
            return true;
        }
        if (hasOptionalCorrelationMismatch(operation, action, result)) {
            stateService.markManualReview(operation, action,
                    AccountPasswordResetFailure.QUERY_IDENTITY_MISMATCH);
            return true;
        }
        return switch (result.outcome()) {
            case SUCCESS -> {
                if (!hasSuccessCorrelation(operation, action, result)) {
                    stateService.markManualReview(operation, action,
                            AccountPasswordResetFailure.QUERY_IDENTITY_MISMATCH);
                } else {
                    finalizeOrReview(operation, action, result);
                }
                yield true;
            }
            case NOT_FOUND -> false;
            case UNKNOWN -> {
                stateService.retryOrMarkManualReview(operation, action, AccountPasswordResetFailure.QUERY_UNKNOWN);
                yield true;
            }
            case IDEMPOTENCY_CONFLICT -> {
                stateService.markManualReview(operation, action,
                        AccountPasswordResetFailure.QUERY_IDEMPOTENCY_CONFLICT);
                yield true;
            }
            case DEFINITIVE_REJECT -> {
                stateService.definitiveFail(operation, action,
                        AccountPasswordResetFailure.QUERY_DEFINITIVE_REJECT);
                yield true;
            }
        };
    }

    private boolean preflight(CorsOperation operation, AccountPasswordAction action) {
        CorsAccountStatusResult result;
        try {
            result = statusGateway.getAccount(action.getCorsAccountId());
        } catch (RuntimeException ignored) {
            stateService.retryOrMarkManualReview(operation, action, AccountPasswordResetFailure.PREFLIGHT_UNKNOWN);
            return false;
        }
        if (result == null || result.outcome() == null) {
            stateService.retryOrMarkManualReview(operation, action, AccountPasswordResetFailure.PREFLIGHT_MALFORMED);
            return false;
        }
        if (result.outcome() == CorsAccountQueryOutcome.UNKNOWN) {
            stateService.retryOrMarkManualReview(operation, action, AccountPasswordResetFailure.PREFLIGHT_UNKNOWN);
            return false;
        }
        if (result.outcome() == CorsAccountQueryOutcome.NOT_FOUND) {
            stateService.definitiveFail(operation, action, AccountPasswordResetFailure.ACCOUNT_NOT_FOUND);
            return false;
        }
        CorsAccountSnapshot snapshot = result.snapshot();
        if (snapshot == null) {
            stateService.retryOrMarkManualReview(operation, action, AccountPasswordResetFailure.PREFLIGHT_MALFORMED);
            return false;
        }
        if (!same(action.getCorsAccountId(), snapshot.accountId())
                || !same(action.getAccount(), snapshot.account())) {
            stateService.markManualReview(operation, action,
                    AccountPasswordResetFailure.PREFLIGHT_IDENTITY_MISMATCH);
            return false;
        }
        return true;
    }

    private void reset(CorsOperation operation, AccountPasswordAction action) {
        CorsPasswordResetResult result;
        try {
            result = passwordGateway.resetPassword(
                    new CorsPasswordResetRequest(operation.getRequestId(), action.getCorsAccountId()));
        } catch (RuntimeException ignored) {
            stateService.retryOrMarkManualReview(operation, action, AccountPasswordResetFailure.POST_UNKNOWN);
            return;
        }
        if (result == null || result.outcome() == null) {
            stateService.retryOrMarkManualReview(operation, action, AccountPasswordResetFailure.POST_MALFORMED);
            return;
        }
        if (hasOptionalCorrelationMismatch(operation, action, result)) {
            stateService.markManualReview(operation, action, AccountPasswordResetFailure.POST_IDENTITY_MISMATCH);
            return;
        }
        switch (result.outcome()) {
            case SUCCESS -> {
                if (!hasSuccessCorrelation(operation, action, result)) {
                    stateService.markManualReview(operation, action,
                            AccountPasswordResetFailure.POST_IDENTITY_MISMATCH);
                } else {
                    finalizeOrReview(operation, action, result);
                }
            }
            case UNKNOWN, NOT_FOUND -> stateService.retryOrMarkManualReview(
                    operation, action, AccountPasswordResetFailure.POST_UNKNOWN);
            case IDEMPOTENCY_CONFLICT -> stateService.markManualReview(
                    operation, action, AccountPasswordResetFailure.POST_IDEMPOTENCY_CONFLICT);
            // The Gateway maps DEFINITIVE_REJECT only when it guarantees sideEffect=false.
            case DEFINITIVE_REJECT -> stateService.definitiveFail(
                    operation, action, AccountPasswordResetFailure.POST_DEFINITIVE_REJECT);
        }
    }

    private void finalizeOrReview(CorsOperation operation, AccountPasswordAction action,
                                  CorsPasswordResetResult result) {
        try {
            finalizeService.finalizeSuccess(operation, action, result);
        } catch (RuntimeException ignored) {
            // A remote success must never be retried as a new reset after local finalize fails.
            stateService.markManualReviewAfterFinalizeFailure(operation, action);
        }
    }

    private static boolean hasClaimIdentity(CorsOperation operation, AccountPasswordAction action) {
        return AccountPasswordResetConstants.isOperation(operation,
                action == null ? null : action.getId(), action == null ? null : action.getServiceAccountId())
                && AccountPasswordResetConstants.CLAIMED.equals(operation.getStatus())
                && operation.getVersion() != null
                && AccountPasswordResetConstants.isAction(action, operation.getServiceAccountId(),
                operation.getRequestId())
                && action.getVersion() != null
                && nonblank(action.getCorsAccountId()) && nonblank(action.getAccount());
    }

    private static boolean hasOptionalCorrelationMismatch(CorsOperation operation,
                                                          AccountPasswordAction action,
                                                          CorsPasswordResetResult result) {
        return result.requestId() != null && !same(operation.getRequestId(), result.requestId())
                || result.accountId() != null && !same(action.getCorsAccountId(), result.accountId());
    }

    private static boolean hasSuccessCorrelation(CorsOperation operation,
                                                 AccountPasswordAction action,
                                                 CorsPasswordResetResult result) {
        return result.outcome() == CorsOutcome.SUCCESS
                && same(operation.getRequestId(), result.requestId())
                && same(action.getRequestId(), result.requestId())
                && same(action.getCorsAccountId(), result.accountId());
    }

    private static boolean same(String left, String right) {
        return left != null && left.equals(right);
    }

    private static boolean nonblank(String value) {
        return value != null && !value.isBlank();
    }
}
