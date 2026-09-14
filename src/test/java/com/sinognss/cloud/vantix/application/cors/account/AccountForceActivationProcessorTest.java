package com.sinognss.cloud.vantix.application.cors.account;

import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.integration.cors.CorsOutcome;
import com.sinognss.cloud.vantix.integration.cors.account.CorsForceActivationGateway;
import com.sinognss.cloud.vantix.integration.cors.account.CorsForceActivationRequest;
import com.sinognss.cloud.vantix.integration.cors.account.CorsForceActivationResult;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountForceActivationProcessorTest {
    private static final Long OPERATION_ID = 41L;
    private static final Long ACCOUNT_ID = 17L;
    private static final Long OPERATION_VERSION = 6L;
    private static final String REQUEST_ID = "force-activation-request-41";
    private static final String CORS_ACCOUNT_ID = "cors-17";

    @Mock
    private AccountForceActivationClaimService claimService;
    @Mock
    private AccountForceActivationStateService stateService;
    @Mock
    private AccountForceActivationFinalizeService finalizeService;
    @Mock
    private ServiceAccountMapper accountMapper;
    @Mock
    private CorsForceActivationGateway gateway;

    private AccountForceActivationProcessor processor;
    private CorsOperation operation;
    private ServiceAccount account;

    @BeforeEach
    void setUp() {
        processor = new AccountForceActivationProcessor(
                claimService, stateService, finalizeService, accountMapper, gateway);
        operation = operation();
        account = account();
        when(claimService.claim(OPERATION_ID))
                .thenReturn(new ClaimedAccountForceActivation(operation, false));
        when(accountMapper.selectById(ACCOUNT_ID)).thenReturn(account);
    }

    @Test
    void pendingOperationPostsWithoutQueryingFirst() {
        when(gateway.forceActivate(request())).thenReturn(outcome(CorsOutcome.UNKNOWN, "POST_UNKNOWN"));

        processor.process(OPERATION_ID);

        verify(gateway, never()).queryForceActivation(anyString());
        verify(gateway).forceActivate(request());
        verify(stateService).retryOrMarkManualReview(eq(operation), eq("POST_UNKNOWN"), anyString());
    }

    @Test
    void retryWaitOperationQueriesFirstAndNotFoundPostsWithOriginalRequestId() {
        when(claimService.claim(OPERATION_ID))
                .thenReturn(new ClaimedAccountForceActivation(operation, true));
        when(gateway.queryForceActivation(REQUEST_ID))
                .thenReturn(outcome(CorsOutcome.NOT_FOUND, "NOT_FOUND"));
        when(gateway.forceActivate(request())).thenReturn(outcome(CorsOutcome.UNKNOWN, "POST_UNKNOWN"));

        processor.process(OPERATION_ID);

        InOrder order = inOrder(gateway);
        order.verify(gateway).queryForceActivation(REQUEST_ID);
        order.verify(gateway).forceActivate(request());
        verify(stateService).retryOrMarkManualReview(eq(operation), eq("POST_UNKNOWN"), anyString());
    }

    @Test
    void querySuccessFinalizesWithoutPosting() {
        when(claimService.claim(OPERATION_ID))
                .thenReturn(new ClaimedAccountForceActivation(operation, true));
        CorsForceActivationResult success = successResult();
        when(gateway.queryForceActivation(REQUEST_ID)).thenReturn(success);

        processor.process(OPERATION_ID);

        verify(finalizeService).finalizeSuccess(OPERATION_ID, OPERATION_VERSION, success);
        verify(gateway, never()).forceActivate(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unknownQuerySchedulesRetryWithoutPosting() {
        when(claimService.claim(OPERATION_ID))
                .thenReturn(new ClaimedAccountForceActivation(operation, true));
        when(gateway.queryForceActivation(REQUEST_ID))
                .thenReturn(outcome(CorsOutcome.UNKNOWN, "QUERY_PENDING"));

        processor.process(OPERATION_ID);

        verify(stateService).retryOrMarkManualReview(eq(operation), eq("QUERY_PENDING"), anyString());
        verify(gateway, never()).forceActivate(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void queryIdempotencyConflictRequiresManualReview() {
        when(claimService.claim(OPERATION_ID))
                .thenReturn(new ClaimedAccountForceActivation(operation, true));
        when(gateway.queryForceActivation(REQUEST_ID))
                .thenReturn(outcome(CorsOutcome.IDEMPOTENCY_CONFLICT, "REMOTE_CONFLICT"));

        processor.process(OPERATION_ID);

        verify(stateService).markManualReview(eq(operation), eq("IDEMPOTENCY_CONFLICT"), anyString());
        verify(gateway, never()).forceActivate(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void queryDefinitiveRejectRequiresManualReview() {
        when(claimService.claim(OPERATION_ID))
                .thenReturn(new ClaimedAccountForceActivation(operation, true));
        when(gateway.queryForceActivation(REQUEST_ID))
                .thenReturn(outcome(CorsOutcome.DEFINITIVE_REJECT, "REMOTE_REJECT"));

        processor.process(OPERATION_ID);

        verify(stateService).markManualReview(eq(operation), eq("DEFINITIVE_REJECT"), anyString());
        verify(gateway, never()).forceActivate(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unknownPostResultSchedulesRetry() {
        when(gateway.forceActivate(request()))
                .thenReturn(outcome(CorsOutcome.UNKNOWN, "POST_PENDING"));

        processor.process(OPERATION_ID);

        verify(stateService).retryOrMarkManualReview(eq(operation), eq("POST_PENDING"), anyString());
    }

    @Test
    void postExceptionSchedulesRetryAsUnknown() {
        doThrow(new IllegalStateException("transport failed"))
                .when(gateway).forceActivate(request());

        processor.process(OPERATION_ID);

        verify(stateService).retryOrMarkManualReview(eq(operation), eq("POST_UNKNOWN"), anyString());
    }

    @Test
    void postIdempotencyConflictRequiresManualReview() {
        when(gateway.forceActivate(request()))
                .thenReturn(outcome(CorsOutcome.IDEMPOTENCY_CONFLICT, "REMOTE_CONFLICT"));

        processor.process(OPERATION_ID);

        verify(stateService).markManualReview(eq(operation), eq("IDEMPOTENCY_CONFLICT"), anyString());
    }

    @Test
    void postDefinitiveRejectRequiresManualReview() {
        when(gateway.forceActivate(request()))
                .thenReturn(outcome(CorsOutcome.DEFINITIVE_REJECT, "REMOTE_REJECT"));

        processor.process(OPERATION_ID);

        verify(stateService).markManualReview(eq(operation), eq("DEFINITIVE_REJECT"), anyString());
    }

    @Test
    void alreadyActiveLocalAccountSucceedsWithoutGatewayPost() {
        account.setCorsActivationStatus("ACTIVE");

        processor.process(OPERATION_ID);

        verify(stateService).markSucceeded(operation);
        verify(gateway, never()).forceActivate(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unknownLocalActivationStateRequiresManualReviewWithoutPosting() {
        account.setCorsActivationStatus("SUSPENDED");

        processor.process(OPERATION_ID);

        verify(stateService).markManualReview(eq(operation), eq("UNKNOWN_LOCAL_ACTIVATION_STATE"), anyString());
        verify(gateway, never()).forceActivate(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void finalizeFailureAfterPostSuccessRequiresManualReview() {
        CorsForceActivationResult success = successResult();
        when(gateway.forceActivate(request())).thenReturn(success);
        doThrow(new IllegalStateException("local finalize failed"))
                .when(finalizeService).finalizeSuccess(OPERATION_ID, OPERATION_VERSION, success);

        processor.process(OPERATION_ID);

        verify(stateService).markManualReview(eq(operation), eq("LOCAL_FINALIZE_FAILED"), anyString());
    }

    private static CorsOperation operation() {
        CorsOperation operation = new CorsOperation();
        operation.setId(OPERATION_ID);
        operation.setRequestId(REQUEST_ID);
        operation.setOperationType("FORCE_ACTIVATE_ACCOUNT");
        operation.setBizType("ACCOUNT_FORCE_ACTIVATION");
        operation.setBizId(ACCOUNT_ID);
        operation.setServiceAccountId(ACCOUNT_ID);
        operation.setStatus("CLAIMED");
        operation.setVersion(OPERATION_VERSION);
        return operation;
    }

    private static ServiceAccount account() {
        ServiceAccount account = new ServiceAccount();
        account.setId(ACCOUNT_ID);
        account.setCorsAccountId(CORS_ACCOUNT_ID);
        account.setAccount("account-17");
        account.setCorsActivationStatus("WAITING_ACTIVATION");
        return account;
    }

    private static CorsForceActivationRequest request() {
        return new CorsForceActivationRequest(REQUEST_ID, CORS_ACCOUNT_ID);
    }

    private static CorsForceActivationResult outcome(CorsOutcome outcome, String errorCode) {
        return CorsForceActivationResult.outcome(outcome, errorCode, "remote details");
    }

    private static CorsForceActivationResult successResult() {
        return CorsForceActivationResult.success(REQUEST_ID, null);
    }
}
