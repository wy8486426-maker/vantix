package com.sinognss.cloud.vantix.application.password.reset;

import com.sinognss.cloud.vantix.config.CorsAccountPasswordProperties;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.domain.password.AccountPasswordAction;
import com.sinognss.cloud.vantix.infrastructure.mapper.AccountPasswordActionMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.CorsOperationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountPasswordResetStateServiceTest {
    private static final long OPERATION_ID = 17L;
    private static final long ACTION_ID = 23L;
    private static final long SERVICE_ACCOUNT_ID = 31L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 14, 12, 0);

    @Mock private CorsOperationMapper operationMapper;
    @Mock private AccountPasswordActionMapper actionMapper;

    private AccountPasswordResetStateService stateService;
    private CorsOperation operation;
    private AccountPasswordAction action;

    @BeforeEach
    void setUp() {
        CorsAccountPasswordProperties properties = new CorsAccountPasswordProperties();
        properties.setMaxRetries(4);
        properties.setRetryBaseDelay(java.time.Duration.ofSeconds(30));
        properties.setRetryMaxDelay(java.time.Duration.ofMinutes(5));
        Clock clock = Clock.fixed(Instant.parse("2026-09-14T12:00:00Z"), ZoneOffset.UTC);
        stateService = new AccountPasswordResetStateService(operationMapper, actionMapper, properties, clock);
        operation = operation("CLAIMED", 2L, 1);
        action = action();
        when(operationMapper.selectByIdForUpdate(OPERATION_ID)).thenReturn(operation);
        when(actionMapper.selectByIdForUpdate(ACTION_ID)).thenReturn(action);
    }

    @Test
    void retryKeepsActionProcessingAndSchedulesOperationForOriginalRequestQuery() {
        when(actionMapper.transitionFromProcessing(eq(ACTION_ID), eq(5L), eq("PROCESSING"),
                eq(AccountPasswordResetFailure.POST_UNKNOWN), eq("远程重置结果未知，等待后续安全查询"),
                eq((LocalDateTime) null), eq(NOW))).thenReturn(1);
        when(operationMapper.scheduleRetry(eq(OPERATION_ID), eq(2L), eq(2), eq(NOW.plusSeconds(60)),
                eq(AccountPasswordResetFailure.POST_UNKNOWN), eq("远程重置结果未知，等待后续安全查询"), eq(NOW)))
                .thenReturn(1);

        stateService.retryOrMarkManualReview(operation, action, AccountPasswordResetFailure.POST_UNKNOWN);

        verify(actionMapper).transitionFromProcessing(ACTION_ID, 5L, "PROCESSING",
                AccountPasswordResetFailure.POST_UNKNOWN, "远程重置结果未知，等待后续安全查询", null, NOW);
        verify(operationMapper).scheduleRetry(OPERATION_ID, 2L, 2, NOW.plusSeconds(60),
                AccountPasswordResetFailure.POST_UNKNOWN, "远程重置结果未知，等待后续安全查询", NOW);
    }

    @Test
    void staleClaimMovesToRetryWaitImmediatelyAndIncrementsRetryCount() {
        CorsOperation stale = operation("CLAIMED", 2L, 1);
        when(actionMapper.transitionFromProcessing(eq(ACTION_ID), eq(5L), eq("PROCESSING"),
                eq(AccountPasswordResetFailure.CLAIM_TIMEOUT),
                eq("重置任务执行超时，下一轮将先查询原请求标识"), eq((LocalDateTime) null), eq(NOW)))
                .thenReturn(1);
        when(operationMapper.recoverClaimed(eq(OPERATION_ID), eq(2L), eq(AccountPasswordResetConstants.RETRY_WAIT),
                eq(2), eq(NOW), eq(AccountPasswordResetFailure.CLAIM_TIMEOUT),
                eq("重置任务执行超时，下一轮将先查询原请求标识"), eq(NOW))).thenReturn(1);

        stateService.recoverStaleClaim(stale);

        verify(actionMapper).transitionFromProcessing(ACTION_ID, 5L, "PROCESSING",
                AccountPasswordResetFailure.CLAIM_TIMEOUT,
                "重置任务执行超时，下一轮将先查询原请求标识", null, NOW);
        verify(operationMapper).recoverClaimed(OPERATION_ID, 2L, AccountPasswordResetConstants.RETRY_WAIT,
                2, NOW, AccountPasswordResetFailure.CLAIM_TIMEOUT,
                "重置任务执行超时，下一轮将先查询原请求标识", NOW);
    }

    @Test
    void definitiveRejectTransitionsBothRowsToFailed() {
        when(actionMapper.transitionFromProcessing(eq(ACTION_ID), eq(5L),
                eq("FAILED"), eq(AccountPasswordResetFailure.POST_DEFINITIVE_REJECT),
                eq("远程重置请求已明确拒绝且未产生副作用"), eq(NOW), eq(NOW))).thenReturn(1);
        when(operationMapper.markFailed(eq(OPERATION_ID), eq(2L),
                eq(AccountPasswordResetFailure.POST_DEFINITIVE_REJECT),
                eq("远程重置请求已明确拒绝且未产生副作用"), eq(NOW))).thenReturn(1);

        stateService.definitiveFail(operation, action, AccountPasswordResetFailure.POST_DEFINITIVE_REJECT);

        verify(actionMapper).transitionFromProcessing(ACTION_ID, 5L, "FAILED",
                AccountPasswordResetFailure.POST_DEFINITIVE_REJECT,
                "远程重置请求已明确拒绝且未产生副作用", NOW, NOW);
        verify(operationMapper).markFailed(OPERATION_ID, 2L,
                AccountPasswordResetFailure.POST_DEFINITIVE_REJECT,
                "远程重置请求已明确拒绝且未产生副作用", NOW);
    }

    @Test
    void retryLimitMovesBothRowsToManualReview() {
        CorsAccountPasswordProperties properties = new CorsAccountPasswordProperties();
        properties.setMaxRetries(0);
        Clock clock = Clock.fixed(Instant.parse("2026-09-14T12:00:00Z"), ZoneOffset.UTC);
        stateService = new AccountPasswordResetStateService(operationMapper, actionMapper, properties, clock);
        when(actionMapper.transitionFromProcessing(eq(ACTION_ID), eq(5L), eq("MANUAL_REVIEW"),
                eq(AccountPasswordResetFailure.RETRY_EXHAUSTED),
                eq("重置任务超过自动重试上限，需要人工处理"), eq(NOW), eq(NOW))).thenReturn(1);
        when(operationMapper.markManualReview(eq(OPERATION_ID), eq(2L),
                eq(AccountPasswordResetFailure.RETRY_EXHAUSTED),
                eq("重置任务超过自动重试上限，需要人工处理"), eq(NOW))).thenReturn(1);

        stateService.retryOrMarkManualReview(operation, action, AccountPasswordResetFailure.POST_UNKNOWN);

        verify(actionMapper).transitionFromProcessing(ACTION_ID, 5L, "MANUAL_REVIEW",
                AccountPasswordResetFailure.RETRY_EXHAUSTED,
                "重置任务超过自动重试上限，需要人工处理", NOW, NOW);
        verify(operationMapper).markManualReview(OPERATION_ID, 2L,
                AccountPasswordResetFailure.RETRY_EXHAUSTED,
                "重置任务超过自动重试上限，需要人工处理", NOW);
    }

    private static CorsOperation operation(String status, long version, int retryCount) {
        CorsOperation operation = new CorsOperation();
        operation.setId(OPERATION_ID);
        operation.setRequestId("PWD-RS-17");
        operation.setOperationType(AccountPasswordResetConstants.OPERATION_TYPE);
        operation.setBizType(AccountPasswordResetConstants.BIZ_TYPE);
        operation.setBizId(ACTION_ID);
        operation.setServiceAccountId(SERVICE_ACCOUNT_ID);
        operation.setStatus(status);
        operation.setVersion(version);
        operation.setRetryCount(retryCount);
        return operation;
    }

    private static AccountPasswordAction action() {
        AccountPasswordAction action = new AccountPasswordAction();
        action.setId(ACTION_ID);
        action.setRequestId("PWD-RS-17");
        action.setActionType("RESET");
        action.setServiceAccountId(SERVICE_ACCOUNT_ID);
        action.setStatus("PROCESSING");
        action.setVersion(5L);
        return action;
    }
}
