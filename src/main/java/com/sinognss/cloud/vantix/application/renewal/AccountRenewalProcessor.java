package com.sinognss.cloud.vantix.application.renewal;

import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.domain.renewal.AccountRenewal;
import com.sinognss.cloud.vantix.domain.renewal.AccountRenewalStatus;
import com.sinognss.cloud.vantix.integration.cors.CorsOutcome;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountQueryOutcome;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountId;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountRenewalGateway;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountRenewalRequest;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountRenewalResult;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountSnapshot;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountStatusGateway;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountStatusResult;
import com.sinognss.cloud.vantix.integration.cors.account.CorsRenewalData;
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

        if (!preflightAndRenew(operation, renewal, account)) {
            return;
        }
    }

    private boolean preflightAndRenew(CorsOperation operation, AccountRenewal renewal, ServiceAccount account) {
        try {
            CorsAccountId.parse(account.getCorsAccountId());
        } catch (IllegalArgumentException exception) {
            stateService.markManualReview(operation, renewal, "CORS_ACCOUNT_ID_INVALID",
                    "服务账号缺少可解析的 CORS 账号标识");
            return false;
        }
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
        if (!ACTIVE.equals(snapshot.activationStatus())
                && !"EXPIRED".equals(snapshot.activationStatus())) {
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
            request = new CorsAccountRenewalRequest(
                    java.util.List.of(CorsAccountId.parse(account.getCorsAccountId())),
                    renewal.getDurationDays(), operation.getRequestId());
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
        if (result.data() == null) {
            stateService.retryOrMarkManualReview(operation, renewal, "CORS_RESULT_PENDING",
                    "CORS 续期请求已接受，但 Redis 结果暂不可用");
            return;
        }
        if (!hasValidRenewalData(result.data(), account.getAccount(), 1)) {
            stateService.markManualReview(operation, renewal, "CORS_RESULT_INVALID",
                    "CORS 续期返回的账号集合无效");
            return;
        }

        if (result.account() == null) {
            CorsAccountStatusResult status;
            try {
                status = statusGateway.getAccount(account.getCorsAccountId());
            } catch (RuntimeException exception) {
                logUnknown("renewal-success-status", operation, exception);
                stateService.retryOrMarkManualReview(operation, renewal, "POST_SUCCESS_STATUS_UNKNOWN",
                        "CORS 续期已返回成功，但账号最新状态暂不可确认");
                return;
            }
            if (status == null || status.outcome() == null || status.outcome() == CorsAccountQueryOutcome.UNKNOWN) {
                stateService.retryOrMarkManualReview(operation, renewal, "POST_SUCCESS_STATUS_UNKNOWN",
                        "CORS 续期已返回成功，但账号最新状态暂不可确认");
                return;
            }
            if (status.outcome() != CorsAccountQueryOutcome.SUCCESS || status.snapshot() == null) {
                stateService.markManualReview(operation, renewal, "POST_SUCCESS_STATUS_INVALID",
                        "CORS 续期已返回成功，但账号最新状态无效");
                return;
            }
            result = result.withAccount(status.snapshot());
        }
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

    private static boolean hasValidRenewalData(CorsRenewalData data, String expectedAccount, int expectedCount) {
        if (data == null || !"corsRenewal".equals(data.interfaceName())
                || data.corsNameList() == null || data.corsNameList().size() != expectedCount
                || data.corsNameList().stream().anyMatch(name -> name == null || name.isBlank()
                || name.length() > 128 || name.codePoints().anyMatch(Character::isISOControl))
                || data.corsNameList().stream().distinct().count() != data.corsNameList().size()) {
            return false;
        }
        return data.corsNameList().contains(expectedAccount);
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
