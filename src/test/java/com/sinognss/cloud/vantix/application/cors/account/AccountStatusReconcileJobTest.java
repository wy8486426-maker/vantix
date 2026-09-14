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
import static org.mockito.Mockito.never;
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
        AccountStatusReconcileJob job = new AccountStatusReconcileJob(mapper, service, properties(2), CLOCK);
        when(mapper.selectDueWaitingActivationIds(NOW, 2)).thenReturn(List.of(11L, 12L));
        doThrow(new IllegalStateException("remote payload must not be logged"))
                .when(service).reconcileOne(11L);

        job.reconcile();

        InOrder inOrder = inOrder(service);
        inOrder.verify(service).reconcileOne(11L);
        inOrder.verify(service).reconcileOne(12L);
        verify(mapper).selectDueWaitingActivationIds(NOW, 2);
        verify(mapper, never()).selectDueOtherIds(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void waitingCandidatesRunFirstAndOtherCandidatesFillOnlyRemainingCapacity() {
        ServiceAccountMapper mapper = mock(ServiceAccountMapper.class);
        AccountStatusReconcileService service = mock(AccountStatusReconcileService.class);
        AccountStatusReconcileJob job = new AccountStatusReconcileJob(mapper, service, properties(3), CLOCK);
        when(mapper.selectDueWaitingActivationIds(NOW, 3)).thenReturn(List.of(21L));
        when(mapper.selectDueOtherIds(NOW, 2)).thenReturn(List.of(22L, 21L, 23L));

        job.reconcile();

        InOrder inOrder = inOrder(service);
        inOrder.verify(service).reconcileOne(21L);
        inOrder.verify(service).reconcileOne(22L);
        inOrder.verify(service).reconcileOne(23L);
        verify(mapper).selectDueOtherIds(NOW, 2);
    }

    @Test
    void overlappingSchedulerRunIsSkipped() throws Exception {
        ServiceAccountMapper mapper = mock(ServiceAccountMapper.class);
        AccountStatusReconcileService service = mock(AccountStatusReconcileService.class);
        AccountStatusReconcileJob job = new AccountStatusReconcileJob(mapper, service, properties(10), CLOCK);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(mapper.selectDueWaitingActivationIds(NOW, 10)).thenReturn(List.of(31L));
        when(mapper.selectDueOtherIds(NOW, 9)).thenReturn(List.of());
        doAnswer(invocation -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return AccountStatusReconcileOutcome.UPDATED;
        }).when(service).reconcileOne(31L);

        Thread firstRun = new Thread(job::reconcile);
        firstRun.start();
        assertTrue(entered.await(5, TimeUnit.SECONDS));

        job.reconcile();

        verify(mapper, times(1)).selectDueWaitingActivationIds(NOW, 10);
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
