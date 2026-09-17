package com.sinognss.cloud.vantix.application.testaccount;

import com.sinognss.cloud.vantix.config.CorsOperationProperties;
import com.sinognss.cloud.vantix.domain.account.TestAccountIssueBatch;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.infrastructure.mapper.CorsOperationMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.TestAccountIssueBatchMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestAccountIssueStateServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 17, 12, 0);
    private final CorsOperationMapper operationMapper = mock(CorsOperationMapper.class);
    private final TestAccountIssueBatchMapper batchMapper = mock(TestAccountIssueBatchMapper.class);
    private final CorsOperationProperties properties = new CorsOperationProperties();
    private final TestAccountIssueStateService service;

    TestAccountIssueStateServiceTest() {
        properties.setRetryBaseDelay(Duration.ofSeconds(15));
        service = new TestAccountIssueStateService(operationMapper, batchMapper, properties,
                Clock.fixed(Instant.parse("2026-09-17T04:00:00Z"), ZoneId.of("Asia/Shanghai")));
    }

    @Test
    void missingFirstAttemptDoesNotUseOldCreatedAtForRetryDeadline() {
        CorsOperation claimed = operation(NOW.minusMinutes(10), null);
        when(operationMapper.selectById(41L)).thenReturn(claimed);
        when(operationMapper.scheduleRetry(eq(41L), eq(3L), eq(1), eq(NOW.plusSeconds(15)),
                eq("CORS_TIMEOUT"), eq("timeout"), eq(NOW))).thenReturn(1);

        assertTrue(service.retryOrMarkManualReview(claimed, "CORS_TIMEOUT", "timeout"));

        verify(operationMapper).scheduleRetry(eq(41L), eq(3L), eq(1), eq(NOW.plusSeconds(15)),
                eq("CORS_TIMEOUT"), eq("timeout"), eq(NOW));
        verify(operationMapper, never()).markManualReview(anyLong(), anyLong(), anyString(),
                anyString(), any());
    }

    @Test
    void firstAttemptAtOlderThanTwoMinutesMovesBatchToManualReview() {
        CorsOperation claimed = operation(NOW.minusMinutes(10), NOW.minusMinutes(2));
        TestAccountIssueBatch batch = new TestAccountIssueBatch();
        batch.setId(9L);
        batch.setStatus(TestAccountIssueConstants.PROCESSING);
        when(operationMapper.selectById(41L)).thenReturn(claimed);
        when(batchMapper.selectByIdForUpdate(9L)).thenReturn(batch);
        when(batchMapper.markManualReview(eq(9L), eq("CORS_RESULT_WINDOW_EXPIRED"), anyString(), eq(NOW)))
                .thenReturn(1);
        when(operationMapper.markManualReview(eq(41L), eq(3L), eq("CORS_RESULT_WINDOW_EXPIRED"),
                anyString(), eq(NOW))).thenReturn(1);

        assertTrue(service.retryOrMarkManualReview(claimed, "CORS_TIMEOUT", "timeout"));

        verify(batchMapper).markManualReview(eq(9L), eq("CORS_RESULT_WINDOW_EXPIRED"), anyString(), eq(NOW));
        verify(operationMapper).markManualReview(eq(41L), eq(3L), eq("CORS_RESULT_WINDOW_EXPIRED"),
                anyString(), eq(NOW));
        verify(operationMapper, never()).scheduleRetry(anyLong(), anyLong(), any(Integer.class),
                any(), anyString(), anyString(), any());
    }

    private static CorsOperation operation(LocalDateTime createdAt, LocalDateTime firstAttemptAt) {
        CorsOperation operation = new CorsOperation();
        operation.setId(41L);
        operation.setRequestId("TEST_cors-1");
        operation.setOperationType(TestAccountIssueConstants.OPERATION_TYPE);
        operation.setBizType(TestAccountIssueConstants.BIZ_TYPE);
        operation.setBizId(9L);
        operation.setStatus(TestAccountIssueConstants.CLAIMED);
        operation.setRetryCount(0);
        operation.setCreatedAt(createdAt);
        operation.setFirstAttemptAt(firstAttemptAt);
        operation.setVersion(3L);
        return operation;
    }
}
