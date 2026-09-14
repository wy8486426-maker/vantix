package com.sinognss.cloud.vantix.application.cors.account;

import com.sinognss.cloud.vantix.config.CorsAccountStatusSyncProperties;
import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountStatusSyncScheduleUpdate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class AccountStatusSyncScheduleService {
    private static final LocalDateTime MAX_MYSQL_DATETIME =
            LocalDateTime.of(9999, 12, 31, 23, 59, 59, 999_000_000);

    private final ServiceAccountMapper accountMapper;
    private final CorsAccountStatusSyncProperties properties;
    private final Clock clock;

    public AccountStatusSyncScheduleService(ServiceAccountMapper accountMapper,
                                            CorsAccountStatusSyncProperties properties,
                                            Clock clock) {
        this.accountMapper = accountMapper;
        this.properties = properties;
        this.clock = clock;
    }

    public AccountStatusSyncSuccessSchedule successSchedule() {
        LocalDateTime now = now();
        return new AccountStatusSyncSuccessSchedule(now, addDelay(now, properties.getStaleAfter()));
    }

    @Transactional
    public boolean markFailure(ServiceAccount local) {
        if (local == null || local.getId() == null || local.getVersion() == null) {
            return false;
        }
        int previousCount = Math.max(0, local.getStatusSyncFailureCount() == null
                ? 0 : local.getStatusSyncFailureCount());
        int failureCount = previousCount == Integer.MAX_VALUE ? Integer.MAX_VALUE : previousCount + 1;
        LocalDateTime now = now();
        LocalDateTime nextAt = addDelay(now, backoffForFailureCount(failureCount));
        ServiceAccountStatusSyncScheduleUpdate update = new ServiceAccountStatusSyncScheduleUpdate(
                local.getId(), local.getVersion(), null, now, nextAt, failureCount, now);
        return accountMapper.updateStatusSyncFailure(update) == 1;
    }

    Duration backoffForFailureCount(int failureCount) {
        Duration base = properties.getRetryBaseDelay();
        Duration maximum = properties.getRetryMaxDelay();
        Duration delay = base;
        for (int attempt = 1; attempt < Math.max(1, failureCount); attempt++) {
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

    private static LocalDateTime addDelay(LocalDateTime now, Duration delay) {
        try {
            return now.plus(delay).truncatedTo(ChronoUnit.MILLIS);
        } catch (DateTimeException | ArithmeticException ignored) {
            return MAX_MYSQL_DATETIME;
        }
    }
}
