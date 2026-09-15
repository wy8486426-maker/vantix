package com.sinognss.cloud.vantix.integration.cors.account;

import com.sinognss.cloud.vantix.integration.cors.CorsOutcome;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CorsAccountRenewalContractTest {
    private static final CorsAccountSnapshot ACCOUNT = new CorsAccountSnapshot(
            "cors-1", "account-1", "ACTIVE", "ACTIVE",
            OffsetDateTime.of(2026, 9, 14, 10, 0, 0, 0, ZoneOffset.ofHours(8)),
            OffsetDateTime.of(2027, 1, 1, 0, 0, 0, 0, ZoneOffset.ofHours(8)),
            OffsetDateTime.of(2026, 9, 1, 0, 0, 0, 0, ZoneOffset.ofHours(8)),
            OffsetDateTime.of(2026, 9, 14, 10, 0, 0, 0, ZoneOffset.ofHours(8)));

    @Test
    void requestCarriesStableRequestIdCorsAccountIdentityAndCodeDuration() {
        CorsAccountRenewalRequest request = new CorsAccountRenewalRequest(
                "RN-20260914-000001", "cors-1", 90);

        assertEquals("RN-20260914-000001", request.requestId());
        assertEquals("cors-1", request.accountId());
        assertEquals(90, request.durationDays());
    }

    @Test
    void requestRejectsInvalidIdAccountAndDuration() {
        assertThrows(IllegalArgumentException.class,
                () -> new CorsAccountRenewalRequest(" ", "cors-1", 1));
        assertThrows(IllegalArgumentException.class,
                () -> new CorsAccountRenewalRequest("x".repeat(129), "cors-1", 1));
        assertThrows(IllegalArgumentException.class,
                () -> new CorsAccountRenewalRequest("RN\n1", "cors-1", 1));
        assertThrows(IllegalArgumentException.class,
                () -> new CorsAccountRenewalRequest("RN-1", " ", 1));
        assertThrows(IllegalArgumentException.class,
                () -> new CorsAccountRenewalRequest("RN-1", "cors-1", 0));
    }

    @Test
    void resultKeepsAllProtocolOutcomesDistinctAndDefinitiveRejectIsExplicit() {
        assertEquals(CorsOutcome.SUCCESS,
                CorsAccountRenewalResult.success("RN-1", ACCOUNT).outcome());
        assertEquals(CorsOutcome.NOT_FOUND,
                CorsAccountRenewalResult.notFound("RN-1", "NOT_FOUND", "missing").outcome());
        assertEquals(CorsOutcome.DEFINITIVE_REJECT,
                CorsAccountRenewalResult.definitiveReject("RN-1", "INACTIVE", "not active").outcome());
        assertEquals(CorsOutcome.UNKNOWN,
                CorsAccountRenewalResult.unknown("RN-1", "TIMEOUT", "timed out").outcome());
        assertEquals(CorsOutcome.IDEMPOTENCY_CONFLICT,
                CorsAccountRenewalResult.idempotencyConflict("RN-1", "CONFLICT", "key conflict").outcome());
    }

    @Test
    void successRequiresRequestIdAndAccountAndNonSuccessCannotCarryAccount() {
        assertThrows(IllegalArgumentException.class,
                () -> CorsAccountRenewalResult.success(" ", ACCOUNT));
        assertThrows(NullPointerException.class,
                () -> CorsAccountRenewalResult.success("RN-1", null));
        assertThrows(IllegalArgumentException.class,
                () -> new CorsAccountRenewalResult(CorsOutcome.UNKNOWN, "RN-1", ACCOUNT, null, null));
    }
}
