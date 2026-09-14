package com.sinognss.cloud.vantix.application.password.reset;

import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.domain.password.AccountPasswordAction;
import com.sinognss.cloud.vantix.integration.cors.CorsOutcome;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountPasswordGateway;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountSnapshot;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountStatusGateway;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountStatusResult;
import com.sinognss.cloud.vantix.integration.cors.account.CorsPasswordResetRequest;
import com.sinognss.cloud.vantix.integration.cors.account.CorsPasswordResetResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountPasswordResetProcessorTest {
    private static final long OPERATION_ID = 17L;
    private static final long ACTION_ID = 23L;
    private static final long SERVICE_ACCOUNT_ID = 31L;
    private static final long OPERATION_VERSION = 8L;
    private static final long ACTION_VERSION = 4L;
    private static final String REQUEST_ID = "PWD-RS-17";
    private static final String CORS_ACCOUNT_ID = "cors-account-31";
    private static final String ACCOUNT = "account-31";

    @Mock private AccountPasswordResetClaimService claimService;
    @Mock private AccountPasswordResetStateService stateService;
    @Mock private AccountPasswordResetFinalizeService finalizeService;
    @Mock private CorsAccountStatusGateway statusGateway;
    @Mock private CorsAccountPasswordGateway passwordGateway;

    private AccountPasswordResetProcessor processor;
    private CorsOperation operation;
    private AccountPasswordAction action;

    @BeforeEach
    void setUp() {
        processor = new AccountPasswordResetProcessor(claimService, stateService, finalizeService,
                statusGateway, passwordGateway);
        operation = operation();
        action = action();
        when(claimService.claim(OPERATION_ID)).thenReturn(
                new ClaimedAccountPasswordReset(operation, action, false));
    }

    @Test
    void pendingPreflightsThenPostsOriginalRequestId() {
        when(statusGateway.getAccount(CORS_ACCOUNT_ID)).thenReturn(CorsAccountStatusResult.success(snapshot()));
        when(passwordGateway.resetPassword(request())).thenReturn(success());

        processor.process(OPERATION_ID);

        InOrder order = inOrder(statusGateway, passwordGateway, finalizeService);
        order.verify(statusGateway).getAccount(CORS_ACCOUNT_ID);
        order.verify(passwordGateway).resetPassword(request());
        order.verify(finalizeService).finalizeSuccess(operation, action, success());
        verify(passwordGateway, never()).queryPasswordReset(anyString());
        verify(stateService, never()).retryOrMarkManualReview(any(), any(), anyString());
    }

    @Test
    void retrySuccessQueriesAndFinalizesWithoutPreflightOrPost() {
        when(claimService.claim(OPERATION_ID)).thenReturn(
                new ClaimedAccountPasswordReset(operation, action, true));
        when(passwordGateway.queryPasswordReset(REQUEST_ID)).thenReturn(success());

        processor.process(OPERATION_ID);

        verify(finalizeService).finalizeSuccess(operation, action, success());
        verify(statusGateway, never()).getAccount(anyString());
        verify(passwordGateway, never()).resetPassword(any());
    }

    @Test
    void retryNotFoundQueriesThenPreflightsThenPostsSameRequestId() {
        when(claimService.claim(OPERATION_ID)).thenReturn(
                new ClaimedAccountPasswordReset(operation, action, true));
        when(passwordGateway.queryPasswordReset(REQUEST_ID)).thenReturn(
                new CorsPasswordResetResult(CorsOutcome.NOT_FOUND, REQUEST_ID, CORS_ACCOUNT_ID, "remote text"));
        when(statusGateway.getAccount(CORS_ACCOUNT_ID)).thenReturn(CorsAccountStatusResult.success(snapshot()));
        when(passwordGateway.resetPassword(request())).thenReturn(unknown());

        processor.process(OPERATION_ID);

        InOrder order = inOrder(passwordGateway, statusGateway);
        order.verify(passwordGateway).queryPasswordReset(REQUEST_ID);
        order.verify(statusGateway).getAccount(CORS_ACCOUNT_ID);
        order.verify(passwordGateway).resetPassword(request());
        verify(stateService).retryOrMarkManualReview(operation, action, AccountPasswordResetFailure.POST_UNKNOWN);
    }

    @Test
    void retryUnknownQueryStopsWithoutPreflightOrPost() {
        when(claimService.claim(OPERATION_ID)).thenReturn(
                new ClaimedAccountPasswordReset(operation, action, true));
        when(passwordGateway.queryPasswordReset(REQUEST_ID)).thenReturn(
                new CorsPasswordResetResult(CorsOutcome.UNKNOWN, REQUEST_ID, CORS_ACCOUNT_ID, "untrusted text"));

        processor.process(OPERATION_ID);

        verify(stateService).retryOrMarkManualReview(operation, action, AccountPasswordResetFailure.QUERY_UNKNOWN);
        verify(statusGateway, never()).getAccount(anyString());
        verify(passwordGateway, never()).resetPassword(any());
    }

    @Test
    void retryQueryConflictRequiresReviewWithoutPreflightOrPost() {
        when(claimService.claim(OPERATION_ID)).thenReturn(
                new ClaimedAccountPasswordReset(operation, action, true));
        when(passwordGateway.queryPasswordReset(REQUEST_ID)).thenReturn(
                new CorsPasswordResetResult(CorsOutcome.IDEMPOTENCY_CONFLICT,
                        REQUEST_ID, CORS_ACCOUNT_ID, "untrusted"));

        processor.process(OPERATION_ID);

        verify(stateService).markManualReview(operation, action,
                AccountPasswordResetFailure.QUERY_IDEMPOTENCY_CONFLICT);
        verify(statusGateway, never()).getAccount(anyString());
        verify(passwordGateway, never()).resetPassword(any());
    }

    @Test
    void retryQueryDefinitiveRejectFailsWithoutPreflightOrPost() {
        when(claimService.claim(OPERATION_ID)).thenReturn(
                new ClaimedAccountPasswordReset(operation, action, true));
        when(passwordGateway.queryPasswordReset(REQUEST_ID)).thenReturn(
                new CorsPasswordResetResult(CorsOutcome.DEFINITIVE_REJECT,
                        REQUEST_ID, CORS_ACCOUNT_ID, "untrusted"));

        processor.process(OPERATION_ID);

        verify(stateService).definitiveFail(operation, action,
                AccountPasswordResetFailure.QUERY_DEFINITIVE_REJECT);
        verify(statusGateway, never()).getAccount(anyString());
        verify(passwordGateway, never()).resetPassword(any());
    }

    @Test
    void queryTimeoutStopsWithoutPostAndDoesNotForwardExceptionText() {
        when(claimService.claim(OPERATION_ID)).thenReturn(
                new ClaimedAccountPasswordReset(operation, action, true));
        doThrow(new IllegalStateException("must not persist or log this"))
                .when(passwordGateway).queryPasswordReset(REQUEST_ID);

        processor.process(OPERATION_ID);

        verify(stateService).retryOrMarkManualReview(operation, action, AccountPasswordResetFailure.QUERY_UNKNOWN);
        verify(statusGateway, never()).getAccount(anyString());
        verify(passwordGateway, never()).resetPassword(any());
    }

    @Test
    void preflightNotFoundFailsWithoutResetPost() {
        when(statusGateway.getAccount(CORS_ACCOUNT_ID))
                .thenReturn(CorsAccountStatusResult.notFound("untrusted", "untrusted body"));

        processor.process(OPERATION_ID);

        verify(stateService).definitiveFail(operation, action, AccountPasswordResetFailure.ACCOUNT_NOT_FOUND);
        verify(passwordGateway, never()).resetPassword(any());
    }

    @Test
    void preflightUnknownRetriesWithoutResetPost() {
        when(statusGateway.getAccount(CORS_ACCOUNT_ID))
                .thenReturn(CorsAccountStatusResult.unknown("untrusted", "untrusted body"));

        processor.process(OPERATION_ID);

        verify(stateService).retryOrMarkManualReview(operation, action,
                AccountPasswordResetFailure.PREFLIGHT_UNKNOWN);
        verify(passwordGateway, never()).resetPassword(any());
    }

    @Test
    void retryNotFoundThenPreflightUnknownStopsWithoutResetPost() {
        when(claimService.claim(OPERATION_ID)).thenReturn(
                new ClaimedAccountPasswordReset(operation, action, true));
        when(passwordGateway.queryPasswordReset(REQUEST_ID)).thenReturn(
                new CorsPasswordResetResult(CorsOutcome.NOT_FOUND, REQUEST_ID, CORS_ACCOUNT_ID, null));
        when(statusGateway.getAccount(CORS_ACCOUNT_ID))
                .thenReturn(CorsAccountStatusResult.unknown("untrusted", "untrusted body"));

        processor.process(OPERATION_ID);

        verify(stateService).retryOrMarkManualReview(operation, action,
                AccountPasswordResetFailure.PREFLIGHT_UNKNOWN);
        verify(passwordGateway, never()).resetPassword(any());
    }

    @Test
    void retryNotFoundThenMalformedPreflightStopsWithoutResetPost() {
        when(claimService.claim(OPERATION_ID)).thenReturn(
                new ClaimedAccountPasswordReset(operation, action, true));
        when(passwordGateway.queryPasswordReset(REQUEST_ID)).thenReturn(
                new CorsPasswordResetResult(CorsOutcome.NOT_FOUND, REQUEST_ID, CORS_ACCOUNT_ID, null));
        when(statusGateway.getAccount(CORS_ACCOUNT_ID)).thenReturn(null);

        processor.process(OPERATION_ID);

        verify(stateService).retryOrMarkManualReview(operation, action,
                AccountPasswordResetFailure.PREFLIGHT_MALFORMED);
        verify(passwordGateway, never()).resetPassword(any());
    }

    @Test
    void retryNotFoundThenPreflightExceptionStopsWithoutResetPost() {
        when(claimService.claim(OPERATION_ID)).thenReturn(
                new ClaimedAccountPasswordReset(operation, action, true));
        when(passwordGateway.queryPasswordReset(REQUEST_ID)).thenReturn(
                new CorsPasswordResetResult(CorsOutcome.NOT_FOUND, REQUEST_ID, CORS_ACCOUNT_ID, null));
        doThrow(new IllegalStateException("untrusted transport exception"))
                .when(statusGateway).getAccount(CORS_ACCOUNT_ID);

        processor.process(OPERATION_ID);

        verify(stateService).retryOrMarkManualReview(operation, action,
                AccountPasswordResetFailure.PREFLIGHT_UNKNOWN);
        verify(passwordGateway, never()).resetPassword(any());
    }

    @Test
    void preflightIdentityMismatchRequiresReviewWithoutResetPost() {
        when(statusGateway.getAccount(CORS_ACCOUNT_ID)).thenReturn(CorsAccountStatusResult.success(
                new CorsAccountSnapshot("different-id", ACCOUNT, "DISABLED", "WAITING_ACTIVATION",
                        null, null, time("2026-09-01T00:00:00Z"), time("2026-09-14T12:00:00Z"))));

        processor.process(OPERATION_ID);

        verify(stateService).markManualReview(operation, action,
                AccountPasswordResetFailure.PREFLIGHT_IDENTITY_MISMATCH);
        verify(passwordGateway, never()).resetPassword(any());
    }

    @Test
    void resetTimeoutRetriesAndNeverImmediatelyPostsAgain() {
        when(statusGateway.getAccount(CORS_ACCOUNT_ID)).thenReturn(CorsAccountStatusResult.success(snapshot()));
        doThrow(new IllegalStateException("request may have completed"))
                .when(passwordGateway).resetPassword(request());

        processor.process(OPERATION_ID);

        verify(passwordGateway).resetPassword(request());
        verify(passwordGateway, never()).queryPasswordReset(anyString());
        verify(stateService).retryOrMarkManualReview(operation, action, AccountPasswordResetFailure.POST_UNKNOWN);
    }

    @Test
    void postNotFoundIsUnknownAndSchedulesQueryFirstRetry() {
        when(statusGateway.getAccount(CORS_ACCOUNT_ID)).thenReturn(CorsAccountStatusResult.success(snapshot()));
        when(passwordGateway.resetPassword(request())).thenReturn(
                new CorsPasswordResetResult(CorsOutcome.NOT_FOUND, REQUEST_ID, CORS_ACCOUNT_ID, "remote text"));

        processor.process(OPERATION_ID);

        verify(stateService).retryOrMarkManualReview(operation, action, AccountPasswordResetFailure.POST_UNKNOWN);
        verify(stateService, never()).definitiveFail(any(), any(), anyString());
    }

    @Test
    void postIdempotencyConflictRequiresManualReview() {
        when(statusGateway.getAccount(CORS_ACCOUNT_ID)).thenReturn(CorsAccountStatusResult.success(snapshot()));
        when(passwordGateway.resetPassword(request())).thenReturn(
                new CorsPasswordResetResult(CorsOutcome.IDEMPOTENCY_CONFLICT, REQUEST_ID, CORS_ACCOUNT_ID,
                        "remote text"));

        processor.process(OPERATION_ID);

        verify(stateService).markManualReview(operation, action,
                AccountPasswordResetFailure.POST_IDEMPOTENCY_CONFLICT);
        verify(stateService, never()).retryOrMarkManualReview(any(), any(), anyString());
    }

    @Test
    void definitivePostRejectFails() {
        when(statusGateway.getAccount(CORS_ACCOUNT_ID)).thenReturn(CorsAccountStatusResult.success(snapshot()));
        when(passwordGateway.resetPassword(request())).thenReturn(
                new CorsPasswordResetResult(CorsOutcome.DEFINITIVE_REJECT, REQUEST_ID, CORS_ACCOUNT_ID,
                        "remote text"));

        processor.process(OPERATION_ID);

        verify(stateService).definitiveFail(operation, action,
                AccountPasswordResetFailure.POST_DEFINITIVE_REJECT);
    }

    @Test
    void successWithWrongRequestIdRequiresReviewAndCannotFinalize() {
        when(statusGateway.getAccount(CORS_ACCOUNT_ID)).thenReturn(CorsAccountStatusResult.success(snapshot()));
        when(passwordGateway.resetPassword(request())).thenReturn(
                new CorsPasswordResetResult(CorsOutcome.SUCCESS, "OTHER-ID", CORS_ACCOUNT_ID, null));

        processor.process(OPERATION_ID);

        verify(stateService).markManualReview(operation, action,
                AccountPasswordResetFailure.POST_IDENTITY_MISMATCH);
        verify(finalizeService, never()).finalizeSuccess(any(), any(), any());
        verify(passwordGateway, never()).queryPasswordReset(anyString());
    }

    @Test
    void successWithWrongAccountIdRequiresReviewAndCannotFinalize() {
        when(statusGateway.getAccount(CORS_ACCOUNT_ID)).thenReturn(CorsAccountStatusResult.success(snapshot()));
        when(passwordGateway.resetPassword(request())).thenReturn(
                new CorsPasswordResetResult(CorsOutcome.SUCCESS, REQUEST_ID, "different-account-id", null));

        processor.process(OPERATION_ID);

        verify(stateService).markManualReview(operation, action,
                AccountPasswordResetFailure.POST_IDENTITY_MISMATCH);
        verify(finalizeService, never()).finalizeSuccess(any(), any(), any());
    }

    @Test
    void remoteSuccessLocalFinalizeFailureMovesToManualReview() {
        when(statusGateway.getAccount(CORS_ACCOUNT_ID)).thenReturn(CorsAccountStatusResult.success(snapshot()));
        when(passwordGateway.resetPassword(request())).thenReturn(success());
        doThrow(new IllegalStateException("database failure"))
                .when(finalizeService).finalizeSuccess(operation, action, success());

        processor.process(OPERATION_ID);

        verify(stateService).markManualReviewAfterFinalizeFailure(operation, action);
        verify(passwordGateway).resetPassword(request());
        verify(passwordGateway, never()).queryPasswordReset(anyString());
        verify(stateService, never()).retryOrMarkManualReview(any(), any(), anyString());
    }

    private static CorsOperation operation() {
        CorsOperation operation = new CorsOperation();
        operation.setId(OPERATION_ID);
        operation.setRequestId(REQUEST_ID);
        operation.setOperationType(AccountPasswordResetConstants.OPERATION_TYPE);
        operation.setBizType(AccountPasswordResetConstants.BIZ_TYPE);
        operation.setBizId(ACTION_ID);
        operation.setServiceAccountId(SERVICE_ACCOUNT_ID);
        operation.setStatus(AccountPasswordResetConstants.CLAIMED);
        operation.setVersion(OPERATION_VERSION);
        operation.setRetryCount(0);
        return operation;
    }

    private static AccountPasswordAction action() {
        AccountPasswordAction action = new AccountPasswordAction();
        action.setId(ACTION_ID);
        action.setRequestId(REQUEST_ID);
        action.setActionType("RESET");
        action.setServiceAccountId(SERVICE_ACCOUNT_ID);
        action.setOwnerCompanyId(91L);
        action.setCorsAccountId(CORS_ACCOUNT_ID);
        action.setAccount(ACCOUNT);
        action.setStatus("PROCESSING");
        action.setVersion(ACTION_VERSION);
        return action;
    }

    private static CorsPasswordResetRequest request() {
        return new CorsPasswordResetRequest(REQUEST_ID, CORS_ACCOUNT_ID);
    }

    private static CorsPasswordResetResult success() {
        return new CorsPasswordResetResult(CorsOutcome.SUCCESS, REQUEST_ID, CORS_ACCOUNT_ID, null);
    }

    private static CorsPasswordResetResult unknown() {
        return new CorsPasswordResetResult(CorsOutcome.UNKNOWN, REQUEST_ID, CORS_ACCOUNT_ID, "remote text");
    }

    private static CorsAccountSnapshot snapshot() {
        return new CorsAccountSnapshot(CORS_ACCOUNT_ID, ACCOUNT, "DISABLED", "WAITING_ACTIVATION",
                null, null, time("2026-09-01T00:00:00Z"), time("2026-09-14T12:00:00Z"));
    }

    private static OffsetDateTime time(String value) {
        return OffsetDateTime.parse(value);
    }
}
