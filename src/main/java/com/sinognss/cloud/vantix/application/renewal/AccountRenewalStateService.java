package com.sinognss.cloud.vantix.application.renewal;

import com.sinognss.cloud.vantix.config.CorsAccountRenewalProperties;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.domain.renewal.AccountRenewal;
import com.sinognss.cloud.vantix.domain.renewal.AccountRenewalStatus;
import com.sinognss.cloud.vantix.domain.servicecode.ProcessingType;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCode;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeStatus;
import com.sinognss.cloud.vantix.infrastructure.mapper.AccountRenewalMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.CorsOperationMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeMapper;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;

public class AccountRenewalStateService {
    private static final Pattern SAFE_ERROR_CODE = Pattern.compile("[A-Za-z0-9_.-]{1,64}");

    private final CorsOperationMapper operationMapper;
    private final AccountRenewalMapper renewalMapper;
    private final ServiceCodeMapper serviceCodeMapper;
    private final CorsAccountRenewalProperties properties;
    private final Clock clock;

    public AccountRenewalStateService(CorsOperationMapper operationMapper,
                                      AccountRenewalMapper renewalMapper,
                                      ServiceCodeMapper serviceCodeMapper,
                                      CorsAccountRenewalProperties properties,
                                      Clock clock) {
        this.operationMapper = operationMapper;
        this.renewalMapper = renewalMapper;
        this.serviceCodeMapper = serviceCodeMapper;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public boolean retryOrMarkManualReview(CorsOperation claimed, AccountRenewal renewal,
                                           String errorCode, String message) {
        LockedState current = lockClaimed(claimed, renewal, true);
        if (current == null) {
            return false;
        }

        LocalDateTime now = now();
        int retryCount = increment(current.operation().getRetryCount());
        String code = safeCode(errorCode, "ACCOUNT_RENEWAL_UNKNOWN");
        String safeMessage = safeMessage(message, "Account renewal result is unknown");
        if (retryCount > properties.getMaxRetries()) {
            requireOne(renewalMapper.markManualReview(current.renewal().getId(),
                    current.renewal().getVersion(), code, safeMessage, now),
                    "Account renewal could not be moved to manual review");
            requireOne(operationMapper.markManualReview(current.operation().getId(),
                    current.operation().getVersion(), code, safeMessage, now),
                    "CORS operation could not be moved to manual review");
            return true;
        }

        requireOne(renewalMapper.updateRetryError(current.renewal().getId(),
                current.renewal().getVersion(), code, safeMessage, now),
                "Account renewal retry error could not be recorded");
        requireOne(operationMapper.scheduleRetry(current.operation().getId(),
                current.operation().getVersion(), retryCount, now.plus(retryDelay(retryCount)),
                code, safeMessage, now), "CORS operation retry could not be scheduled");
        return true;
    }

    @Transactional
    public boolean markManualReview(CorsOperation claimed, AccountRenewal renewal,
                                   String errorCode, String message) {
        LockedState current = lockClaimed(claimed, renewal, false);
        if (current == null) {
            return false;
        }
        LocalDateTime now = now();
        String code = safeCode(errorCode, "ACCOUNT_RENEWAL_REQUIRES_REVIEW");
        String safeMessage = safeMessage(message, "Account renewal requires manual review");
        if (current.renewal() != null) {
            requireOne(renewalMapper.markManualReview(current.renewal().getId(),
                    current.renewal().getVersion(), code, safeMessage, now),
                    "Account renewal could not be moved to manual review");
        }
        requireOne(operationMapper.markManualReview(current.operation().getId(),
                current.operation().getVersion(), code, safeMessage, now),
                "CORS operation could not be moved to manual review");
        return true;
    }

    @Transactional
    public boolean definitiveFail(CorsOperation claimed, AccountRenewal renewal,
                                 String errorCode, String message) {
        LockedState current = lockClaimed(claimed, renewal, true);
        if (current == null) {
            return false;
        }
        if (!hasStrictIdentity(current.operation(), current.renewal())) {
            throw new IllegalStateException("Renewal operation identity is inconsistent");
        }
        ServiceCode code = serviceCodeMapper.selectByIdForUpdate(current.renewal().getServiceCodeId());
        if (!isReservedForRenewal(code, current.renewal())) {
            throw new IllegalStateException("Renewal service code reservation changed before release");
        }

        LocalDateTime now = now();
        String error = safeCode(errorCode, "ACCOUNT_RENEWAL_REJECTED");
        String safeMessage = safeMessage(message, "CORS definitively rejected account renewal");
        requireOne(serviceCodeMapper.releaseRenewalCode(code.getId(), current.renewal().getRequestId(),
                code.getVersion(), now), "Renewal service code could not be released");
        requireOne(renewalMapper.fail(current.renewal().getId(), current.renewal().getVersion(),
                error, safeMessage, now), "Account renewal could not be marked failed");
        requireOne(operationMapper.markFailed(current.operation().getId(), current.operation().getVersion(),
                error, safeMessage, now), "CORS operation could not be marked failed");
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markManualReviewAfterFinalizeFailure(CorsOperation claimed, AccountRenewal renewal,
                                                        String errorCode, String message) {
        LockedState current = lockClaimed(claimed, renewal, false);
        if (current == null) {
            return false;
        }
        LocalDateTime now = now();
        String code = safeCode(errorCode, "LOCAL_FINALIZE_FAILED");
        String safeMessage = safeMessage(message,
                "CORS reported renewal success but local finalize failed; manual review is required");
        if (current.renewal() != null) {
            requireOne(renewalMapper.markManualReview(current.renewal().getId(),
                    current.renewal().getVersion(), code, safeMessage, now),
                    "Account renewal could not be moved to manual review after finalize failure");
        }
        requireOne(operationMapper.markManualReview(current.operation().getId(),
                current.operation().getVersion(), code, safeMessage, now),
                "CORS operation could not be moved to manual review after finalize failure");
        return true;
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
        AccountRenewal renewal = operation.getBizId() == null ? null
                : renewalMapper.selectByIdForUpdate(operation.getBizId());
        if (!hasRenewalLink(operation, renewal) || renewal.getVersion() == null
                || !AccountRenewalStatus.PROCESSING.equals(renewal.getStatus())) {
            return false;
        }

        LocalDateTime now = now();
        int retryCount = increment(operation.getRetryCount());
        boolean exhausted = retryCount > properties.getMaxRetries();
        String error = exhausted ? "CLAIM_TIMEOUT_EXHAUSTED" : "CLAIM_TIMEOUT";
        String message = exhausted
                ? "Account renewal requires manual review after claim timeout"
                : "Stale account renewal claim recovered; next attempt will query requestId";
        if (exhausted) {
            requireOne(renewalMapper.markManualReview(renewal.getId(), renewal.getVersion(),
                    error, message, now), "Account renewal could not be moved to manual review");
        } else {
            requireOne(renewalMapper.updateRetryError(renewal.getId(), renewal.getVersion(),
                    error, message, now), "Stale account renewal timeout could not be recorded");
        }
        requireOne(operationMapper.recoverClaimed(operation.getId(), operation.getVersion(),
                exhausted ? AccountRenewalConstants.MANUAL_REVIEW : AccountRenewalConstants.RETRY_WAIT,
                retryCount, exhausted ? null : now, error, message, now),
                "Stale CORS renewal operation could not be recovered");
        return true;
    }

    private LockedState lockClaimed(CorsOperation claimed, AccountRenewal suppliedRenewal,
                                    boolean requireStrictIdentity) {
        if (claimed == null || claimed.getId() == null || claimed.getVersion() == null) {
            return null;
        }
        CorsOperation current = operationMapper.selectByIdForUpdate(claimed.getId());
        if (!sameClaim(current, claimed)) {
            return null;
        }
        AccountRenewal renewal = current.getBizId() == null ? null
                : renewalMapper.selectByIdForUpdate(current.getBizId());
        if (renewal == null) {
            return requireStrictIdentity ? null : new LockedState(current, null);
        }
        if (!hasRenewalRow(current, renewal)
                || requireStrictIdentity && !hasRenewalLink(current, renewal)
                || !AccountRenewalStatus.PROCESSING.equals(renewal.getStatus())
                || renewal.getVersion() == null
                || suppliedRenewal != null && (!renewal.getId().equals(suppliedRenewal.getId())
                || suppliedRenewal.getVersion() == null
                || !renewal.getVersion().equals(suppliedRenewal.getVersion()))) {
            return null;
        }
        if (requireStrictIdentity && !hasStrictIdentity(current, renewal)) {
            return null;
        }
        return new LockedState(current, renewal);
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
        return current != null && claimed != null && isRenewalOperation(current)
                && AccountRenewalConstants.CLAIMED.equals(current.getStatus())
                && current.getId().equals(claimed.getId())
                && current.getVersion() != null && current.getVersion().equals(claimed.getVersion())
                && current.getRequestId() != null
                && current.getRequestId().equals(claimed.getRequestId());
    }

    private static boolean hasRenewalLink(CorsOperation operation, AccountRenewal renewal) {
        return hasRenewalRow(operation, renewal)
                && operation.getRequestId().equals(renewal.getRequestId());
    }

    private static boolean hasRenewalRow(CorsOperation operation, AccountRenewal renewal) {
        return operation != null && renewal != null && isRenewalOperation(operation)
                && operation.getBizId().equals(renewal.getId());
    }

    private static boolean hasStrictIdentity(CorsOperation operation, AccountRenewal renewal) {
        return hasRenewalLink(operation, renewal) && operation.getServiceAccountId() != null
                && operation.getServiceAccountId().equals(renewal.getServiceAccountId())
                && renewal.getServiceCodeId() != null;
    }

    private static boolean isRenewalOperation(CorsOperation operation) {
        return operation != null
                && AccountRenewalConstants.OPERATION_TYPE.equals(operation.getOperationType())
                && AccountRenewalConstants.BIZ_TYPE.equals(operation.getBizType())
                && operation.getBizId() != null && operation.getRequestId() != null
                && !operation.getRequestId().isBlank();
    }

    private static boolean isReservedForRenewal(ServiceCode code, AccountRenewal renewal) {
        return code != null && code.getId() != null && code.getVersion() != null
                && renewal.getServiceCodeId().equals(code.getId())
                && code.getStatus() == ServiceCodeStatus.PROCESSING
                && code.getProcessingType() == ProcessingType.RENEWAL
                && renewal.getRequestId().equals(code.getProcessingRequestId());
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

    private static String safeCode(String value, String fallback) {
        if (value == null || !SAFE_ERROR_CODE.matcher(value.trim()).matches()) {
            return fallback;
        }
        return value.trim();
    }

    private static String safeMessage(String value, String fallback) {
        String result = value == null || value.isBlank() ? fallback : value.trim();
        return result.length() <= 1024 ? result : result.substring(0, 1024);
    }

    private record LockedState(CorsOperation operation, AccountRenewal renewal) {
    }
}
