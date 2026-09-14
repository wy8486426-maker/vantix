package com.sinognss.cloud.vantix.application.cors.account;

import com.sinognss.cloud.vantix.config.CorsForceActivationProperties;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.infrastructure.mapper.CorsOperationMapper;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

public class AccountForceActivationStateService {
    private static final Pattern SAFE_ERROR_CODE = Pattern.compile("[A-Za-z0-9_.-]{1,64}");

    private final CorsOperationMapper operationMapper;
    private final CorsForceActivationProperties properties;
    private final Clock clock;

    public AccountForceActivationStateService(CorsOperationMapper operationMapper,
                                              CorsForceActivationProperties properties,
                                              Clock clock) {
        this.operationMapper = operationMapper;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public boolean retryOrMarkManualReview(CorsOperation claimed, String errorCode, String message) {
        CorsOperation current = currentClaim(claimed);
        if (current == null) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        int previous = current.getRetryCount() == null ? 0 : Math.max(0, current.getRetryCount());
        int retryCount = previous == Integer.MAX_VALUE ? Integer.MAX_VALUE : previous + 1;
        String code = safeCode(errorCode, "FORCE_ACTIVATION_UNKNOWN");
        String safeMessage = safeMessage(message, "Force activation result is unknown");
        if (retryCount > properties.getMaxRetries()) {
            return operationMapper.markManualReview(current.getId(), current.getVersion(),
                    code, safeMessage, now) == 1;
        }
        return operationMapper.scheduleRetry(current.getId(), current.getVersion(), retryCount,
                now.plus(retryDelay(retryCount)), code, safeMessage, now) == 1;
    }

    @Transactional
    public boolean markManualReview(CorsOperation claimed, String errorCode, String message) {
        CorsOperation current = currentClaim(claimed);
        if (current == null) {
            return false;
        }
        return operationMapper.markManualReview(current.getId(), current.getVersion(),
                safeCode(errorCode, "FORCE_ACTIVATION_REQUIRES_REVIEW"),
                safeMessage(message, "Force activation requires manual review"),
                LocalDateTime.now(clock)) == 1;
    }

    @Transactional
    public boolean markSucceeded(CorsOperation claimed) {
        CorsOperation current = currentClaim(claimed);
        if (current == null) {
            return false;
        }
        return operationMapper.markSucceeded(current.getId(), current.getVersion(),
                LocalDateTime.now(clock)) == 1;
    }

    private CorsOperation currentClaim(CorsOperation claimed) {
        if (claimed == null || claimed.getId() == null || claimed.getVersion() == null
                || claimed.getServiceAccountId() == null) {
            return null;
        }
        CorsOperation current = operationMapper.selectById(claimed.getId());
        if (!AccountForceActivationConstants.isOperationForAccount(current, claimed.getServiceAccountId())
                || !AccountForceActivationConstants.CLAIMED.equals(current.getStatus())
                || !claimed.getVersion().equals(current.getVersion())) {
            return null;
        }
        return current;
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
}
