package com.sinognss.cloud.vantix.application.cors.account;

import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountSnapshot;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountCorsSnapshotUpdate;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;

public class CorsAccountStateApplyService {
    private static final ZoneId CORS_ZONE = ZoneId.of("Asia/Shanghai");

    private final ServiceAccountMapper accountMapper;
    private final Clock clock;

    public CorsAccountStateApplyService(ServiceAccountMapper accountMapper, Clock clock) {
        this.accountMapper = accountMapper;
        this.clock = clock;
    }

    @Transactional
    public CorsAccountStateApplyOutcome apply(ServiceAccount local, CorsAccountSnapshot remote) {
        if (!isConsistentIdentity(local, remote) || hasInvalidTimeRange(remote)) {
            return CorsAccountStateApplyOutcome.INCONSISTENT;
        }

        LocalDateTime remoteUpdatedAt = local(remote.updatedAt());
        boolean idempotent = false;
        if (local.getCorsUpdatedAt() != null) {
            int timestampOrder = remoteUpdatedAt.compareTo(local.getCorsUpdatedAt());
            if (timestampOrder < 0) {
                return CorsAccountStateApplyOutcome.STALE_IGNORED;
            }
            if (timestampOrder == 0) {
                if (!sameBusinessState(local, remote)) {
                    return CorsAccountStateApplyOutcome.INCONSISTENT;
                }
                idempotent = true;
            }
        }

        LocalDateTime now = LocalDateTime.now(clock);
        ServiceAccountCorsSnapshotUpdate update = new ServiceAccountCorsSnapshotUpdate(
                local.getId(), local.getVersion(), remote.accountStatus(), remote.activationStatus(),
                local(remote.activatedAt()), local(remote.expireAt()), local(remote.createdAt()),
                remoteUpdatedAt, now, now);
        if (accountMapper.updateCorsSnapshot(update) != 1) {
            return CorsAccountStateApplyOutcome.CONCURRENT_MODIFICATION;
        }
        return idempotent ? CorsAccountStateApplyOutcome.IDEMPOTENT_NOOP
                : CorsAccountStateApplyOutcome.UPDATED;
    }

    private static boolean isConsistentIdentity(ServiceAccount local, CorsAccountSnapshot remote) {
        return local != null && remote != null
                && local.getId() != null && local.getVersion() != null
                && !blank(local.getCorsAccountId()) && local.getCorsAccountId().equals(remote.accountId())
                && !blank(local.getAccount()) && local.getAccount().equals(remote.account());
    }

    private static boolean hasInvalidTimeRange(CorsAccountSnapshot remote) {
        OffsetDateTime activatedAt = remote.activatedAt();
        OffsetDateTime expireAt = remote.expireAt();
        return activatedAt != null && expireAt != null && activatedAt.isAfter(expireAt);
    }

    private static boolean sameBusinessState(ServiceAccount local, CorsAccountSnapshot remote) {
        return Objects.equals(local.getCorsStatus(), remote.accountStatus())
                && Objects.equals(local.getCorsActivationStatus(), remote.activationStatus())
                && Objects.equals(local.getActivatedAt(), local(remote.activatedAt()))
                && Objects.equals(local.getExpireAt(), local(remote.expireAt()));
    }

    private static LocalDateTime local(OffsetDateTime value) {
        return value == null ? null : value.atZoneSameInstant(CORS_ZONE).toLocalDateTime();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
