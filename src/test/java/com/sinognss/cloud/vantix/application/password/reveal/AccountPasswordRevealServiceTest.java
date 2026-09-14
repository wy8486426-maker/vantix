package com.sinognss.cloud.vantix.application.password.reveal;

import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.domain.password.AccountPasswordAction;
import com.sinognss.cloud.vantix.integration.cors.CorsOutcome;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountPasswordGateway;
import com.sinognss.cloud.vantix.integration.cors.account.CorsPasswordRevealRequest;
import com.sinognss.cloud.vantix.integration.cors.account.CorsPasswordRevealResult;
import com.sinognss.cloud.vantix.integration.cors.account.PasswordRevealSecret;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountPasswordRevealServiceTest {
    private final AccountPasswordRevealAuditService audit = mock(AccountPasswordRevealAuditService.class);
    private final CorsAccountPasswordGateway gateway = mock(CorsAccountPasswordGateway.class);
    private final AccountPasswordRevealService service = new AccountPasswordRevealService(audit, gateway);

    @Test
    void returnsSecretOnlyAfterAuditReservationGatewayAndSuccessFinalize() {
        AccountPasswordAction action = action();
        when(audit.reserve(41L, "RV-41")).thenReturn(action);
        when(gateway.revealPassword(new CorsPasswordRevealRequest("RV-41", "cors-41")))
                .thenReturn(new CorsPasswordRevealResult(CorsOutcome.SUCCESS, "RV-41", "cors-41",
                        new PasswordRevealSecret("p@ssword-value"), null));

        PasswordRevealResponse response = service.reveal(41L, "RV-41");

        assertEquals(41L, response.getServiceAccountId());
        assertEquals("account-41", response.getAccount());
        assertEquals("p@ssword-value", response.getPassword());
        assertFalse(response.toString().contains("p@ssword-value"));
        var order = inOrder(audit, gateway);
        order.verify(audit).reserve(41L, "RV-41");
        order.verify(gateway).revealPassword(new CorsPasswordRevealRequest("RV-41", "cors-41"));
        order.verify(audit).complete(51L, 0L);
        verify(audit, never()).fail(51L, 0L);
    }

    @Test
    void nonSuccessAndMalformedSuccessAreAuditedFailedAndNeverReturnSecret() {
        AccountPasswordAction action = action();
        when(audit.reserve(41L, "RV-41")).thenReturn(action);
        when(gateway.revealPassword(new CorsPasswordRevealRequest("RV-41", "cors-41")))
                .thenReturn(new CorsPasswordRevealResult(CorsOutcome.SUCCESS, "OTHER", "cors-41",
                        new PasswordRevealSecret("must-not-escape"), null));

        BusinessException failure = assertThrows(BusinessException.class,
                () -> service.reveal(41L, "RV-41"));

        assertFalse(failure.getMessage().contains("must-not-escape"));
        assertEquals("PASSWORD_REVEAL_FAILED", failure.getVantixErrorCode().value());
        verify(audit).fail(51L, 0L);
        verify(audit, never()).complete(51L, 0L);
    }

    @Test
    void duplicateRequestIdDoesNotCallCorsAgain() {
        when(audit.reserve(41L, "RV-41")).thenThrow(new DuplicateKeyException("duplicate"));

        BusinessException failure = assertThrows(BusinessException.class,
                () -> service.reveal(41L, "RV-41"));

        assertEquals("PASSWORD_REVEAL_REQUEST_ALREADY_USED", failure.getVantixErrorCode().value());
        verify(gateway, never()).revealPassword(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void gatewayExceptionIsDiscardedAndAuditIsFailed() {
        AccountPasswordAction action = action();
        when(audit.reserve(41L, "RV-41")).thenReturn(action);
        when(gateway.revealPassword(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("remote body contains secret-value"));

        BusinessException failure = assertThrows(BusinessException.class,
                () -> service.reveal(41L, "RV-41"));

        assertFalse(failure.getMessage().contains("secret-value"));
        assertEquals("PASSWORD_REVEAL_FAILED", failure.getVantixErrorCode().value());
        verify(audit).fail(51L, 0L);
    }

    @Test
    void auditFinalizeFailureSuppressesSuccessfulRemoteSecret() {
        AccountPasswordAction action = action();
        when(audit.reserve(41L, "RV-41")).thenReturn(action);
        when(gateway.revealPassword(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new CorsPasswordRevealResult(CorsOutcome.SUCCESS, "RV-41", "cors-41",
                        new PasswordRevealSecret("p@ssword-value"), null));
        org.mockito.Mockito.doThrow(new IllegalStateException("database unavailable"))
                .when(audit).complete(51L, 0L);

        assertThrows(IllegalStateException.class, () -> service.reveal(41L, "RV-41"));
        verify(audit, never()).fail(51L, 0L);
    }

    @Test
    void secretToStringIsAlwaysRedacted() {
        PasswordRevealSecret secret = new PasswordRevealSecret("p@ssword-value");
        assertEquals("PasswordRevealSecret[REDACTED]", secret.toString());
        assertFalse(secret.toString().contains(secret.getPassword()));
    }

    private static AccountPasswordAction action() {
        AccountPasswordAction action = new AccountPasswordAction();
        action.setId(51L);
        action.setRequestId("RV-41");
        action.setCorsAccountId("cors-41");
        action.setAccount("account-41");
        action.setServiceAccountId(41L);
        action.setVersion(0L);
        return action;
    }
}
