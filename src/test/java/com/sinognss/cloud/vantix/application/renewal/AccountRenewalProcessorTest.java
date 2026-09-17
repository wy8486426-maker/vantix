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
import com.sinognss.cloud.vantix.integration.cors.account.CorsRenewalData;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountRenewalProcessorTest {
    private static final long OPERATION_ID = 31L;
    private static final long RENEWAL_ID = 51L;
    private static final long ACCOUNT_ID = 61L;
    private static final long CORS_ACCOUNT_ID = 101L;
    private static final long CODE_ID = 71L;
    private static final long OPERATION_VERSION = 6L;
    private static final String REQUEST_ID = "RN-test-31";
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
        when(claimService.claim(OPERATION_ID)).thenReturn(new ClaimedAccountRenewal(operation, renewal));
        lenient().when(claimService.initializeFirstAttempt(operation)).thenReturn(true);
        when(accountMapper.selectById(ACCOUNT_ID)).thenReturn(account);
    }

    @Test
    void preflightsThenPostsBatchRequestUsingCorsIdAndFrozenDuration() {
        when(statusGateway.getAccount(String.valueOf(CORS_ACCOUNT_ID))).thenReturn(statusSuccess(snapshot()));
        when(renewalGateway.renew(request())).thenReturn(unknown("POST_UNKNOWN"));

        processor.process(OPERATION_ID);

        InOrder order = inOrder(statusGateway, claimService, renewalGateway);
        order.verify(claimService).claim(OPERATION_ID);
        order.verify(statusGateway).getAccount(String.valueOf(CORS_ACCOUNT_ID));
        order.verify(claimService).initializeFirstAttempt(operation);
        ArgumentCaptor<CorsAccountRenewalRequest> captor = ArgumentCaptor.forClass(CorsAccountRenewalRequest.class);
        order.verify(renewalGateway).renew(captor.capture());
        assertEquals(List.of(CORS_ACCOUNT_ID), captor.getValue().ids());
        assertEquals(90, captor.getValue().dayType());
        assertEquals(REQUEST_ID, captor.getValue().requestId());
        verify(stateService).retryOrMarkManualReview(eq(operation), eq(renewal),
                eq("POST_UNKNOWN"), anyString());
    }

    @Test
    void codeZeroWithNullRedisDataStaysRetryableAndDoesNotFinalize() {
        when(statusGateway.getAccount(String.valueOf(CORS_ACCOUNT_ID))).thenReturn(statusSuccess(snapshot()));
        when(renewalGateway.renew(request())).thenReturn(
                CorsAccountRenewalResult.successWithData(REQUEST_ID, null));

        processor.process(OPERATION_ID);

        verify(stateService).retryOrMarkManualReview(operation, renewal, "CORS_RESULT_PENDING",
                "CORS 续期请求已接受，但 Redis 结果暂不可用");
        verify(finalizeService, never()).finalizeSuccess(any(), any(), any());
    }

    @Test
    void preflightUnknownDoesNotStartTheRenewalResultWindowOrPost() {
        when(statusGateway.getAccount(String.valueOf(CORS_ACCOUNT_ID)))
                .thenReturn(CorsAccountStatusResult.unknown("PREFLIGHT_UNKNOWN", "unavailable"));

        processor.process(OPERATION_ID);

        verify(claimService, never()).initializeFirstAttempt(operation);
        verify(renewalGateway, never()).renew(any());
    }

    @Test
    void retryResendsTheSameRequestIdAndFinalizesOnlyAfterCompleteRedisData() {
        when(statusGateway.getAccount(String.valueOf(CORS_ACCOUNT_ID))).thenReturn(statusSuccess(snapshot()));
        when(renewalGateway.renew(request())).thenReturn(
                CorsAccountRenewalResult.successWithData(REQUEST_ID, null),
                CorsAccountRenewalResult.successWithData(REQUEST_ID,
                        new CorsRenewalData("corsRenewal", List.of(ACCOUNT))));

        processor.process(OPERATION_ID);
        processor.process(OPERATION_ID);

        verify(renewalGateway, org.mockito.Mockito.times(2)).renew(request());
        verify(finalizeService).finalizeSuccess(eq(OPERATION_ID), eq(OPERATION_VERSION),
                eq(CorsAccountRenewalResult.successWithData(REQUEST_ID,
                        new CorsRenewalData("corsRenewal", List.of(ACCOUNT))).withAccount(snapshot())));
    }

    @Test
    void successfulRedisDataRequiresCorsRenewalInterfaceAndValidNameSet() {
        when(statusGateway.getAccount(String.valueOf(CORS_ACCOUNT_ID))).thenReturn(statusSuccess(snapshot()));
        when(renewalGateway.renew(request())).thenReturn(CorsAccountRenewalResult.successWithData(
                REQUEST_ID, new CorsRenewalData("otherInterface", List.of(ACCOUNT))));

        processor.process(OPERATION_ID);

        verify(stateService).markManualReview(operation, renewal, "CORS_RESULT_INVALID",
                "CORS 续期返回的账号集合无效");
        verify(finalizeService, never()).finalizeSuccess(any(), any(), any());
    }

    @Test
    void preflightNotFoundDoesNotPost() {
        when(statusGateway.getAccount(String.valueOf(CORS_ACCOUNT_ID)))
                .thenReturn(CorsAccountStatusResult.notFound("CORS_ACCOUNT_NOT_FOUND", "missing"));

        processor.process(OPERATION_ID);

        verify(stateService).definitiveFail(operation, renewal, "CORS_ACCOUNT_NOT_FOUND",
                "CORS account was not found before renewal");
        verify(renewalGateway, never()).renew(any());
    }

    @Test
    void invalidCorsAccountIdIsRejectedBeforeAnyCorsCall() {
        account.setCorsAccountId("not-a-long");

        processor.process(OPERATION_ID);

        verify(stateService).markManualReview(operation, renewal, "CORS_ACCOUNT_ID_INVALID",
                "服务账号缺少可解析的 CORS 账号标识");
        verify(statusGateway, never()).getAccount(anyString());
        verify(renewalGateway, never()).renew(any());
    }

    @Test
    void timeoutIsUnknownAndKeepsTheOriginalRequestIdForRetry() {
        when(statusGateway.getAccount(String.valueOf(CORS_ACCOUNT_ID))).thenReturn(statusSuccess(snapshot()));
        doThrow(new IllegalStateException("connection reset")).when(renewalGateway).renew(request());

        processor.process(OPERATION_ID);

        verify(stateService).retryOrMarkManualReview(operation, renewal, "POST_UNKNOWN",
                "CORS renewal request result is unknown");
        verify(renewalGateway).renew(new CorsAccountRenewalRequest(
                List.of(CORS_ACCOUNT_ID), renewal.getDurationDays(), REQUEST_ID));
    }

    @Test
    void everyTrustedNonzeroRenewalCodeFailsWithoutRetry() {
        assertDefinitiveFailure("5314");
        assertDefinitiveFailure("5345");
        assertDefinitiveFailure("5316");
        assertDefinitiveFailure("5999");
    }

    @Test
    void expiredPreflightCanStillBeRenewed() {
        when(statusGateway.getAccount(String.valueOf(CORS_ACCOUNT_ID))).thenReturn(
                statusSuccess(snapshot("EXPIRED", "ENABLED")));
        when(renewalGateway.renew(request())).thenReturn(unknown("POST_UNKNOWN"));

        processor.process(OPERATION_ID);

        verify(renewalGateway).renew(request());
    }

    private void assertDefinitiveFailure(String corsCode) {
        org.mockito.Mockito.reset(statusGateway, renewalGateway, stateService);
        when(statusGateway.getAccount(String.valueOf(CORS_ACCOUNT_ID))).thenReturn(statusSuccess(snapshot()));
        String message = "failure-" + corsCode;
        when(renewalGateway.renew(request())).thenReturn(
                CorsAccountRenewalResult.definitiveReject(REQUEST_ID, corsCode, message));

        processor.process(OPERATION_ID);

        verify(stateService).definitiveFail(operation, renewal, corsCode, message);
        verify(stateService, never()).retryOrMarkManualReview(any(), any(), anyString(), anyString());
    }

    private static CorsOperation operation() {
        CorsOperation operation = new CorsOperation();
        operation.setId(OPERATION_ID);
        operation.setRequestId(REQUEST_ID);
        operation.setOperationType(AccountRenewalConstants.OPERATION_TYPE);
        operation.setBizType(AccountRenewalConstants.BIZ_TYPE);
        operation.setBizId(RENEWAL_ID);
        operation.setServiceAccountId(ACCOUNT_ID);
        operation.setStatus(AccountRenewalConstants.CLAIMED);
        operation.setVersion(OPERATION_VERSION);
        return operation;
    }

    private static AccountRenewal renewal() {
        AccountRenewal renewal = new AccountRenewal();
        renewal.setId(RENEWAL_ID);
        renewal.setServiceAccountId(ACCOUNT_ID);
        renewal.setServiceCodeId(CODE_ID);
        renewal.setRequestId(REQUEST_ID);
        renewal.setStatus(AccountRenewalConstants.PROCESSING);
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
        account.setCorsAccountId(String.valueOf(CORS_ACCOUNT_ID));
        account.setAccount(ACCOUNT);
        account.setServiceType("STANDARD");
        account.setCorsActivationStatus("ACTIVE");
        account.setVersion(2L);
        return account;
    }

    private static CorsAccountRenewalRequest request() {
        return new CorsAccountRenewalRequest(List.of(CORS_ACCOUNT_ID), 90, REQUEST_ID);
    }

    private static CorsAccountStatusResult statusSuccess(CorsAccountSnapshot snapshot) {
        return CorsAccountStatusResult.success(snapshot);
    }

    private static CorsAccountSnapshot snapshot() {
        return snapshot("ACTIVE", "ENABLED");
    }

    private static CorsAccountSnapshot snapshot(String activationStatus, String accountStatus) {
        return new CorsAccountSnapshot(String.valueOf(CORS_ACCOUNT_ID), ACCOUNT, accountStatus, activationStatus,
                OffsetDateTime.parse("2026-09-01T00:00:00+08:00"),
                OffsetDateTime.parse("2027-09-01T00:00:00+08:00"),
                OffsetDateTime.parse("2026-08-01T00:00:00+08:00"),
                OffsetDateTime.parse("2026-09-14T12:00:00+08:00"));
    }

    private static CorsAccountRenewalResult unknown(String errorCode) {
        return CorsAccountRenewalResult.unknown(REQUEST_ID, errorCode, "remote result");
    }
}
