package com.sinognss.cloud.vantix.application.renewal;

import com.sinognss.cloud.vantix.config.CorsAccountRenewalProperties;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.domain.renewal.AccountRenewal;
import com.sinognss.cloud.vantix.domain.servicecode.ProcessingType;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCode;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeStatus;
import com.sinognss.cloud.vantix.infrastructure.mapper.AccountRenewalMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.CorsOperationMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountRenewalStateServiceTest {
    private static final long OPERATION_ID = 31L;
    private static final long RENEWAL_ID = 51L;
    private static final long ACCOUNT_ID = 61L;
    private static final long CODE_ID = 71L;
    private static final long OP_VERSION = 7L;
    private static final long RENEWAL_VERSION = 9L;
    private static final long CODE_VERSION = 12L;
    private static final String REQUEST_ID = "RN-test-31";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 14, 12, 0);

    private CorsOperationMapper operationMapper;
    private AccountRenewalMapper renewalMapper;
    private ServiceCodeMapper codeMapper;
    private CorsAccountRenewalProperties properties;
    private AccountRenewalStateService stateService;
    private CorsOperation operation;
    private AccountRenewal renewal;

    @BeforeEach
    void setUp() {
        operationMapper = org.mockito.Mockito.mock(CorsOperationMapper.class);
        renewalMapper = org.mockito.Mockito.mock(AccountRenewalMapper.class);
        codeMapper = org.mockito.Mockito.mock(ServiceCodeMapper.class);
        properties = new CorsAccountRenewalProperties();
        Clock clock = Clock.fixed(Instant.parse("2026-09-14T12:00:00Z"), ZoneOffset.UTC);
        stateService = new AccountRenewalStateService(operationMapper, renewalMapper, codeMapper,
                properties, clock);
        operation = operation("CLAIMED", OP_VERSION, 2);
        renewal = renewal("PROCESSING", RENEWAL_VERSION);
    }

    @Test
    void unknownRetryUsesBackoffAndRecordsErrorOnBothRows() {
        stubClaimedState();
        when(renewalMapper.updateRetryError(RENEWAL_ID, RENEWAL_VERSION, "QUERY_UNKNOWN",
                "unknown remote result", NOW)).thenReturn(1);
        when(operationMapper.scheduleRetry(OPERATION_ID, OP_VERSION, 3, NOW.plusMinutes(2),
                "QUERY_UNKNOWN", "unknown remote result", NOW)).thenReturn(1);

        assertTrue(stateService.retryOrMarkManualReview(operation, renewal,
                "QUERY_UNKNOWN", "unknown remote result"));

        verify(codeMapper, never()).releaseRenewalCode(any(), anyString(), any(), any());
        verify(codeMapper, never()).consumeRenewalCode(any(), anyString(), any(), any());
    }

    @Test
    void retryLimitMovesRenewalAndOperationToManualReviewWithoutTouchingCode() {
        properties.setMaxRetries(2);
        operation.setRetryCount(2);
        stubClaimedState();
        when(renewalMapper.markManualReview(RENEWAL_ID, RENEWAL_VERSION,
                "REMOTE_UNKNOWN", "remote result unknown", NOW)).thenReturn(1);
        when(operationMapper.markManualReview(OPERATION_ID, OP_VERSION,
                "REMOTE_UNKNOWN", "remote result unknown", NOW)).thenReturn(1);

        assertTrue(stateService.retryOrMarkManualReview(operation, renewal,
                "REMOTE_UNKNOWN", "remote result unknown"));

        verify(renewalMapper).markManualReview(RENEWAL_ID, RENEWAL_VERSION,
                "REMOTE_UNKNOWN", "remote result unknown", NOW);
        verify(operationMapper).markManualReview(OPERATION_ID, OP_VERSION,
                "REMOTE_UNKNOWN", "remote result unknown", NOW);
        verify(codeMapper, never()).releaseRenewalCode(any(), anyString(), any(), any());
    }

    @Test
    void staleClaimVersionCannotChangeRows() {
        CorsOperation changed = operation("CLAIMED", OP_VERSION + 1, 2);
        when(operationMapper.selectByIdForUpdate(OPERATION_ID)).thenReturn(changed);

        assertFalse(stateService.definitiveFail(operation, renewal, "REJECTED", "rejected"));

        verify(renewalMapper, never()).selectByIdForUpdate(RENEWAL_ID);
        verify(codeMapper, never()).selectByIdForUpdate(CODE_ID);
    }

    @Test
    void definitiveFailureReleasesOnlyTheMatchingReservedCodeAtomically() {
        ServiceCode code = reservedCode();
        stubClaimedState();
        when(codeMapper.selectByIdForUpdate(CODE_ID)).thenReturn(code);
        when(codeMapper.releaseRenewalCode(CODE_ID, REQUEST_ID, CODE_VERSION, NOW)).thenReturn(1);
        when(renewalMapper.fail(RENEWAL_ID, RENEWAL_VERSION, "CORS_RENEWAL_REJECTED",
                "CORS rejected", NOW)).thenReturn(1);
        when(operationMapper.markFailed(OPERATION_ID, OP_VERSION, "CORS_RENEWAL_REJECTED",
                "CORS rejected", NOW)).thenReturn(1);

        assertTrue(stateService.definitiveFail(operation, renewal,
                "CORS_RENEWAL_REJECTED", "CORS rejected"));

        var order = inOrder(operationMapper, renewalMapper, codeMapper);
        order.verify(operationMapper).selectByIdForUpdate(OPERATION_ID);
        order.verify(renewalMapper).selectByIdForUpdate(RENEWAL_ID);
        order.verify(codeMapper).selectByIdForUpdate(CODE_ID);
        order.verify(codeMapper).releaseRenewalCode(CODE_ID, REQUEST_ID, CODE_VERSION, NOW);
        order.verify(renewalMapper).fail(RENEWAL_ID, RENEWAL_VERSION,
                "CORS_RENEWAL_REJECTED", "CORS rejected", NOW);
        order.verify(operationMapper).markFailed(OPERATION_ID, OP_VERSION,
                "CORS_RENEWAL_REJECTED", "CORS rejected", NOW);
    }

    @Test
    void failedReleaseCasRollsBackBeforeRenewalOrOperationCanFail() {
        stubClaimedState();
        when(codeMapper.selectByIdForUpdate(CODE_ID)).thenReturn(reservedCode());
        when(codeMapper.releaseRenewalCode(CODE_ID, REQUEST_ID, CODE_VERSION, NOW)).thenReturn(0);

        assertThrows(IllegalStateException.class,
                () -> stateService.definitiveFail(operation, renewal, "REJECTED", "rejected"));

        verify(renewalMapper, never()).fail(eq(RENEWAL_ID), any(), anyString(), anyString(), any());
        verify(operationMapper, never()).markFailed(eq(OPERATION_ID), any(), anyString(), anyString(), any());
    }

    @Test
    void manualReviewNeverReleasesServiceCodeAndFinalizationFallbackUsesNewTransaction() throws Exception {
        stubClaimedState();
        when(renewalMapper.markManualReview(RENEWAL_ID, RENEWAL_VERSION,
                "IDEMPOTENCY_CONFLICT", "review", NOW)).thenReturn(1);
        when(operationMapper.markManualReview(OPERATION_ID, OP_VERSION,
                "IDEMPOTENCY_CONFLICT", "review", NOW)).thenReturn(1);

        assertTrue(stateService.markManualReview(operation, renewal, "IDEMPOTENCY_CONFLICT", "review"));
        verify(codeMapper, never()).releaseRenewalCode(any(), anyString(), any(), any());

        Transactional annotation = AccountRenewalStateService.class
                .getMethod("markManualReviewAfterFinalizeFailure", CorsOperation.class,
                        AccountRenewal.class, String.class, String.class)
                .getAnnotation(Transactional.class);
        assertEquals(Propagation.REQUIRES_NEW, annotation.propagation());
    }

    @Test
    void staleClaimRecoverySchedulesQueryFirstRetryAndExhaustionKeepsCodeReserved() {
        CorsOperation stale = operation("CLAIMED", OP_VERSION, 0);
        operation.setRetryCount(0);
        stubClaimedState();
        when(renewalMapper.updateRetryError(RENEWAL_ID, RENEWAL_VERSION, "CLAIM_TIMEOUT",
                "Stale account renewal claim recovered; next attempt will query requestId", NOW)).thenReturn(1);
        when(operationMapper.recoverClaimed(eq(OPERATION_ID), eq(OP_VERSION), eq("RETRY_WAIT"), eq(1),
                eq(NOW), eq("CLAIM_TIMEOUT"), anyString(), eq(NOW))).thenReturn(1);

        assertTrue(stateService.recoverStaleClaim(stale));
        verify(operationMapper).recoverClaimed(OPERATION_ID, OP_VERSION, "RETRY_WAIT", 1,
                NOW, "CLAIM_TIMEOUT", "Stale account renewal claim recovered; next attempt will query requestId", NOW);
        verify(renewalMapper).updateRetryError(RENEWAL_ID, RENEWAL_VERSION, "CLAIM_TIMEOUT",
                "Stale account renewal claim recovered; next attempt will query requestId", NOW);
        verify(codeMapper, never()).selectByIdForUpdate(CODE_ID);

        properties.setMaxRetries(0);
        CorsOperation exhausted = operation("CLAIMED", OP_VERSION, 0);
        operation.setRetryCount(0);
        stubClaimedState();
        when(renewalMapper.markManualReview(eq(RENEWAL_ID), eq(RENEWAL_VERSION),
                eq("CLAIM_TIMEOUT_EXHAUSTED"), anyString(), eq(NOW))).thenReturn(1);
        when(operationMapper.recoverClaimed(eq(OPERATION_ID), eq(OP_VERSION), eq("MANUAL_REVIEW"), eq(1),
                eq(null), eq("CLAIM_TIMEOUT_EXHAUSTED"), anyString(), eq(NOW))).thenReturn(1);

        assertTrue(stateService.recoverStaleClaim(exhausted));
        verify(codeMapper, never()).releaseRenewalCode(any(), anyString(), any(), any());
    }

    private void stubClaimedState() {
        when(operationMapper.selectByIdForUpdate(OPERATION_ID)).thenReturn(operation);
        when(renewalMapper.selectByIdForUpdate(RENEWAL_ID)).thenReturn(renewal);
    }

    private static CorsOperation operation(String status, long version, int retryCount) {
        CorsOperation operation = new CorsOperation();
        operation.setId(OPERATION_ID);
        operation.setRequestId(REQUEST_ID);
        operation.setOperationType("RENEW_ACCOUNT");
        operation.setBizType("ACCOUNT_RENEWAL");
        operation.setBizId(RENEWAL_ID);
        operation.setServiceAccountId(ACCOUNT_ID);
        operation.setStatus(status);
        operation.setRetryCount(retryCount);
        operation.setVersion(version);
        return operation;
    }

    private static AccountRenewal renewal(String status, long version) {
        AccountRenewal renewal = new AccountRenewal();
        renewal.setId(RENEWAL_ID);
        renewal.setServiceAccountId(ACCOUNT_ID);
        renewal.setServiceCodeId(CODE_ID);
        renewal.setRequestId(REQUEST_ID);
        renewal.setStatus(status);
        renewal.setVersion(version);
        return renewal;
    }

    private static ServiceCode reservedCode() {
        ServiceCode code = new ServiceCode();
        code.setId(CODE_ID);
        code.setStatus(ServiceCodeStatus.PROCESSING);
        code.setProcessingType(ProcessingType.RENEWAL);
        code.setProcessingRequestId(REQUEST_ID);
        code.setVersion(CODE_VERSION);
        return code;
    }
}
