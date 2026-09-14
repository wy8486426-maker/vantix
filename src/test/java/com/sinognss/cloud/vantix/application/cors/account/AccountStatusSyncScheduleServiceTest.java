package com.sinognss.cloud.vantix.application.cors.account;

import com.sinognss.cloud.vantix.config.CorsAccountStatusSyncProperties;
import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountStatusSyncScheduleUpdate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountStatusSyncScheduleServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 14, 10, 30);

    @Mock
    private ServiceAccountMapper mapper;

    private CorsAccountStatusSyncProperties properties;
    private AccountStatusSyncScheduleService service;

    @BeforeEach
    void setUp() {
        properties = new CorsAccountStatusSyncProperties();
        service = new AccountStatusSyncScheduleService(mapper, properties,
                Clock.fixed(Instant.parse("2026-09-14T02:30:00Z"), ZoneId.of("Asia/Shanghai")));
    }

    @Test
    void successScheduleUsesStaleAfterAndResetsRetryMetadata() {
        AccountStatusSyncSuccessSchedule schedule = service.successSchedule();

        assertEquals(NOW, schedule.syncedAt());
        assertEquals(NOW.plusMinutes(10), schedule.nextAt());
    }

    @Test
    void failureBackoffDoublesAndCapsWithoutOverflow() {
        assertEquals(Duration.ofMinutes(1), service.backoffForFailureCount(1));
        assertEquals(Duration.ofMinutes(2), service.backoffForFailureCount(2));
        assertEquals(Duration.ofMinutes(4), service.backoffForFailureCount(3));
        assertEquals(Duration.ofMinutes(8), service.backoffForFailureCount(4));
        assertEquals(Duration.ofMinutes(16), service.backoffForFailureCount(5));
        assertEquals(Duration.ofMinutes(30), service.backoffForFailureCount(6));
        assertEquals(Duration.ofMinutes(30), service.backoffForFailureCount(7));
        assertEquals(Duration.ofMinutes(30), service.backoffForFailureCount(Integer.MAX_VALUE));
    }

    @Test
    void firstFailureAdvancesNextAtAndPreservesSuccessfulSyncTimestamp() {
        ServiceAccount local = local(0);
        when(mapper.updateStatusSyncFailure(any())).thenReturn(1);

        assertTrue(service.markFailure(local));

        ServiceAccountStatusSyncScheduleUpdate update = capture();
        assertEquals(73L, update.id());
        assertEquals(8L, update.expectedVersion());
        assertEquals(null, update.lastSyncAt());
        assertEquals(NOW, update.lastAttemptAt());
        assertEquals(NOW.plusMinutes(1), update.nextAt());
        assertEquals(1, update.failureCount());
        assertEquals(NOW, update.updatedAt());
    }

    @Test
    void nextFailureUsesDoubledDelayAndSuccessResetsCounter() {
        ServiceAccount local = local(1);
        when(mapper.updateStatusSyncFailure(any())).thenReturn(1);

        assertTrue(service.markFailure(local));

        ServiceAccountStatusSyncScheduleUpdate update = capture();
        assertEquals(NOW.plusMinutes(2), update.nextAt());
        assertEquals(2, update.failureCount());
    }

    @Test
    void failedCasReportsConcurrentChangeWithoutRetrying() {
        ServiceAccount local = local(5);
        when(mapper.updateStatusSyncFailure(any())).thenReturn(0);

        assertFalse(service.markFailure(local));

        verify(mapper).updateStatusSyncFailure(any());
    }

    private ServiceAccountStatusSyncScheduleUpdate capture() {
        ArgumentCaptor<ServiceAccountStatusSyncScheduleUpdate> captor =
                ArgumentCaptor.forClass(ServiceAccountStatusSyncScheduleUpdate.class);
        verify(mapper).updateStatusSyncFailure(captor.capture());
        return captor.getValue();
    }

    private static ServiceAccount local(int failureCount) {
        ServiceAccount account = new ServiceAccount();
        account.setId(73L);
        account.setVersion(8L);
        account.setStatusSyncFailureCount(failureCount);
        account.setLastSyncAt(NOW.minusHours(1));
        return account;
    }
}
