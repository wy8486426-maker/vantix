package com.sinognss.cloud.vantix.application.cors.account;

import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountSnapshot;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountCorsSnapshotUpdate;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorsAccountStateApplyServiceTest {
    private static final ZoneOffset OFFSET = ZoneOffset.ofHours(8);
    private static final OffsetDateTime CREATED_AT = at(8, 0);
    private static final OffsetDateTime FIRST_UPDATE = at(9, 0);
    private static final OffsetDateTime SECOND_UPDATE = at(10, 0);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 14, 10, 30);

    @Mock
    private ServiceAccountMapper accountMapper;

    private CorsAccountStateApplyService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-14T02:30:00Z"), ZoneId.of("Asia/Shanghai"));
        service = new CorsAccountStateApplyService(accountMapper);
    }

    @Test
    void waitingActivationCopiesCorsActivationAndExpiryTimes() {
        ServiceAccount local = local("WAITING_ACTIVATION", null, null, FIRST_UPDATE.toLocalDateTime());
        OffsetDateTime activatedAt = at(9, 30);
        OffsetDateTime expireAt = at(11, 30);
        CorsAccountSnapshot remote = snapshot("ACTIVE", "ACTIVE", activatedAt, expireAt, SECOND_UPDATE);
        when(accountMapper.updateCorsSnapshot(any())).thenReturn(1);

        CorsAccountStateApplyOutcome outcome = apply(local, remote);

        assertEquals(CorsAccountStateApplyOutcome.UPDATED, outcome);
        ServiceAccountCorsSnapshotUpdate update = captureUpdate();
        assertEquals("ACTIVE", update.corsStatus());
        assertEquals("ACTIVE", update.corsActivationStatus());
        assertEquals(activatedAt.toLocalDateTime(), update.activatedAt());
        assertEquals(expireAt.toLocalDateTime(), update.expireAt());
        assertEquals(CREATED_AT.toLocalDateTime(), update.corsCreatedAt());
        assertEquals(SECOND_UPDATE.toLocalDateTime(), update.corsUpdatedAt());
        assertEquals(NOW, update.lastSyncAt());
        assertEquals(NOW, update.statusSyncLastAttemptAt());
        assertEquals(NOW.plusMinutes(10), update.statusSyncNextAt());
        assertEquals(NOW, update.updatedAt());
        assertEquals(41L, update.id());
        assertEquals(3L, update.expectedVersion());
    }

    @Test
    void newerActiveSnapshotCanChangeAccountStatusAndExpiry() {
        ServiceAccount local = local("ACTIVE", "ACTIVE", at(8, 30).toLocalDateTime(),
                FIRST_UPDATE.toLocalDateTime());
        CorsAccountSnapshot remote = snapshot("DISABLED", "ACTIVE", at(8, 30), at(12, 0), SECOND_UPDATE);
        when(accountMapper.updateCorsSnapshot(any())).thenReturn(1);

        assertEquals(CorsAccountStateApplyOutcome.UPDATED, apply(local, remote));
        ServiceAccountCorsSnapshotUpdate update = captureUpdate();
        assertEquals("DISABLED", update.corsStatus());
        assertEquals(at(12, 0).toLocalDateTime(), update.expireAt());
    }

    @Test
    void olderRemoteSnapshotIsIgnoredWithoutWriting() {
        ServiceAccount local = local("ACTIVE", "ACTIVE", at(8, 30).toLocalDateTime(),
                SECOND_UPDATE.toLocalDateTime());

        when(accountMapper.updateStatusSyncSuccess(any())).thenReturn(1);

        assertEquals(CorsAccountStateApplyOutcome.STALE_IGNORED,
                apply(local, snapshot("DISABLED", "ACTIVE", at(8, 30), at(12, 0), FIRST_UPDATE)));

        verify(accountMapper, never()).updateCorsSnapshot(any());
        ArgumentCaptor<com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountStatusSyncScheduleUpdate> captor =
                ArgumentCaptor.forClass(com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountStatusSyncScheduleUpdate.class);
        verify(accountMapper).updateStatusSyncSuccess(captor.capture());
        assertEquals(NOW, captor.getValue().lastSyncAt());
        assertEquals(NOW, captor.getValue().lastAttemptAt());
        assertEquals(NOW.plusMinutes(10), captor.getValue().nextAt());
        assertEquals(0, captor.getValue().failureCount());
    }

    @Test
    void equalTimestampAndEqualBusinessStateIsIdempotentAndRefreshesSyncMetadata() {
        ServiceAccount local = local("ACTIVE", "ACTIVE", at(8, 30).toLocalDateTime(),
                SECOND_UPDATE.toLocalDateTime());
        CorsAccountSnapshot remote = snapshot("ACTIVE", "ACTIVE", at(8, 30), at(11, 0), SECOND_UPDATE);
        when(accountMapper.updateCorsSnapshot(any())).thenReturn(1);

        assertEquals(CorsAccountStateApplyOutcome.IDEMPOTENT_NOOP, apply(local, remote));
        ServiceAccountCorsSnapshotUpdate update = captureUpdate();
        assertEquals(NOW, update.lastSyncAt());
        assertEquals(NOW, update.statusSyncLastAttemptAt());
        assertEquals(NOW.plusMinutes(10), update.statusSyncNextAt());
        assertEquals(SECOND_UPDATE.toLocalDateTime(), update.corsUpdatedAt());
    }

    @Test
    void equalTimestampWithDifferentBusinessStateIsInconsistent() {
        ServiceAccount local = local("ACTIVE", "ACTIVE", at(8, 30).toLocalDateTime(),
                SECOND_UPDATE.toLocalDateTime());

        assertEquals(CorsAccountStateApplyOutcome.INCONSISTENT,
                apply(local, snapshot("DISABLED", "ACTIVE", at(8, 30), at(11, 0), SECOND_UPDATE)));

        verify(accountMapper, never()).updateCorsSnapshot(any());
    }

    @Test
    void mismatchedCorsAccountIdIsInconsistent() {
        ServiceAccount local = local("ACTIVE", "ACTIVE", at(8, 30).toLocalDateTime(),
                FIRST_UPDATE.toLocalDateTime());

        assertEquals(CorsAccountStateApplyOutcome.INCONSISTENT,
                apply(local, snapshot("ACTIVE", "ACTIVE", at(8, 30), at(11, 0),
                        SECOND_UPDATE, "another-id", "account")));

        verify(accountMapper, never()).updateCorsSnapshot(any());
    }

    @Test
    void mismatchedAccountNameIsInconsistent() {
        ServiceAccount local = local("ACTIVE", "ACTIVE", at(8, 30).toLocalDateTime(),
                FIRST_UPDATE.toLocalDateTime());

        assertEquals(CorsAccountStateApplyOutcome.INCONSISTENT,
                apply(local, snapshot("ACTIVE", "ACTIVE", at(8, 30), at(11, 0),
                        SECOND_UPDATE, "cors-id", "renamed-account")));

        verify(accountMapper, never()).updateCorsSnapshot(any());
    }

    @Test
    void activationAfterExpiryIsRejected() {
        ServiceAccount local = local("WAITING_ACTIVATION", null, null, FIRST_UPDATE.toLocalDateTime());

        assertEquals(CorsAccountStateApplyOutcome.INCONSISTENT,
                apply(local, snapshot("ACTIVE", "ACTIVE", at(11, 0), at(10, 0), SECOND_UPDATE)));

        verify(accountMapper, never()).updateCorsSnapshot(any());
    }

    @Test
    void casFailureIsReportedWithoutRetryingBlindly() {
        ServiceAccount local = local("WAITING_ACTIVATION", null, null, FIRST_UPDATE.toLocalDateTime());
        when(accountMapper.updateCorsSnapshot(any())).thenReturn(0);

        assertEquals(CorsAccountStateApplyOutcome.CONCURRENT_MODIFICATION,
                apply(local, snapshot("ACTIVE", "ACTIVE", at(9, 30), at(11, 30), SECOND_UPDATE)));

        verify(accountMapper).updateCorsSnapshot(any());
    }

    @Test
    void snapshotRequiresIdentityStatusesAndRemoteTimestamps() {
        assertThrows(IllegalArgumentException.class,
                () -> new CorsAccountSnapshot(" ", "account", "ACTIVE", "ACTIVE",
                        null, null, CREATED_AT, SECOND_UPDATE));
        assertThrows(NullPointerException.class,
                () -> new CorsAccountSnapshot("id", "account", "ACTIVE", "ACTIVE",
                        null, null, null, SECOND_UPDATE));
        assertThrows(NullPointerException.class,
                () -> new CorsAccountSnapshot("id", "account", "ACTIVE", "ACTIVE",
                        null, null, CREATED_AT, null));
    }

    private CorsAccountStateApplyOutcome apply(ServiceAccount local, CorsAccountSnapshot remote) {
        return service.apply(local, remote, new AccountStatusSyncSuccessSchedule(NOW, NOW.plusMinutes(10)));
    }

    private ServiceAccountCorsSnapshotUpdate captureUpdate() {
        ArgumentCaptor<ServiceAccountCorsSnapshotUpdate> captor =
                ArgumentCaptor.forClass(ServiceAccountCorsSnapshotUpdate.class);
        verify(accountMapper).updateCorsSnapshot(captor.capture());
        return captor.getValue();
    }

    private static ServiceAccount local(String activationStatus, String status,
                                        LocalDateTime activatedAt, LocalDateTime corsUpdatedAt) {
        ServiceAccount account = new ServiceAccount();
        account.setId(41L);
        account.setCorsAccountId("cors-id");
        account.setAccount("account");
        account.setVersion(3L);
        account.setStatusSyncFailureCount(0);
        account.setCorsStatus(status);
        account.setCorsActivationStatus(activationStatus);
        account.setActivatedAt(activatedAt);
        account.setExpireAt(activatedAt == null ? null : at(11, 0).toLocalDateTime());
        account.setCorsUpdatedAt(corsUpdatedAt);
        return account;
    }

    private static CorsAccountSnapshot snapshot(String status, String activationStatus,
                                                OffsetDateTime activatedAt, OffsetDateTime expireAt,
                                                OffsetDateTime updatedAt) {
        return snapshot(status, activationStatus, activatedAt, expireAt, updatedAt, "cors-id", "account");
    }

    private static CorsAccountSnapshot snapshot(String status, String activationStatus,
                                                OffsetDateTime activatedAt, OffsetDateTime expireAt,
                                                OffsetDateTime updatedAt, String accountId, String account) {
        return new CorsAccountSnapshot(accountId, account, status, activationStatus,
                activatedAt, expireAt, CREATED_AT, updatedAt);
    }

    private static OffsetDateTime at(int hour, int minute) {
        return OffsetDateTime.of(2026, 9, 14, hour, minute, 0, 0, OFFSET);
    }
}
