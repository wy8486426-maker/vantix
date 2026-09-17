package com.sinognss.cloud.vantix.integration.cors.account;

import com.sinognss.cloud.vantix.integration.cors.CorsOutcome;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CorsAccountRenewalContractTest {
    @Test
    void requestCarriesBatchIdsStableRequestIdAndFrozenDurationAsDayType() {
        CorsAccountRenewalRequest request = new CorsAccountRenewalRequest(
                List.of(101L, 102L), 365, "RN-20260914-000001");

        assertEquals("RN-20260914-000001", request.requestId());
        assertEquals(List.of(101L, 102L), request.ids());
        assertEquals(365, request.dayType());
    }

    @Test
    void requestRejectsInvalidIdAccountAndDuration() {
        assertThrows(IllegalArgumentException.class,
                () -> new CorsAccountRenewalRequest(List.of(101L), 1, " "));
        assertThrows(IllegalArgumentException.class,
                () -> new CorsAccountRenewalRequest(List.of(101L), 1, "x".repeat(129)));
        assertThrows(IllegalArgumentException.class,
                () -> new CorsAccountRenewalRequest(List.of(101L), 1, "RN\n1"));
        assertThrows(IllegalArgumentException.class,
                () -> new CorsAccountRenewalRequest(List.of(), 1, "RN-1"));
        assertThrows(IllegalArgumentException.class,
                () -> new CorsAccountRenewalRequest(List.of(101L, 101L), 1, "RN-1"));
        assertThrows(IllegalArgumentException.class,
                () -> new CorsAccountRenewalRequest(List.of(101L), 0, "RN-1"));
    }

    @Test
    void resultModelsRedisPendingAndDefinitiveFailureSeparately() {
        assertEquals(CorsOutcome.SUCCESS,
                CorsAccountRenewalResult.successWithData("RN-1", null).outcome());
        assertEquals(null, CorsAccountRenewalResult.successWithData("RN-1", null).data());
        assertEquals(CorsOutcome.NOT_FOUND,
                CorsAccountRenewalResult.notFound("RN-1", "NOT_FOUND", "missing").outcome());
        assertEquals(CorsOutcome.DEFINITIVE_REJECT,
                CorsAccountRenewalResult.definitiveReject("RN-1", "5314", "not active").outcome());
        assertEquals(CorsOutcome.UNKNOWN,
                CorsAccountRenewalResult.unknown("RN-1", "TIMEOUT", "timed out").outcome());
    }

    @Test
    void resultRequiresCorrelationAndNeverCarriesDataForFailures() {
        assertThrows(IllegalArgumentException.class,
                () -> CorsAccountRenewalResult.successWithData(" ", null));
        assertThrows(IllegalArgumentException.class,
                () -> new CorsAccountRenewalResult(CorsOutcome.UNKNOWN, "RN-1", null,
                        new CorsRenewalData("corsRenewal", List.of("account-1")), "5314", "invalid"));
    }
}
