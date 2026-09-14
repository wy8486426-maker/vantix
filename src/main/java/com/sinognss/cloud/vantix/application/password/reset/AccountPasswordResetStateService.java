package com.sinognss.cloud.vantix.application.password.reset;

import com.sinognss.cloud.vantix.config.CorsAccountPasswordProperties;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.domain.password.AccountPasswordAction;
import com.sinognss.cloud.vantix.domain.password.AccountPasswordActionConstants;
import com.sinognss.cloud.vantix.infrastructure.mapper.AccountPasswordActionMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.CorsOperationMapper;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class AccountPasswordResetStateService {
    private final CorsOperationMapper operationMapper;
    private final AccountPasswordActionMapper actionMapper;
    private final CorsAccountPasswordProperties properties;
    private final Clock clock;

    public AccountPasswordResetStateService(CorsOperationMapper operationMapper,
                                            AccountPasswordActionMapper actionMapper,
                                            CorsAccountPasswordProperties properties,
                                            Clock clock) {
        this.operationMapper = operationMapper;
        this.actionMapper = actionMapper;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public boolean retryOrMarkManualReview(CorsOperation claimed, AccountPasswordAction suppliedAction,
                                           String errorCode) {
        LockedState current = lockClaimed(claimed, suppliedAction, true);
        if (current == null) {
            return false;
        }
        LocalDateTime now = now();
        int retryCount = increment(current.operation().getRetryCount());
        if (retryCount > properties.getMaxRetries()) {
            String code = AccountPasswordResetFailure.RETRY_EXHAUSTED;
            String message = AccountPasswordResetFailure.message(code);
            updateAction(current.action(), AccountPasswordActionConstants.MANUAL_REVIEW, code, message, now, now);
            requireOne(operationMapper.markManualReview(current.operation().getId(), current.operation().getVersion(),
                    code, message, now), "Password reset operation could not be moved to manual review");
            return true;
        }

        String code = retryableCode(errorCode);
        String message = AccountPasswordResetFailure.message(code);
        updateAction(current.action(), AccountPasswordActionConstants.PROCESSING, code, message, null, now);
        requireOne(operationMapper.scheduleRetry(current.operation().getId(), current.operation().getVersion(),
                retryCount, now.plus(retryDelay(retryCount)), code, message, now),
                "Password reset retry could not be scheduled");
        return true;
    }

    @Transactional
    public boolean definitiveFail(CorsOperation claimed, AccountPasswordAction suppliedAction, String errorCode) {
        LockedState current = lockClaimed(claimed, suppliedAction, true);
        if (current == null) {
            return false;
        }
        String code = definitiveCode(errorCode);
        LocalDateTime now = now();
        String message = AccountPasswordResetFailure.message(code);
        updateAction(current.action(), AccountPasswordActionConstants.FAILED, code, message, now, now);
        requireOne(operationMapper.markFailed(current.operation().getId(), current.operation().getVersion(),
                code, message, now), "Password reset operation could not be marked failed");
        return true;
    }

    @Transactional
    public boolean markManualReview(CorsOperation claimed, AccountPasswordAction suppliedAction, String errorCode) {
        return markManualReviewInCurrentTransaction(claimed, suppliedAction, errorCode);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markManualReviewAfterFinalizeFailure(CorsOperation claimed,
                                                        AccountPasswordAction suppliedAction) {
        return markManualReviewInCurrentTransaction(claimed, suppliedAction,
                AccountPasswordResetFailure.LOCAL_FINALIZE_FAILED);
    }

    @Transactional
    public boolean recoverStaleClaim(CorsOperation stale) {
        if (stale == null || stale.getId() == null || stale.getVersion() == null) {
            return false;
        }
        CorsOperation operation = operationMapper.selectByIdForUpdate(stale.getId());
        if (!sameClaim(operation, stale)) {
            return false;
        }
        AccountPasswordAction action = operation.getBizId() == null ? null
                : actionMapper.selectByIdForUpdate(operation.getBizId());
        if (!hasStrictIdentity(operation, action) || action.getVersion() == null) {
            return markManualReviewLocked(operation, action, AccountPasswordResetFailure.CLAIM_TIMEOUT_EXHAUSTED);
        }

        LocalDateTime now = now();
        int retryCount = increment(operation.getRetryCount());
        boolean exhausted = retryCount > properties.getMaxRetries();
        String code = exhausted ? AccountPasswordResetFailure.CLAIM_TIMEOUT_EXHAUSTED
                : AccountPasswordResetFailure.CLAIM_TIMEOUT;
        String message = AccountPasswordResetFailure.message(code);
        if (exhausted) {
            updateAction(action, AccountPasswordActionConstants.MANUAL_REVIEW, code, message, now, now);
        } else {
            updateAction(action, AccountPasswordActionConstants.PROCESSING, code, message, null, now);
        }
        requireOne(operationMapper.recoverClaimed(operation.getId(), operation.getVersion(),
                exhausted ? AccountPasswordResetConstants.MANUAL_REVIEW : AccountPasswordResetConstants.RETRY_WAIT,
                retryCount, exhausted ? null : now, code, message, now),
                "Stale password reset operation could not be recovered");
        return true;
    }

    private boolean markManualReviewInCurrentTransaction(CorsOperation claimed,
                                                         AccountPasswordAction suppliedAction,
                                                         String errorCode) {
        LockedState current = lockClaimed(claimed, suppliedAction, false);
        if (current == null) {
            return false;
        }
        return markManualReviewLocked(current.operation(), current.action(), manualCode(errorCode));
    }

    private boolean markManualReviewLocked(CorsOperation operation,
                                           AccountPasswordAction action,
                                           String errorCode) {
        LocalDateTime now = now();
        String code = manualCode(errorCode);
        String message = AccountPasswordResetFailure.message(code);
        if (action != null && action.getId() != null && operation.getBizId() != null
                && operation.getBizId().equals(action.getId())
                && AccountPasswordActionConstants.RESET.equals(action.getActionType())
                && operation.getServiceAccountId() != null
                && operation.getServiceAccountId().equals(action.getServiceAccountId())
                && action.getVersion() != null
                && AccountPasswordActionConstants.PROCESSING.equals(action.getStatus())) {
            updateAction(action, AccountPasswordActionConstants.MANUAL_REVIEW, code, message, now, now);
        }
        requireOne(operationMapper.markManualReview(operation.getId(), operation.getVersion(), code, message, now),
                "Password reset operation could not be moved to manual review");
        return true;
    }

    private LockedState lockClaimed(CorsOperation suppliedOperation,
                                    AccountPasswordAction suppliedAction,
                                    boolean strictActionIdentity) {
        if (suppliedOperation == null || suppliedOperation.getId() == null || suppliedOperation.getVersion() == null) {
            return null;
        }
        CorsOperation operation = operationMapper.selectByIdForUpdate(suppliedOperation.getId());
        if (!sameClaim(operation, suppliedOperation)) {
            return null;
        }
        AccountPasswordAction action = operation.getBizId() == null ? null
                : actionMapper.selectByIdForUpdate(operation.getBizId());
        if (action == null || action.getId() == null || !operation.getBizId().equals(action.getId())
                || !AccountPasswordActionConstants.RESET.equals(action.getActionType())) {
            if (strictActionIdentity) {
                return null;
            }
            if (suppliedAction != null && (suppliedAction.getId() == null
                    || !suppliedAction.getId().equals(operation.getBizId()))) {
                return null;
            }
            return new LockedState(operation, action);
        }
        if (suppliedAction != null && (suppliedAction.getId() == null
                || !suppliedAction.getId().equals(action.getId()))) {
            return null;
        }
        boolean actionIdentity = action.getServiceAccountId() != null
                && action.getServiceAccountId().equals(operation.getServiceAccountId())
                && action.getVersion() != null
                && AccountPasswordActionConstants.PROCESSING.equals(action.getStatus());
        boolean correlation = operation.getRequestId() != null
                && operation.getRequestId().equals(action.getRequestId());
        if ((!actionIdentity || strictActionIdentity && !correlation)
                || suppliedAction != null && (suppliedAction.getVersion() == null
                || !suppliedAction.getVersion().equals(action.getVersion()))) {
            if (strictActionIdentity) {
                return null;
            }
            // Identity drift itself needs review; retain the action when it is the row linked by biz_id.
            if (!actionIdentity) {
                return new LockedState(operation, null);
            }
        }
        return new LockedState(operation, action);
    }

    private void updateAction(AccountPasswordAction action, String status, String code,
                              String message, LocalDateTime completedAt, LocalDateTime now) {
        if (action == null || action.getId() == null || action.getVersion() == null) {
            throw new IllegalStateException("Password reset action identity is unavailable");
        }
        requireOne(actionMapper.transitionFromProcessing(action.getId(), action.getVersion(), status,
                code, message, completedAt, now), "Password reset action state could not be updated");
    }

    private Duration retryDelay(int retryCount) {
        Duration delay = properties.getRetryBaseDelay();
        Duration maximum = properties.getRetryMaxDelay();
        for (int attempt = 1; attempt < retryCount; attempt++) {
            if (delay.compareTo(maximum) >= 0 || delay.compareTo(maximum.dividedBy(2)) > 0) {
                return maximum;
            }
            try {
                delay = delay.multipliedBy(2);
            } catch (ArithmeticException ignored) {
                return maximum;
            }
            if (delay.compareTo(maximum) > 0) {
                return maximum;
            }
        }
        return delay;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
    }

    private static boolean sameClaim(CorsOperation current, CorsOperation claimed) {
        return current != null && claimed != null
                && AccountPasswordResetConstants.OPERATION_TYPE.equals(current.getOperationType())
                && AccountPasswordResetConstants.BIZ_TYPE.equals(current.getBizType())
                && AccountPasswordResetConstants.CLAIMED.equals(current.getStatus())
                && current.getId().equals(claimed.getId())
                && current.getVersion() != null && current.getVersion().equals(claimed.getVersion())
                && current.getBizId() != null && current.getBizId().equals(claimed.getBizId())
                && current.getRequestId() != null && current.getRequestId().equals(claimed.getRequestId())
                && current.getServiceAccountId() != null
                && current.getServiceAccountId().equals(claimed.getServiceAccountId());
    }

    private static boolean hasStrictIdentity(CorsOperation operation, AccountPasswordAction action) {
        return AccountPasswordResetConstants.isOperation(operation,
                action == null ? null : action.getId(), action == null ? null : action.getServiceAccountId())
                && AccountPasswordResetConstants.isAction(action, operation.getServiceAccountId(),
                operation.getRequestId());
    }

    private static String retryableCode(String code) {
        return AccountPasswordResetFailure.isRetryable(code) ? code : AccountPasswordResetFailure.POST_UNKNOWN;
    }

    private static String manualCode(String code) {
        return AccountPasswordResetFailure.isManualReview(code)
                ? code : AccountPasswordResetFailure.POST_IDENTITY_MISMATCH;
    }

    private static String definitiveCode(String code) {
        return AccountPasswordResetFailure.isDefinitiveFailure(code)
                ? code : AccountPasswordResetFailure.POST_DEFINITIVE_REJECT;
    }

    private static int increment(Integer value) {
        int current = value == null ? 0 : Math.max(0, value);
        return current == Integer.MAX_VALUE ? Integer.MAX_VALUE : current + 1;
    }

    private static void requireOne(int changed, String message) {
        if (changed != 1) {
            throw new IllegalStateException(message);
        }
    }

    private record LockedState(CorsOperation operation, AccountPasswordAction action) {
    }
}
