package com.sinognss.cloud.vantix.application.renewal;

import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.domain.renewal.AccountRenewal;
import com.sinognss.cloud.vantix.integration.cors.CorsOutcome;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountQueryOutcome;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountRenewalGateway;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountRenewalRequest;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountRenewalResult;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountSnapshot;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountStatusGateway;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountStatusResult;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountRenewalProcessorTest {
    private static final long OPERATION_ID = 31L;
    private static final long RENEWAL_ID = 51L;
    private static final long ACCOUNT_ID = 61L;
    private static final long CODE_ID = 71L;
    private static final long OPERATION_VERSION = 6L;
    private static final String REQUEST_ID = "RN-test-31";
    private static final String CORS_ACCOUNT_ID = "cors-61";
    private static final String ACCOUNT = "account-61";

    @Mock private AccountRenewalClaimService claimService;
    @Mock private AccountRenewalStateService stateService;
    @Mock private AccountRenewalFinalizeService finalizeService;
    @Mock private ServiceAccountMapper accountMapper;
    @Mock private CorsAccountStatusGateway statusGateway;
    @Mock private CorsAccountRenewalGateway renewalGateway;

    private AccountRenewalProcessor processor;
    private CorsOperation operation;
    private AccountRenewal renewal;
    private ServiceAccount account;

    @BeforeEach
    void setUp() {
        processor = new AccountRenewalProcessor(claimService, stateService, finalizeService,
                accountMapper, statusGateway, renewalGateway);
        operation = operation();
        renewal = renewal();
        account = account();
        when(claimService.claim(OPERATION_ID)).thenReturn(new ClaimedAccountRenewal(operation, renewal, false));
        when(accountMapper.selectById(ACCOUNT_ID)).thenReturn(account);
    }

    @Test
    void pendingPreflightsBeforePostingAndUsesFrozenRenewalRequest() {
        when(statusGateway.getAccount(CORS_ACCOUNT_ID)).thenReturn(statusSuccess(snapshot()));
        when(renewalGateway.renew(request())).thenReturn(result(CorsOutcome.UNKNOWN, "POST_UNKNOWN"));

        processor.process(OPERATION_ID);

        InOrder order = inOrder(statusGateway, renewalGateway);
        order.verify(statusGateway).getAccount(CORS_ACCOUNT_ID);
        order.verify(renewalGateway).renew(request());
        verify(renewalGateway, never()).queryRenewal(anyString());
        verify(stateService).retryOrMarkManualReview(eq(operation), eq(renewal),
                eq("POST_UNKNOWN"), anyString());
        verify(stateService, never()).definitiveFail(any(), any(), anyString(), anyString());
    }

    @Test
    void pendingPreflightNotFoundReleasesBeforeAnyRenewPost() {
        when(statusGateway.getAccount(CORS_ACCOUNT_ID))
                .thenReturn(CorsAccountStatusResult.notFound("CORS_ACCOUNT_NOT_FOUND", "missing"));

        processor.process(OPERATION_ID);

        verify(stateService).definitiveFail(operation, renewal, "CORS_ACCOUNT_NOT_FOUND",
                "CORS account was not found before renewal");
        verify(renewalGateway, never()).renew(any());
    }

    @Test
    void preflightNonActiveAccountIsDefinitivelyFailedWithoutPost() {
        when(statusGateway.getAccount(CORS_ACCOUNT_ID))
                .thenReturn(statusSuccess(snapshot("WAITING_ACTIVATION", "ENABLED", time("2026-09-01T00:00:00+08:00"))));

        processor.process(OPERATION_ID);

        verify(stateService).definitiveFail(operation, renewal, "ACCOUNT_NOT_ACTIVATED",
                "CORS account is not active for renewal");
        verify(renewalGateway, never()).renew(any());
    }

    @Test
    void expiredPreflightContinuesToRenewPost() {
        when(statusGateway.getAccount(CORS_ACCOUNT_ID))
                .thenReturn(statusSuccess(snapshot("EXPIRED", "ENABLED", time("2026-09-01T00:00:00+08:00"))));
        when(renewalGateway.renew(request())).thenReturn(result(CorsOutcome.UNKNOWN, "POST_UNKNOWN"));

        processor.process(OPERATION_ID);

        verify(renewalGateway).renew(request());
        verify(stateService).retryOrMarkManualReview(eq(operation), eq(renewal),
                eq("POST_UNKNOWN"), anyString());
    }

    @Test
    void preflightUnknownOrExceptionKeepsCodeReservedAndDoesNotPost() {
        when(statusGateway.getAccount(CORS_ACCOUNT_ID))
                .thenReturn(CorsAccountStatusResult.unknown("UPSTREAM_TIMEOUT", "unknown"));

        processor.process(OPERATION_ID);

        verify(stateService).retryOrMarkManualReview(operation, renewal, "UPSTREAM_TIMEOUT",
                "CORS account preflight result is unknown");
        verify(stateService, never()).definitiveFail(any(), any(), anyString(), anyString());
        verify(renewalGateway, never()).renew(any());
    }

    @Test
    void preflightIdentityMismatchRequiresReviewWithoutPost() {
        when(statusGateway.getAccount(CORS_ACCOUNT_ID))
                .thenReturn(statusSuccess(new CorsAccountSnapshot("some-other-id", ACCOUNT, "ENABLED", "ACTIVE",
                        time("2026-09-01T00:00:00+08:00"), time("2027-09-01T00:00:00+08:00"),
                        time("2026-08-01T00:00:00+08:00"), time("2026-09-01T00:00:00+08:00"))));

        processor.process(OPERATION_ID);

        verify(stateService).markManualReview(operation, renewal, "PREFLIGHT_IDENTITY_MISMATCH",
                "CORS account preflight identity does not match the local account");
        verify(renewalGateway, never()).renew(any());
    }

    @Test
    void retryQuerySuccessFinalizesWithoutPreflightOrPosting() {
        when(claimService.claim(OPERATION_ID)).thenReturn(new ClaimedAccountRenewal(operation, renewal, true));
        CorsAccountRenewalResult success = successResult(REQUEST_ID, snapshot());
        when(renewalGateway.queryRenewal(REQUEST_ID)).thenReturn(success);

        processor.process(OPERATION_ID);

        verify(finalizeService).finalizeSuccess(OPERATION_ID, OPERATION_VERSION, success);
        verify(statusGateway, never()).getAccount(anyString());
        verify(renewalGateway, never()).renew(any());
    }

    @Test
    void retryNotFoundQueriesThenPreflightsThenPostsWithOriginalRequestId() {
        when(claimService.claim(OPERATION_ID)).thenReturn(new ClaimedAccountRenewal(operation, renewal, true));
        when(renewalGateway.queryRenewal(REQUEST_ID))
                .thenReturn(CorsAccountRenewalResult.notFound(REQUEST_ID, "NOT_FOUND", "not found"));
        when(statusGateway.getAccount(CORS_ACCOUNT_ID)).thenReturn(statusSuccess(snapshot()));
        when(renewalGateway.renew(request())).thenReturn(result(CorsOutcome.NOT_FOUND, "POST_NOT_FOUND"));

        processor.process(OPERATION_ID);

        InOrder order = inOrder(renewalGateway, statusGateway);
        order.verify(renewalGateway).queryRenewal(REQUEST_ID);
        order.verify(statusGateway).getAccount(CORS_ACCOUNT_ID);
        order.verify(renewalGateway).renew(request());
        verify(stateService).retryOrMarkManualReview(operation, renewal, "POST_NOT_FOUND",
                "CORS renewal request result is unknown");
        verify(stateService, never()).definitiveFail(any(), any(), anyString(), anyString());
    }

    @Test
    void retryUnknownQueryStopsWithoutPreflightOrPost() {
        when(claimService.claim(OPERATION_ID)).thenReturn(new ClaimedAccountRenewal(operation, renewal, true));
        when(renewalGateway.queryRenewal(REQUEST_ID))
                .thenReturn(result(CorsOutcome.UNKNOWN, "QUERY_PENDING"));

        processor.process(OPERATION_ID);

        verify(stateService).retryOrMarkManualReview(operation, renewal,
                "QUERY_PENDING", "CORS renewal query result is unknown");
        verify(statusGateway, never()).getAccount(anyString());
        verify(renewalGateway, never()).renew(any());
    }

    @Test
    void retryIdempotencyConflictRequiresReviewAndKeepsCodeReserved() {
        when(claimService.claim(OPERATION_ID)).thenReturn(new ClaimedAccountRenewal(operation, renewal, true));
        when(renewalGateway.queryRenewal(REQUEST_ID))
                .thenReturn(result(CorsOutcome.IDEMPOTENCY_CONFLICT, "REMOTE_CONFLICT"));

        processor.process(OPERATION_ID);

        verify(stateService).markManualReview(operation, renewal, "IDEMPOTENCY_CONFLICT",
                "CORS renewal requestId conflicts with a different remote request");
        verify(statusGateway, never()).getAccount(anyString());
        verify(renewalGateway, never()).renew(any());
    }

    @Test
    void definitiveQueryRejectReleasesReservation() {
        when(claimService.claim(OPERATION_ID)).thenReturn(new ClaimedAccountRenewal(operation, renewal, true));
        when(renewalGateway.queryRenewal(REQUEST_ID))
                .thenReturn(result(CorsOutcome.DEFINITIVE_REJECT, "REMOTE_REJECT"));

        processor.process(OPERATION_ID);

        verify(stateService).definitiveFail(operation, renewal, "REMOTE_REJECT",
                "CORS definitively rejected the renewal without side effects");
        verify(statusGateway, never()).getAccount(anyString());
        verify(renewalGateway, never()).renew(any());
    }

    @Test
    void queryNotFoundWithWrongRequestIdRequiresReviewBeforeAnotherPost() {
        when(claimService.claim(OPERATION_ID)).thenReturn(new ClaimedAccountRenewal(operation, renewal, true));
        when(renewalGateway.queryRenewal(REQUEST_ID))
                .thenReturn(CorsAccountRenewalResult.notFound("OTHER-REQUEST", "NOT_FOUND", "not found"));

        processor.process(OPERATION_ID);

        verify(stateService).markManualReview(operation, renewal, "QUERY_REQUEST_ID_MISMATCH",
                "CORS renewal query response requestId does not match the original request");
        verify(statusGateway, never()).getAccount(anyString());
        verify(renewalGateway, never()).renew(any());
        verify(stateService, never()).definitiveFail(any(), any(), anyString(), anyString());
    }

    @Test
    void definitiveRejectWithoutMatchingRequestIdRequiresReviewAndKeepsReservation() {
        when(statusGateway.getAccount(CORS_ACCOUNT_ID)).thenReturn(statusSuccess(snapshot()));
        when(renewalGateway.renew(request())).thenReturn(
                CorsAccountRenewalResult.definitiveReject(null, "REJECTED", "no side effect"));

        processor.process(OPERATION_ID);

        verify(stateService).markManualReview(operation, renewal, "REJECT_REQUEST_ID_MISMATCH",
                "CORS definitive rejection is not correlated to the original request");
        verify(stateService, never()).definitiveFail(any(), any(), anyString(), anyString());
    }

    @Test
    void postUnknownKeepsReservationAndSchedulesRetry() {
        when(statusGateway.getAccount(CORS_ACCOUNT_ID)).thenReturn(statusSuccess(snapshot()));
        when(renewalGateway.renew(request())).thenReturn(result(CorsOutcome.UNKNOWN, "POST_PENDING"));

        processor.process(OPERATION_ID);

        verify(stateService).retryOrMarkManualReview(operation, renewal,
                "POST_PENDING", "CORS renewal request result is unknown");
        verify(stateService, never()).definitiveFail(any(), any(), anyString(), anyString());
    }

    @Test
    void postExceptionKeepsReservationAndSchedulesRetry() {
        when(statusGateway.getAccount(CORS_ACCOUNT_ID)).thenReturn(statusSuccess(snapshot()));
        doThrow(new IllegalStateException("connection reset")).when(renewalGateway).renew(request());

        processor.process(OPERATION_ID);

        verify(stateService).retryOrMarkManualReview(operation, renewal,
                "POST_UNKNOWN", "CORS renewal request result is unknown");
        verify(stateService, never()).definitiveFail(any(), any(), anyString(), anyString());
    }

    @Test
    void definitivePostRejectIsTheOnlyPostResultThatReleasesCode() {
        when(statusGateway.getAccount(CORS_ACCOUNT_ID)).thenReturn(statusSuccess(snapshot()));
        when(renewalGateway.renew(request())).thenReturn(result(CorsOutcome.DEFINITIVE_REJECT, "NO_SIDE_EFFECT"));

        processor.process(OPERATION_ID);

        verify(stateService).definitiveFail(operation, renewal, "NO_SIDE_EFFECT",
                "CORS definitively rejected the renewal without side effects");
        verify(stateService, never()).retryOrMarkManualReview(any(), any(), anyString(), anyString());
    }

    @Test
    void mismatchedSuccessCorrelationRequiresReviewAndNeverRetries() {
        when(statusGateway.getAccount(CORS_ACCOUNT_ID)).thenReturn(statusSuccess(snapshot()));
        CorsAccountRenewalResult success = successResult("OTHER-REQUEST", snapshot());
        when(renewalGateway.renew(request())).thenReturn(success);

        processor.process(OPERATION_ID);

        verify(stateService).markManualReview(operation, renewal, "POST_REQUEST_ID_MISMATCH",
                "CORS renewal response requestId does not match the original request");
        verify(finalizeService, never()).finalizeSuccess(eq(OPERATION_ID), any(), any());
        verify(stateService, never()).definitiveFail(any(), any(), anyString(), anyString());
        verify(stateService, never()).retryOrMarkManualReview(any(), any(), anyString(), anyString());
    }

    @Test
    void finalizeFailureMovesToManualReviewWithoutReleasingCode() {
        when(statusGateway.getAccount(CORS_ACCOUNT_ID)).thenReturn(statusSuccess(snapshot()));
        CorsAccountRenewalResult success = successResult(REQUEST_ID, snapshot());
        when(renewalGateway.renew(request())).thenReturn(success);
        doThrow(new IllegalStateException("database failure"))
                .when(finalizeService).finalizeSuccess(OPERATION_ID, OPERATION_VERSION, success);

        processor.process(OPERATION_ID);

        verify(stateService).markManualReviewAfterFinalizeFailure(operation, renewal,
                "LOCAL_FINALIZE_FAILED",
                "CORS reported renewal success but local finalize failed; manual review is required");
        verify(stateService, never()).definitiveFail(any(), any(), anyString(), anyString());
    }

    @Test
    void localAccountIdentityMismatchRequiresReviewWithoutCallingCors() {
        account.setId(999L);
        when(accountMapper.selectById(ACCOUNT_ID)).thenReturn(account);

        processor.process(OPERATION_ID);

        verify(stateService).markManualReview(operation, renewal, "OPERATION_ACCOUNT_MISMATCH",
                "Renewal operation account identity is inconsistent");
        verify(statusGateway, never()).getAccount(anyString());
        verify(renewalGateway, never()).queryRenewal(anyString());
        verify(renewalGateway, never()).renew(any());
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
        renewal.setVersion(8L);
        return renewal;
    }

    private static ServiceAccount account() {
        ServiceAccount account = new ServiceAccount();
        account.setId(ACCOUNT_ID);
        account.setCorsAccountId(CORS_ACCOUNT_ID);
        account.setAccount(ACCOUNT);
        account.setServiceType("STANDARD");
        account.setCorsActivationStatus("ACTIVE");
        account.setVersion(2L);
        return account;
    }

    private static CorsAccountRenewalRequest request() {
        return new CorsAccountRenewalRequest(REQUEST_ID, CORS_ACCOUNT_ID, 90);
    }

    private static CorsAccountStatusResult statusSuccess(CorsAccountSnapshot snapshot) {
        return CorsAccountStatusResult.success(snapshot);
    }

    private static CorsAccountSnapshot snapshot() {
        return snapshot("ACTIVE", "ENABLED", time("2026-09-01T00:00:00+08:00"));
    }

    private static CorsAccountSnapshot snapshot(String activationStatus, String accountStatus,
                                                OffsetDateTime activatedAt) {
        return new CorsAccountSnapshot(CORS_ACCOUNT_ID, ACCOUNT, accountStatus, activationStatus,
                activatedAt, time("2027-09-01T00:00:00+08:00"),
                time("2026-08-01T00:00:00+08:00"), time("2026-09-14T12:00:00+08:00"));
    }

    private static CorsAccountRenewalResult result(CorsOutcome outcome, String errorCode) {
        return new CorsAccountRenewalResult(outcome, REQUEST_ID, null, errorCode, "remote result");
    }

    private static CorsAccountRenewalResult successResult(String requestId, CorsAccountSnapshot snapshot) {
        return CorsAccountRenewalResult.success(requestId, snapshot);
    }

    private static OffsetDateTime time(String value) {
        return OffsetDateTime.parse(value);
    }
}
