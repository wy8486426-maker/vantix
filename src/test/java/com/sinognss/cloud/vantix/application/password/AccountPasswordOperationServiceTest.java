package com.sinognss.cloud.vantix.application.password;

import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.domain.password.AccountPasswordAction;
import com.sinognss.cloud.vantix.domain.password.AccountPasswordActionConstants;
import com.sinognss.cloud.vantix.integration.cors.CorsOutcome;
import com.sinognss.cloud.vantix.integration.cors.account.CorsPasswordGateway;
import com.sinognss.cloud.vantix.integration.cors.account.CorsCustomPasswordRequest;
import com.sinognss.cloud.vantix.integration.cors.account.CorsPasswordResult;
import com.sinognss.cloud.vantix.integration.cors.account.CorsResetPasswordRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountPasswordOperationServiceTest {
    private static final long SERVICE_ACCOUNT_ID = 41L;
    private static final long CORS_ACCOUNT_ID = 10001L;
    private static final String PASSWORD = "NewPassword123";

    @Mock private AccountPasswordAuditService auditService;
    @Mock private CorsPasswordGateway gateway;

    private AccountPasswordOperationService service;
    private AccountPasswordAction action;

    @BeforeEach
    void setUp() {
        service = new AccountPasswordOperationService(auditService, gateway);
        action = action();
        lenient().when(auditService.reserve(AccountPasswordActionConstants.RESET, SERVICE_ACCOUNT_ID)).thenReturn(action);
        lenient().when(auditService.reserve(AccountPasswordActionConstants.CUSTOM, SERVICE_ACCOUNT_ID)).thenReturn(action);
    }

    @Test
    void resetPostsOnlyTheCorsAccountIdAndReturnsNoPasswordData() {
        when(gateway.resetPassword(new CorsResetPasswordRequest(CORS_ACCOUNT_ID)))
                .thenReturn(CorsPasswordResult.success());

        service.reset(SERVICE_ACCOUNT_ID);

        verify(gateway).resetPassword(new CorsResetPasswordRequest(CORS_ACCOUNT_ID));
        verify(auditService).complete(action);
        verify(auditService, never()).manualReview(eq(action), eq("CORS_PASSWORD_RESULT_UNKNOWN"),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void customPassUsesCorsAccountIdAndKeepsPasswordOutOfAuditMetadata() {
        when(gateway.customPassword(new CorsCustomPasswordRequest(CORS_ACCOUNT_ID, PASSWORD)))
                .thenReturn(CorsPasswordResult.success());

        service.custom(SERVICE_ACCOUNT_ID, PASSWORD);

        ArgumentCaptor<CorsCustomPasswordRequest> request = ArgumentCaptor.forClass(CorsCustomPasswordRequest.class);
        verify(gateway).customPassword(request.capture());
        assertEquals(CORS_ACCOUNT_ID, request.getValue().id());
        assertEquals(PASSWORD, request.getValue().password());
        verify(auditService).complete(action);
        assertFalse(Arrays.stream(AccountPasswordAction.class.getDeclaredFields())
                .anyMatch(field -> field.getName().toLowerCase().contains("password")));
        assertFalse(request.getValue().toString().contains(PASSWORD));
    }

    @Test
    void nonzeroCorsCodeIsABusinessFailureWithoutPersistingRemoteMessage() {
        when(gateway.customPassword(new CorsCustomPasswordRequest(CORS_ACCOUNT_ID, PASSWORD)))
                .thenReturn(CorsPasswordResult.businessFailure("5401", "remote rejected"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.custom(SERVICE_ACCOUNT_ID, PASSWORD));

        assertEquals(ErrorCode.PASSWORD_CUSTOM_FAILED, error.getVantixErrorCode());
        verify(auditService).fail(action, "5401", "CORS 自定义密码操作失败");
        verify(auditService, never()).manualReview(eq(action), eq("CORS_PASSWORD_RESULT_UNKNOWN"),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void timeoutIsUnknownAndIsNotAutomaticallyReplayed() {
        when(gateway.customPassword(new CorsCustomPasswordRequest(CORS_ACCOUNT_ID, PASSWORD)))
                .thenThrow(new IllegalStateException("timeout"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.custom(SERVICE_ACCOUNT_ID, PASSWORD));

        assertEquals(ErrorCode.CORS_PASSWORD_RESULT_UNKNOWN, error.getVantixErrorCode());
        verify(gateway).customPassword(new CorsCustomPasswordRequest(CORS_ACCOUNT_ID, PASSWORD));
        verify(auditService).manualReview(action, "CORS_PASSWORD_RESULT_UNKNOWN",
                "CORS 密码操作结果未知，请人工确认");
    }

    private static AccountPasswordAction action() {
        AccountPasswordAction action = new AccountPasswordAction();
        action.setId(501L);
        action.setActionType(AccountPasswordActionConstants.CUSTOM);
        action.setServiceAccountId(SERVICE_ACCOUNT_ID);
        action.setCorsAccountId(String.valueOf(CORS_ACCOUNT_ID));
        action.setStatus(AccountPasswordActionConstants.PROCESSING);
        action.setVersion(0L);
        return action;
    }
}
