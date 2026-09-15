package com.sinognss.cloud.vantix.application.cors.account;

import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import com.sinognss.cloud.vantix.integration.cors.account.CorsUserInfoRepository;
import com.sinognss.cloud.vantix.integration.cors.account.CorsUserInfoSnapshotMapper;
import com.sinognss.cloud.vantix.integration.cors.account.CorsUserInfoStatusRow;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CorsAccountRealtimeRefreshServiceTest {
    private final ServiceAccountMapper accountMapper = mock(ServiceAccountMapper.class);
    private final CorsUserInfoRepository repository = mock(CorsUserInfoRepository.class);
    private final CorsAccountStateApplyService applyService = mock(CorsAccountStateApplyService.class);
    private final AccountStatusSyncScheduleService scheduleService = mock(AccountStatusSyncScheduleService.class);
    private final CorsAccountRealtimeRefreshService service = new CorsAccountRealtimeRefreshService(
            accountMapper, repository, new CorsUserInfoSnapshotMapper(), applyService, scheduleService);

    @Test
    void missingLocalAccountDoesNotQueryCors() {
        when(accountMapper.selectForCorsRealtimeRefreshByAccount("missing")).thenReturn(null);

        assertEquals(CorsAccountRealtimeRefreshOutcome.LOCAL_NOT_FOUND, service.refresh("missing", "updatePass"));
        verifyNoInteractions(repository, applyService);
    }

    @Test
    void invalidLocalIdDoesNotQueryCors() {
        ServiceAccount local = local("account", "0");
        when(accountMapper.selectForCorsRealtimeRefreshByAccount("account")).thenReturn(local);

        assertEquals(CorsAccountRealtimeRefreshOutcome.INCONSISTENT, service.refresh("account", "active"));
        verifyNoInteractions(repository, applyService);
    }

    @Test
    void corsDatabaseFailureHasDedicatedRealtimeOutcome() {
        ServiceAccount local = local("account", "501");
        when(accountMapper.selectForCorsRealtimeRefreshByAccount("account")).thenReturn(local);
        when(repository.findById(501L)).thenThrow(new IllegalStateException("unavailable"));

        assertEquals(CorsAccountRealtimeRefreshOutcome.REMOTE_UNAVAILABLE,
                service.refresh("account", "active"));
        verifyNoInteractions(applyService);
    }

    @Test
    void identityChecksProtectApplyAndValidRowIsDelegated() {
        ServiceAccount local = local("account", "501");
        when(accountMapper.selectForCorsRealtimeRefreshByAccount("account")).thenReturn(local);
        when(repository.findById(501L)).thenReturn(new CorsUserInfoStatusRow(501L, "other", 0, 0,
                null, null, LocalDateTime.of(2026, 9, 15, 10, 0)));
        assertEquals(CorsAccountRealtimeRefreshOutcome.INCONSISTENT, service.refresh("account", "disable"));
        verifyNoInteractions(applyService);

        when(repository.findById(501L)).thenReturn(new CorsUserInfoStatusRow(501L, "account", 0, 1,
                null, null, LocalDateTime.of(2026, 9, 15, 10, 0)));
        when(scheduleService.successSchedule()).thenReturn(new AccountStatusSyncSuccessSchedule(
                LocalDateTime.of(2026, 9, 15, 10, 1), LocalDateTime.of(2026, 9, 15, 10, 11)));
        when(applyService.apply(eq(local), any(), any())).thenReturn(CorsAccountStateApplyOutcome.UPDATED);

        assertEquals(CorsAccountRealtimeRefreshOutcome.UPDATED, service.refresh("account", "disable"));
        verify(applyService).apply(eq(local), any(), any());
    }

    private static ServiceAccount local(String account, String corsId) {
        ServiceAccount result = new ServiceAccount();
        result.setId(1L);
        result.setVersion(0L);
        result.setAccount(account);
        result.setCorsAccountId(corsId);
        return result;
    }
}
