package com.sinognss.cloud.vantix.application.cors.account;

import com.sinognss.cloud.vantix.config.CorsAccountStatusSyncProperties;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountStatusReconcileJobTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-14T02:30:00Z"), ZoneId.of("Asia/Shanghai"));
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 14, 10, 30);

    @Test
    void appliesBatchLimitAndContinuesAfterOneAccountFailsInOrder() {
        ServiceAccountMapper mapper = mock(ServiceAccountMapper.class);
        AccountStatusReconcileService service = mock(AccountStatusReconcileService.class);
        CorsAccountStatusSyncProperties properties = properties(2);
        AccountStatusReconcileJob job = new AccountStatusReconcileJob(mapper, service, properties, CLOCK);
        when(mapper.selectSyncCandidates(NOW.minusMinutes(10), 2)).thenReturn(List.of(11L, 12L));
        doThrow(new IllegalStateException("remote payload must not be logged"))
                .when(service).reconcileOne(11L);

        job.reconcile();

        InOrder inOrder = inOrder(service);
        inOrder.verify(service).reconcileOne(11L);
        inOrder.verify(service).reconcileOne(12L);
        verify(mapper).selectSyncCandidates(NOW.minusMinutes(10), 2);
    }

    @Test
    void overlappingSchedulerRunIsSkipped() throws Exception {
        ServiceAccountMapper mapper = mock(ServiceAccountMapper.class);
        AccountStatusReconcileService service = mock(AccountStatusReconcileService.class);
        AccountStatusReconcileJob job = new AccountStatusReconcileJob(mapper, service, properties(10), CLOCK);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(mapper.selectSyncCandidates(NOW.minusMinutes(10), 10)).thenReturn(List.of(21L));
        doAnswer(invocation -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return AccountStatusReconcileOutcome.UPDATED;
        }).when(service).reconcileOne(21L);

        Thread firstRun = new Thread(job::reconcile);
        firstRun.start();
        assertTrue(entered.await(5, TimeUnit.SECONDS));

        job.reconcile();

        verify(mapper, times(1)).selectSyncCandidates(NOW.minusMinutes(10), 10);
        release.countDown();
        firstRun.join(5000);
        assertFalse(firstRun.isAlive());
    }

    private static CorsAccountStatusSyncProperties properties(int batchSize) {
        CorsAccountStatusSyncProperties properties = new CorsAccountStatusSyncProperties();
        properties.setStaleAfter(java.time.Duration.ofMinutes(10));
        properties.setBatchSize(batchSize);
        return properties;
    }
}
