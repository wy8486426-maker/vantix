package com.sinognss.cloud.vantix.integration.cors.account;

import com.sinognss.cloud.vantix.domain.config.DurationUnit;
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
                "RN-20260914-000001", "cors-1", 3, DurationUnit.MONTH);

        assertEquals("RN-20260914-000001", request.requestId());
        assertEquals("cors-1", request.accountId());
        assertEquals(3, request.durationValue());
        assertEquals(DurationUnit.MONTH, request.durationUnit());
    }

    @Test
    void requestRejectsInvalidIdAccountDurationAndUnit() {
        assertThrows(IllegalArgumentException.class,
                () -> new CorsAccountRenewalRequest(" ", "cors-1", 1, DurationUnit.DAY));
        assertThrows(IllegalArgumentException.class,
                () -> new CorsAccountRenewalRequest("x".repeat(129), "cors-1", 1, DurationUnit.DAY));
        assertThrows(IllegalArgumentException.class,
                () -> new CorsAccountRenewalRequest("RN\n1", "cors-1", 1, DurationUnit.DAY));
        assertThrows(IllegalArgumentException.class,
                () -> new CorsAccountRenewalRequest("RN-1", " ", 1, DurationUnit.DAY));
        assertThrows(IllegalArgumentException.class,
                () -> new CorsAccountRenewalRequest("RN-1", "cors-1", 0, DurationUnit.DAY));
        assertThrows(NullPointerException.class,
                () -> new CorsAccountRenewalRequest("RN-1", "cors-1", 1, null));
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
