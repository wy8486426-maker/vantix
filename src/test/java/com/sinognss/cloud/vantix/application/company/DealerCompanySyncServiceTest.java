package com.sinognss.cloud.vantix.application.company;

import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.config.CompanySyncProperties;
import com.sinognss.cloud.vantix.domain.company.DealerCompany;
import com.sinognss.cloud.vantix.infrastructure.mapper.DealerCompanyMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DealerCompanySyncServiceTest {
    private final DealerCompanyMapper companyMapper = mock(DealerCompanyMapper.class);
    private final UserCenterCompanyGateway gateway = mock(UserCenterCompanyGateway.class);
    private final CompanySyncProperties properties = new CompanySyncProperties();
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private DealerCompanySyncService service;

    @BeforeEach
    void setUp() {
        service = new DealerCompanySyncService(companyMapper, gateway, properties, clock);
    }

    @Test
    void ensurePresentRefreshesManagerFieldsForExistingLocalCompany() {
        DealerCompany existing = company(100L, "local");
        when(companyMapper.selectByCompanyId(100L)).thenReturn(existing);
        when(gateway.findByCompanyId(100L))
                .thenReturn(Optional.of(new UserCenterCompany(100L, "remote", 123L, "13800138000")));

        assertDoesNotThrow(() -> service.ensurePresent(100L));

        verify(gateway).findByCompanyId(100L);
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Collection<DealerCompany>> captor =
                org.mockito.ArgumentCaptor.forClass(Collection.class);
        verify(companyMapper).upsertSyncedCompanies(captor.capture());
        DealerCompany synced = captor.getValue().iterator().next();
        assertEquals(123L, synced.getManagerId());
        assertEquals("13800138000", synced.getManagerTel());
    }

    @Test
    void missingCompanyUsesPreciseRemoteIdentityAndDefaultsToFirstLevel() {
        when(companyMapper.selectByCompanyId(100L)).thenReturn(null);
        when(gateway.findByCompanyId(100L))
                .thenReturn(Optional.of(new UserCenterCompany(100L, "remote", 123L, "13800138000")));

        service.ensurePresent(100L);

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Collection<DealerCompany>> captor =
                org.mockito.ArgumentCaptor.forClass(Collection.class);
        verify(companyMapper).upsertSyncedCompanies(captor.capture());
        DealerCompany inserted = captor.getValue().iterator().next();
        assertEquals(100L, inserted.getCompanyId());
        assertEquals("remote", inserted.getCompanyName());
        assertEquals(123L, inserted.getManagerId());
        assertEquals("13800138000", inserted.getManagerTel());
        assertNull(inserted.getParentCompanyId());
        assertNull(inserted.getCompanyStatus());
        assertEquals(2026, inserted.getCompanySyncedAt().getYear());
    }

    @Test
    void remoteCompanyMissingReturnsNotFoundAndDoesNotWrite() {
        when(companyMapper.selectByCompanyId(100L)).thenReturn(null);
        when(gateway.findByCompanyId(100L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.ensurePresent(100L));

        assertEquals(ErrorCode.NOT_FOUND, exception.getVantixErrorCode());
        verify(companyMapper, never()).upsertSyncedCompanies(any());
    }

    @Test
    void invalidRemoteCompanyDoesNotWrite() {
        when(companyMapper.selectByCompanyId(100L)).thenReturn(null);
        when(gateway.findByCompanyId(100L))
                .thenReturn(Optional.of(new UserCenterCompany(100L, "  ")));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.ensurePresent(100L));

        assertEquals(ErrorCode.COMPANY_SYNC_FAILED, exception.getVantixErrorCode());
        verify(companyMapper, never()).upsertSyncedCompanies(any());
    }

    @Test
    void remoteFailureDoesNotWrite() {
        when(companyMapper.selectByCompanyId(100L)).thenReturn(null);
        when(gateway.findByCompanyId(100L)).thenThrow(
                new BusinessException(ErrorCode.COMPANY_SYNC_FAILED, "unavailable"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.ensurePresent(100L));

        assertEquals(ErrorCode.COMPANY_SYNC_FAILED, exception.getVantixErrorCode());
        verify(companyMapper, never()).upsertSyncedCompanies(any());
    }

    @Test
    void fullSyncProcessesTwoPagesAsSeparateBatches() {
        when(gateway.page(1, 200)).thenReturn(new UserCenterCompanyPage(1, 2,
                1, List.of(new UserCenterCompany(100L, "one", 101L, "13800000001"))));
        when(gateway.page(2, 200)).thenReturn(new UserCenterCompanyPage(2, 2,
                1, List.of(new UserCenterCompany(200L, "two", 202L, "13800000002"))));

        DealerCompanySyncService.SyncSummary summary = service.syncAllCompanies();

        assertEquals(new DealerCompanySyncService.SyncSummary(2, 2), summary);
        verify(companyMapper, times(2)).upsertSyncedCompanies(any());
        verify(gateway).page(1, 200);
        verify(gateway).page(2, 200);
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Collection<DealerCompany>> captor =
                org.mockito.ArgumentCaptor.forClass(Collection.class);
        verify(companyMapper, times(2)).upsertSyncedCompanies(captor.capture());
        assertEquals(101L, captor.getAllValues().get(0).iterator().next().getManagerId());
        assertEquals("13800000002", captor.getAllValues().get(1).iterator().next().getManagerTel());
    }

    @Test
    void fullSyncRefreshesChangedManagerFields() {
        when(gateway.page(1, 200))
                .thenReturn(new UserCenterCompanyPage(1, 1, 1,
                        List.of(new UserCenterCompany(100L, "company", 101L, "13800000001"))))
                .thenReturn(new UserCenterCompanyPage(1, 1, 1,
                        List.of(new UserCenterCompany(100L, "company", 202L, "13800000002"))));

        service.syncAllCompanies();
        service.syncAllCompanies();

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Collection<DealerCompany>> captor =
                org.mockito.ArgumentCaptor.forClass(Collection.class);
        verify(companyMapper, times(2)).upsertSyncedCompanies(captor.capture());
        assertEquals(101L, captor.getAllValues().get(0).iterator().next().getManagerId());
        assertEquals(202L, captor.getAllValues().get(1).iterator().next().getManagerId());
        assertEquals("13800000002", captor.getAllValues().get(1).iterator().next().getManagerTel());
    }

    @Test
    void emptyPageStopsWithoutLoopingOrWriting() {
        when(gateway.page(1, 200)).thenReturn(new UserCenterCompanyPage(1, 3, 0, List.of()));

        DealerCompanySyncService.SyncSummary summary = service.syncAllCompanies();

        assertEquals(new DealerCompanySyncService.SyncSummary(0, 0), summary);
        verify(gateway, times(1)).page(1, 200);
        verify(companyMapper, never()).upsertSyncedCompanies(any());
    }

    @Test
    void pageFailureTerminatesRunWithoutDeletingOrWriting() {
        when(gateway.page(1, 200)).thenThrow(
                new BusinessException(ErrorCode.COMPANY_SYNC_FAILED, "unavailable"));

        assertThrows(BusinessException.class, () -> service.syncAllCompanies());

        verify(companyMapper, never()).upsertSyncedCompanies(any());
    }

    @Test
    void invalidFullSyncItemIsSkippedButValidItemIsWritten() {
        when(gateway.page(1, 200)).thenReturn(new UserCenterCompanyPage(1, 1,
                2, List.of(new UserCenterCompany(100L, "valid", 101L, null),
                        new UserCenterCompany(null, "invalid", null, null))));

        DealerCompanySyncService.SyncSummary summary = service.syncAllCompanies();

        assertEquals(new DealerCompanySyncService.SyncSummary(1, 1), summary);
        verify(companyMapper).upsertSyncedCompanies(any());
    }

    @Test
    void allInvalidSourcePageDoesNotStopFollowingPages() {
        when(gateway.page(1, 200)).thenReturn(new UserCenterCompanyPage(1, 2, 2, List.of()));
        when(gateway.page(2, 200)).thenReturn(new UserCenterCompanyPage(2, 2, 1,
                List.of(new UserCenterCompany(200L, "two", 202L, "13800000002"))));

        DealerCompanySyncService.SyncSummary summary = service.syncAllCompanies();

        assertEquals(new DealerCompanySyncService.SyncSummary(2, 1), summary);
        verify(gateway).page(2, 200);
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Collection<DealerCompany>> captor =
                org.mockito.ArgumentCaptor.forClass(Collection.class);
        verify(companyMapper).upsertSyncedCompanies(captor.capture());
        assertEquals(200L, captor.getValue().iterator().next().getCompanyId());
    }

    private DealerCompany company(Long companyId, String name) {
        DealerCompany company = new DealerCompany();
        company.setCompanyId(companyId);
        company.setCompanyName(name);
        return company;
    }
}
