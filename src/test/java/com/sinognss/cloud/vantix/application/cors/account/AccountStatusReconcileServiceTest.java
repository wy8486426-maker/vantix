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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class AccountStatusReconcileServiceTest {
    @Mock
    private ServiceAccountMapper accountMapper;
    @Mock
    private CorsAccountStatusGateway gateway;
    @Mock
    private CorsAccountStateApplyService applyService;

    private AccountStatusReconcileService service;
    private ServiceAccount local;
    private CorsAccountSnapshot snapshot;

    @BeforeEach
    void setUp() {
        service = new AccountStatusReconcileService(accountMapper, gateway, applyService);
        local = new ServiceAccount();
        local.setId(17L);
        local.setCorsAccountId("cors-17");
        local.setAccount("account-17");
        local.setVersion(4L);
        local.setCorsActivationStatus("WAITING_ACTIVATION");
        local.setLastSyncAt(null);
        OffsetDateTime now = OffsetDateTime.of(2026, 9, 14, 10, 0, 0, 0, ZoneOffset.ofHours(8));
        snapshot = new CorsAccountSnapshot("cors-17", "account-17", "ACTIVE", "ACTIVE",
                now, now.plusMonths(1), now.minusDays(1), now);
        lenient().when(accountMapper.selectById(17L)).thenReturn(local);
    }

    @Test
    void successPassesSnapshotToUnifiedApplyService() {
        when(gateway.getAccount("cors-17")).thenReturn(CorsAccountStatusResult.success(snapshot));
        when(applyService.apply(local, snapshot)).thenReturn(CorsAccountStateApplyOutcome.UPDATED);

        assertEquals(AccountStatusReconcileOutcome.UPDATED, service.reconcileOne(17L));

        verify(applyService).apply(local, snapshot);
    }

    @Test
    void notFoundLeavesAccountProjectionAlone() {
        when(gateway.getAccount("cors-17")).thenReturn(
                CorsAccountStatusResult.notFound("ACCOUNT_NOT_FOUND", "details are not logged"));

        assertEquals(AccountStatusReconcileOutcome.NOT_FOUND, service.reconcileOne(17L));

        verify(applyService, never()).apply(local, snapshot);
        verify(accountMapper).selectById(17L);
    }

    @Test
    void unknownDoesNotApplyOrAdvanceLastSync() {
        when(gateway.getAccount("cors-17")).thenReturn(
                CorsAccountStatusResult.unknown("UPSTREAM_TIMEOUT", "sensitive response body"));

        assertEquals(AccountStatusReconcileOutcome.UNKNOWN, service.reconcileOne(17L));

        verify(applyService, never()).apply(local, snapshot);
        verify(accountMapper, never()).updateCorsSnapshot(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void timeoutExceptionIsConvertedToUnknown() {
        doThrow(new IllegalStateException("secret token response")).when(gateway).getAccount("cors-17");

        assertEquals(AccountStatusReconcileOutcome.UNKNOWN, service.reconcileOne(17L));

        verify(applyService, never()).apply(local, snapshot);
        verify(accountMapper, never()).updateCorsSnapshot(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void malformedNullResultIsConvertedToUnknown() {
        when(gateway.getAccount("cors-17")).thenReturn(null);

        assertEquals(AccountStatusReconcileOutcome.UNKNOWN, service.reconcileOne(17L));

        verify(applyService, never()).apply(local, snapshot);
        verify(accountMapper, never()).updateCorsSnapshot(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void missingLocalAccountIsSkippedWithoutRemoteCall() {
        when(accountMapper.selectById(99L)).thenReturn(null);

        assertEquals(AccountStatusReconcileOutcome.SKIPPED, service.reconcileOne(99L));

        verify(gateway, never()).getAccount(org.mockito.ArgumentMatchers.anyString());
        verify(applyService, never()).apply(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
