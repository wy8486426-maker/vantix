package com.sinognss.cloud.vantix.application.testaccount;

import com.sinognss.cloud.vantix.config.CorsOperationProperties;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.infrastructure.mapper.CorsOperationMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.TestAccountIssueBatchMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestAccountIssueClaimServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 17, 12, 0);
    private final CorsOperationMapper operationMapper = mock(CorsOperationMapper.class);
    private final TestAccountIssueBatchMapper batchMapper = mock(TestAccountIssueBatchMapper.class);
    private final CorsOperationProperties properties = new CorsOperationProperties();
    private final TestAccountIssueClaimService service = new TestAccountIssueClaimService(
            operationMapper, batchMapper, properties,
            Clock.fixed(Instant.parse("2026-09-17T04:00:00Z"), ZoneId.of("Asia/Shanghai")));

    @Test
    void oldPendingOperationWithNoFirstAttemptCanStillBeClaimed() {
        CorsOperation pending = operation("PENDING", NOW.minusMinutes(10), null);
        CorsOperation claimed = operation("CLAIMED", pending.getCreatedAt(), NOW);
        when(operationMapper.selectById(41L)).thenReturn(pending, claimed);
        when(operationMapper.claim(41L, "PENDING", 3L, NOW)).thenReturn(1);

        assertNotNull(service.claim(41L));

        verify(operationMapper).claim(41L, "PENDING", 3L, NOW);
        verify(operationMapper, never()).markPendingManualReview(anyLong(), anyLong(), anyString(),
                anyString(), any());
    }

    @Test
    void recentFirstAttemptAllowsRetryEvenWhenCreatedAtIsOld() {
        CorsOperation retryWait = operation("RETRY_WAIT", NOW.minusMinutes(10), NOW.minusMinutes(1));
        retryWait.setNextRetryAt(NOW.minusSeconds(1));
        CorsOperation claimed = operation("CLAIMED", retryWait.getCreatedAt(), retryWait.getFirstAttemptAt());
        when(operationMapper.selectById(41L)).thenReturn(retryWait, claimed);
        when(operationMapper.claim(41L, "RETRY_WAIT", 3L, NOW)).thenReturn(1);

        assertNotNull(service.claim(41L));

        verify(operationMapper).claim(41L, "RETRY_WAIT", 3L, NOW);
        assertEquals(NOW.minusMinutes(1), claimed.getFirstAttemptAt());
    }

    @Test
    void firstAttemptAtPastResultWindowStopsRetry() {
        CorsOperation expired = operation("RETRY_WAIT", NOW.minusMinutes(10), NOW.minusMinutes(2));
        when(operationMapper.selectById(41L)).thenReturn(expired);
        when(operationMapper.markPendingManualReview(eq(41L), eq(3L),
                eq("CORS_RESULT_WINDOW_EXPIRED"), anyString(), eq(NOW))).thenReturn(1);
        when(batchMapper.markManualReview(eq(9L), eq("CORS_RESULT_WINDOW_EXPIRED"), anyString(), eq(NOW)))
                .thenReturn(1);

        assertNull(service.claim(41L));

        verify(operationMapper, never()).claim(anyLong(), anyString(), anyLong(), any());
        verify(operationMapper).markPendingManualReview(eq(41L), eq(3L),
                eq("CORS_RESULT_WINDOW_EXPIRED"), anyString(), eq(NOW));
    }

    private static CorsOperation operation(String status, LocalDateTime createdAt,
                                           LocalDateTime firstAttemptAt) {
        CorsOperation operation = new CorsOperation();
        operation.setId(41L);
        operation.setRequestId("TEST_cors-1");
        operation.setOperationType(TestAccountIssueConstants.OPERATION_TYPE);
        operation.setBizType(TestAccountIssueConstants.BIZ_TYPE);
        operation.setBizId(9L);
        operation.setStatus(status);
        operation.setRetryCount(0);
        operation.setCreatedAt(createdAt);
        operation.setFirstAttemptAt(firstAttemptAt);
        operation.setVersion(3L);
        return operation;
    }
}
