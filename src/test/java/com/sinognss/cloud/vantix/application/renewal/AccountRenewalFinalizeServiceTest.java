package com.sinognss.cloud.vantix.application.renewal;

import com.sinognss.cloud.vantix.application.cors.account.AccountStatusSyncScheduleService;
import com.sinognss.cloud.vantix.application.cors.account.AccountStatusSyncSuccessSchedule;
import com.sinognss.cloud.vantix.application.cors.account.CorsAccountStateApplyOutcome;
import com.sinognss.cloud.vantix.application.cors.account.CorsAccountStateApplyService;
import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.domain.renewal.AccountRenewal;
import com.sinognss.cloud.vantix.domain.servicecode.ProcessingType;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCode;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeStatus;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountRenewalResult;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountSnapshot;
import com.sinognss.cloud.vantix.infrastructure.mapper.AccountRenewalMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.CorsOperationMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountRenewalFinalizeServiceTest {
    private static final long OPERATION_ID = 31L;
    private static final long RENEWAL_ID = 51L;
    private static final long ACCOUNT_ID = 61L;
    private static final long CODE_ID = 71L;
    private static final long OPERATION_VERSION = 6L;
    private static final long RENEWAL_VERSION = 8L;
    private static final long CODE_VERSION = 12L;
    private static final String REQUEST_ID = "RN-test-31";
    private static final String CORS_ACCOUNT_ID = "cors-61";
    private static final String ACCOUNT_NAME = "account-61";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 14, 12, 0);

    @Mock private CorsOperationMapper operationMapper;
    @Mock private AccountRenewalMapper renewalMapper;
    @Mock private ServiceAccountMapper accountMapper;
    @Mock private ServiceCodeMapper codeMapper;
    @Mock private CorsAccountStateApplyService applyService;
    @Mock private AccountStatusSyncScheduleService scheduleService;

    private AccountRenewalFinalizeService finalizeService;
    private CorsOperation operation;
    private AccountRenewal renewal;
    private ServiceAccount account;
    private ServiceCode code;
    private CorsAccountSnapshot snapshot;
    private CorsAccountRenewalResult result;
    private AccountStatusSyncSuccessSchedule schedule;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-14T12:00:00Z"), ZoneOffset.UTC);
        finalizeService = new AccountRenewalFinalizeService(operationMapper, renewalMapper,
                accountMapper, codeMapper, applyService, scheduleService, clock);
        operation = operation();
        renewal = renewal();
        account = account();
        code = reservedCode();
        snapshot = snapshot();
        result = CorsAccountRenewalResult.success(REQUEST_ID, snapshot);
        schedule = new AccountStatusSyncSuccessSchedule(NOW, NOW.plusHours(12));
        when(operationMapper.selectByIdForUpdate(OPERATION_ID)).thenReturn(operation);
        when(renewalMapper.selectByIdForUpdate(RENEWAL_ID)).thenReturn(renewal);
        when(accountMapper.selectByIdForUpdate(ACCOUNT_ID)).thenReturn(account);
        when(codeMapper.selectByIdForUpdate(CODE_ID)).thenReturn(code);
        lenient().when(scheduleService.successSchedule()).thenReturn(schedule);
    }

    @Test
    void successfulFinalizeAppliesCorsStateConsumesCodeAndCompletesAllRows() {
        when(applyService.apply(account, snapshot, schedule)).thenReturn(CorsAccountStateApplyOutcome.UPDATED);
        when(codeMapper.consumeRenewalCode(CODE_ID, REQUEST_ID, CODE_VERSION, NOW)).thenReturn(1);
        when(renewalMapper.complete(RENEWAL_ID, RENEWAL_VERSION, NOW)).thenReturn(1);
        when(operationMapper.markSucceeded(OPERATION_ID, OPERATION_VERSION, NOW)).thenReturn(1);

        finalizeService.finalizeSuccess(OPERATION_ID, OPERATION_VERSION, result);

        InOrder order = inOrder(operationMapper, renewalMapper, accountMapper, codeMapper,
                scheduleService, applyService);
        order.verify(operationMapper).selectByIdForUpdate(OPERATION_ID);
        order.verify(renewalMapper).selectByIdForUpdate(RENEWAL_ID);
        order.verify(accountMapper).selectByIdForUpdate(ACCOUNT_ID);
        order.verify(codeMapper).selectByIdForUpdate(CODE_ID);
        order.verify(scheduleService).successSchedule();
        order.verify(applyService).apply(account, snapshot, schedule);
        order.verify(codeMapper).consumeRenewalCode(CODE_ID, REQUEST_ID, CODE_VERSION, NOW);
        order.verify(renewalMapper).complete(RENEWAL_ID, RENEWAL_VERSION, NOW);
        order.verify(operationMapper).markSucceeded(OPERATION_ID, OPERATION_VERSION, NOW);
    }

    @Test
    void staleIgnoredAccountSnapshotCanCompleteWhenLocalStateIsNewer() {
        CorsAccountSnapshot staleSnapshot = snapshotWithUpdatedAt("2026-09-14T03:00:00Z");
        ServiceAccount newerLocalState = newerLocalState();
        stubStaleApply(staleSnapshot, newerLocalState);
        when(codeMapper.consumeRenewalCode(CODE_ID, REQUEST_ID, CODE_VERSION, NOW)).thenReturn(1);
        when(renewalMapper.complete(RENEWAL_ID, RENEWAL_VERSION, NOW)).thenReturn(1);
        when(operationMapper.markSucceeded(OPERATION_ID, OPERATION_VERSION, NOW)).thenReturn(1);

        finalizeService.finalizeSuccess(OPERATION_ID, OPERATION_VERSION,
                CorsAccountRenewalResult.success(REQUEST_ID, staleSnapshot));

        verify(accountMapper, org.mockito.Mockito.times(2)).selectByIdForUpdate(ACCOUNT_ID);
        verify(codeMapper).consumeRenewalCode(CODE_ID, REQUEST_ID, CODE_VERSION, NOW);
        verify(renewalMapper).complete(RENEWAL_ID, RENEWAL_VERSION, NOW);
        verify(operationMapper).markSucceeded(OPERATION_ID, OPERATION_VERSION, NOW);
    }

    @Test
    void staleIgnoredRequiresLocalUpdatedAtAtOrAfterRemoteTimeAfterZoneConversion() {
        CorsAccountSnapshot staleSnapshot = snapshotWithUpdatedAt("2026-09-14T04:30:00Z");
        ServiceAccount current = newerLocalState();
        current.setCorsUpdatedAt(LocalDateTime.of(2026, 9, 14, 12, 0));
        stubStaleApply(staleSnapshot, current);

        assertThrows(IllegalStateException.class,
                () -> finalizeService.finalizeSuccess(OPERATION_ID, OPERATION_VERSION,
                        CorsAccountRenewalResult.success(REQUEST_ID, staleSnapshot)));

        verifyNoCompletion();
    }

    @Test
    void staleIgnoredRequiresLocalUpdatedAtToBePresent() {
        CorsAccountSnapshot staleSnapshot = snapshotWithUpdatedAt("2026-09-14T03:00:00Z");
        ServiceAccount current = newerLocalState();
        current.setCorsUpdatedAt(null);
        stubStaleApply(staleSnapshot, current);

        assertThrows(IllegalStateException.class,
                () -> finalizeService.finalizeSuccess(OPERATION_ID, OPERATION_VERSION,
                        CorsAccountRenewalResult.success(REQUEST_ID, staleSnapshot)));

        verifyNoCompletion();
    }

    @Test
    void staleIgnoredRejectsChangedCorsAccountIdentity() {
        CorsAccountSnapshot staleSnapshot = snapshotWithUpdatedAt("2026-09-14T03:00:00Z");
        ServiceAccount current = newerLocalState();
        current.setCorsAccountId("different-cors-id");
        stubStaleApply(staleSnapshot, current);

        assertThrows(IllegalStateException.class,
                () -> finalizeService.finalizeSuccess(OPERATION_ID, OPERATION_VERSION,
                        CorsAccountRenewalResult.success(REQUEST_ID, staleSnapshot)));

        verifyNoCompletion();
    }

    @Test
    void staleIgnoredRejectsChangedAccountName() {
        CorsAccountSnapshot staleSnapshot = snapshotWithUpdatedAt("2026-09-14T03:00:00Z");
        ServiceAccount current = newerLocalState();
        current.setAccount("different-account");
        stubStaleApply(staleSnapshot, current);

        assertThrows(IllegalStateException.class,
                () -> finalizeService.finalizeSuccess(OPERATION_ID, OPERATION_VERSION,
                        CorsAccountRenewalResult.success(REQUEST_ID, staleSnapshot)));

        verifyNoCompletion();
    }

    @Test
    void successWithWrongRequestIdIsRejectedBeforeAccountApply() {
        CorsAccountRenewalResult wrongRequest = CorsAccountRenewalResult.success("OTHER", snapshot);

        assertThrows(IllegalStateException.class,
                () -> finalizeService.finalizeSuccess(OPERATION_ID, OPERATION_VERSION, wrongRequest));

        verify(applyService, never()).apply(any(), any(), any());
        verify(codeMapper, never()).consumeRenewalCode(any(), any(), any(), any());
    }

    @Test
    void serviceCodeConsumeCasConflictPreventsRenewalAndOperationCompletion() {
        when(applyService.apply(account, snapshot, schedule)).thenReturn(CorsAccountStateApplyOutcome.UPDATED);
        when(codeMapper.consumeRenewalCode(CODE_ID, REQUEST_ID, CODE_VERSION, NOW)).thenReturn(0);

        assertThrows(IllegalStateException.class,
                () -> finalizeService.finalizeSuccess(OPERATION_ID, OPERATION_VERSION, result));

        verify(renewalMapper, never()).complete(any(), any(), any());
        verify(operationMapper, never()).markSucceeded(any(), any(), any());
    }

    @Test
    void responseForDifferentCorsAccountIsRejectedBeforeApply() {
        CorsAccountSnapshot mismatched = new CorsAccountSnapshot("other", ACCOUNT_NAME,
                "ENABLED", "ACTIVE", snapshot.activatedAt(), snapshot.expireAt(),
                snapshot.createdAt(), snapshot.updatedAt());
        CorsAccountRenewalResult invalid = CorsAccountRenewalResult.success(REQUEST_ID, mismatched);

        assertThrows(IllegalStateException.class,
                () -> finalizeService.finalizeSuccess(OPERATION_ID, OPERATION_VERSION, invalid));

        verify(applyService, never()).apply(any(), any(), any());
    }

    private static CorsOperation operation() {
        CorsOperation operation = new CorsOperation();
        operation.setId(OPERATION_ID);
        operation.setRequestId(REQUEST_ID);
        operation.setOperationType("RENEW_ACCOUNT");
        operation.setBizType("ACCOUNT_RENEWAL");
        operation.setBizId(RENEWAL_ID);
        operation.setServiceAccountId(ACCOUNT_ID);
        operation.setStatus("CLAIMED");
        operation.setVersion(OPERATION_VERSION);
        return operation;
    }

    private static AccountRenewal renewal() {
        AccountRenewal renewal = new AccountRenewal();
        renewal.setId(RENEWAL_ID);
        renewal.setServiceAccountId(ACCOUNT_ID);
        renewal.setServiceCodeId(CODE_ID);
        renewal.setRequestId(REQUEST_ID);
        renewal.setStatus("PROCESSING");
        renewal.setServiceType("STANDARD");
        renewal.setSpecCode("SPEC-1");
        renewal.setDurationDays(90);
        renewal.setCodeSilenceDays(180);
        renewal.setVersion(RENEWAL_VERSION);
        return renewal;
    }

    private static ServiceAccount account() {
        ServiceAccount account = new ServiceAccount();
        account.setId(ACCOUNT_ID);
        account.setCorsAccountId(CORS_ACCOUNT_ID);
        account.setAccount(ACCOUNT_NAME);
        account.setServiceType("STANDARD");
        account.setVersion(2L);
        return account;
    }

    private static ServiceCode reservedCode() {
        ServiceCode code = new ServiceCode();
        code.setId(CODE_ID);
        code.setStatus(ServiceCodeStatus.PROCESSING);
        code.setProcessingType(ProcessingType.RENEWAL);
        code.setProcessingRequestId(REQUEST_ID);
        code.setSpecCode("SPEC-1");
        code.setServiceType("STANDARD");
        code.setDurationDays(90);
        code.setCodeSilenceDays(180);
        code.setVersion(CODE_VERSION);
        return code;
    }

    private static CorsAccountSnapshot snapshot() {
        return new CorsAccountSnapshot(CORS_ACCOUNT_ID, ACCOUNT_NAME, "ENABLED", "ACTIVE",
                time("2026-09-01T00:00:00+08:00"), time("2027-01-01T00:00:00+08:00"),
                time("2026-08-01T00:00:00+08:00"), time("2026-09-14T12:00:00+08:00"));
    }

    private static CorsAccountSnapshot snapshotWithUpdatedAt(String updatedAt) {
        return new CorsAccountSnapshot(CORS_ACCOUNT_ID, ACCOUNT_NAME, "ENABLED", "ACTIVE",
                time("2026-09-01T00:00:00+08:00"), time("2027-01-01T00:00:00+08:00"),
                time("2026-08-01T00:00:00+08:00"), OffsetDateTime.parse(updatedAt));
    }

    private ServiceAccount newerLocalState() {
        ServiceAccount current = account();
        current.setCorsStatus("ENABLED");
        current.setCorsActivationStatus("ACTIVE");
        current.setActivatedAt(LocalDateTime.of(2026, 9, 1, 0, 0));
        current.setExpireAt(LocalDateTime.of(2031, 2, 1, 0, 0));
        current.setCorsUpdatedAt(LocalDateTime.of(2026, 9, 14, 12, 0));
        current.setVersion(3L);
        return current;
    }

    private void stubStaleApply(CorsAccountSnapshot staleSnapshot, ServiceAccount current) {
        when(applyService.apply(account, staleSnapshot, schedule))
                .thenReturn(CorsAccountStateApplyOutcome.STALE_IGNORED);
        when(accountMapper.selectByIdForUpdate(ACCOUNT_ID)).thenReturn(account, current);
    }

    private void verifyNoCompletion() {
        verify(codeMapper, never()).consumeRenewalCode(any(), any(), any(), any());
        verify(renewalMapper, never()).complete(any(), any(), any());
        verify(operationMapper, never()).markSucceeded(any(), any(), any());
    }

    private static OffsetDateTime time(String value) {
        return OffsetDateTime.parse(value);
    }
}
