package com.sinognss.cloud.vantix.application.cors.account;

import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountSnapshot;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountStatusGateway;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountStatusResult;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountStatusReconcileServiceTest {
    @Mock
    private ServiceAccountMapper accountMapper;
    @Mock
    private CorsAccountStatusGateway gateway;
    @Mock
    private CorsAccountStateApplyService applyService;
    @Mock
    private AccountStatusSyncScheduleService scheduleService;

    private AccountStatusReconcileService service;
    private ServiceAccount local;
    private CorsAccountSnapshot snapshot;
    private AccountStatusSyncSuccessSchedule successSchedule;

    @BeforeEach
    void setUp() {
        service = new AccountStatusReconcileService(accountMapper, gateway, applyService, scheduleService);
        local = new ServiceAccount();
        local.setId(17L);
        local.setCorsAccountId("cors-17");
        local.setAccount("account-17");
        local.setVersion(4L);
        local.setStatusSyncFailureCount(2);
        local.setCorsActivationStatus("WAITING_ACTIVATION");
        local.setLastSyncAt(null);
        OffsetDateTime now = OffsetDateTime.of(2026, 9, 14, 10, 0, 0, 0, ZoneOffset.ofHours(8));
        snapshot = new CorsAccountSnapshot("cors-17", "account-17", "ACTIVE", "ACTIVE",
                now, now.plusDays(30), now.minusDays(1), now);
        successSchedule = new AccountStatusSyncSuccessSchedule(
                now.toLocalDateTime(), now.toLocalDateTime().plusMinutes(10));
        lenient().when(accountMapper.selectById(17L)).thenReturn(local);
        lenient().when(scheduleService.successSchedule()).thenReturn(successSchedule);
        lenient().when(scheduleService.markFailure(local)).thenReturn(true);
    }

    @Test
    void successPassesSnapshotAndSuccessScheduleToUnifiedApplyService() {
        when(gateway.getAccount("cors-17")).thenReturn(CorsAccountStatusResult.success(snapshot));
        when(applyService.apply(local, snapshot, successSchedule)).thenReturn(CorsAccountStateApplyOutcome.UPDATED);

        assertEquals(AccountStatusReconcileOutcome.UPDATED, service.reconcileOne(17L));

        verify(applyService).apply(local, snapshot, successSchedule);
        verify(scheduleService, never()).markFailure(local);
    }

    @Test
    void notFoundSchedulesFailureWithoutApplyingSnapshot() {
        when(gateway.getAccount("cors-17")).thenReturn(
                CorsAccountStatusResult.notFound("ACCOUNT_NOT_FOUND", "details are not logged"));

        assertEquals(AccountStatusReconcileOutcome.NOT_FOUND, service.reconcileOne(17L));

        verify(applyService, never()).apply(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(scheduleService).markFailure(local);
    }

    @Test
    void unknownSchedulesFailureWithoutAdvancingLastSync() {
        when(gateway.getAccount("cors-17")).thenReturn(
                CorsAccountStatusResult.unknown("UPSTREAM_TIMEOUT", "sensitive response body"));

        assertEquals(AccountStatusReconcileOutcome.UNKNOWN, service.reconcileOne(17L));

        verify(applyService, never()).apply(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(scheduleService).markFailure(local);
        verify(accountMapper, never()).updateCorsSnapshot(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void timeoutExceptionIsConvertedToUnknownAndScheduledForRetry() {
        doThrow(new IllegalStateException("secret token response")).when(gateway).getAccount("cors-17");

        assertEquals(AccountStatusReconcileOutcome.UNKNOWN, service.reconcileOne(17L));

        verify(scheduleService).markFailure(local);
        verify(accountMapper, never()).updateCorsSnapshot(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void malformedNullResultIsConvertedToUnknownAndScheduledForRetry() {
        when(gateway.getAccount("cors-17")).thenReturn(null);

        assertEquals(AccountStatusReconcileOutcome.UNKNOWN, service.reconcileOne(17L));

        verify(scheduleService).markFailure(local);
        verify(accountMapper, never()).updateCorsSnapshot(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void inconsistentSnapshotSchedulesFailure() {
        when(gateway.getAccount("cors-17")).thenReturn(CorsAccountStatusResult.success(snapshot));
        when(applyService.apply(local, snapshot, successSchedule))
                .thenReturn(CorsAccountStateApplyOutcome.INCONSISTENT);

        assertEquals(AccountStatusReconcileOutcome.INCONSISTENT, service.reconcileOne(17L));

        verify(scheduleService).markFailure(local);
    }

    @Test
    void concurrentFailureScheduleIsNotBlindlyRetried() {
        when(gateway.getAccount("cors-17")).thenReturn(
                CorsAccountStatusResult.unknown("UPSTREAM_TIMEOUT", "details"));
        when(scheduleService.markFailure(local)).thenReturn(false);

        assertEquals(AccountStatusReconcileOutcome.CONCURRENT_MODIFICATION, service.reconcileOne(17L));
        verify(scheduleService).markFailure(local);
    }

    @Test
    void applyExceptionIsConvertedToUnknownAndScheduledForRetry() {
        when(gateway.getAccount("cors-17")).thenReturn(CorsAccountStatusResult.success(snapshot));
        when(applyService.apply(local, snapshot, successSchedule))
                .thenThrow(new IllegalStateException("database detail must not be logged"));

        assertEquals(AccountStatusReconcileOutcome.UNKNOWN, service.reconcileOne(17L));

        verify(scheduleService).markFailure(local);
    }

    @Test
    void missingLocalAccountIsSkippedWithoutRemoteCallOrSchedule() {
        when(accountMapper.selectById(99L)).thenReturn(null);

        assertEquals(AccountStatusReconcileOutcome.SKIPPED, service.reconcileOne(99L));

        verify(gateway, never()).getAccount(org.mockito.ArgumentMatchers.anyString());
        verify(scheduleService, never()).markFailure(org.mockito.ArgumentMatchers.any());
    }
}
