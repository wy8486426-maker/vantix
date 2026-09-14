package com.sinognss.cloud.vantix.application.cors.account;

import com.sinognss.cloud.vantix.config.CorsForceActivationProperties;
import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountForceActivationJobTest {
    private static final long ACCOUNT_ID = 21L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 14, 12, 0);

    @Mock
    private ServiceAccountMapper accountMapper;
    @Mock
    private AccountStatusReconcileService reconcileService;
    @Mock
    private AccountForceActivationReserveService reserveService;

    private AccountForceActivationJob job;
    private ServiceAccount account;

    @BeforeEach
    void setUp() {
        CorsForceActivationProperties properties = new CorsForceActivationProperties();
        Clock clock = Clock.fixed(Instant.parse("2026-09-14T12:00:00Z"), ZoneOffset.UTC);
        job = new AccountForceActivationJob(
                accountMapper, reconcileService, reserveService, properties, clock);
        account = eligibleAccount();
        when(accountMapper.selectDueForceActivationCandidateIds(NOW, 100))
                .thenReturn(List.of(ACCOUNT_ID));
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatusReconcileOutcome.class, names = {"UPDATED", "IDEMPOTENT_NOOP"})
    void successfulPreflightRefetchesAndReservesOnlyStillEligibleAccount(
            AccountStatusReconcileOutcome preflight) {
        when(reconcileService.reconcileOne(ACCOUNT_ID)).thenReturn(preflight);
        when(accountMapper.selectById(ACCOUNT_ID)).thenReturn(account);
        when(reserveService.reserve(ACCOUNT_ID)).thenReturn(AccountForceActivationReserveOutcome.CREATED);

        job.scan();

        InOrder order = inOrder(accountMapper, reconcileService, reserveService);
        order.verify(accountMapper).selectDueForceActivationCandidateIds(NOW, 100);
        order.verify(reconcileService).reconcileOne(ACCOUNT_ID);
        order.verify(accountMapper).selectById(ACCOUNT_ID);
        order.verify(reserveService).reserve(ACCOUNT_ID);
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatusReconcileOutcome.class, names = {
            "STALE_IGNORED", "INCONSISTENT", "CONCURRENT_MODIFICATION",
            "NOT_FOUND", "UNKNOWN", "SKIPPED"
    })
    void unsuccessfulOrStalePreflightNeverCreatesOperation(AccountStatusReconcileOutcome preflight) {
        when(reconcileService.reconcileOne(ACCOUNT_ID)).thenReturn(preflight);

        job.scan();

        verify(accountMapper, never()).selectById(ACCOUNT_ID);
        verify(reserveService, never()).reserve(ACCOUNT_ID);
    }

    @Test
    void accountThatChangedAfterPreflightIsNotReserved() {
        when(reconcileService.reconcileOne(ACCOUNT_ID))
                .thenReturn(AccountStatusReconcileOutcome.UPDATED);
        account.setCorsActivationStatus("ACTIVE");
        when(accountMapper.selectById(ACCOUNT_ID)).thenReturn(account);

        job.scan();

        verify(accountMapper).selectById(ACCOUNT_ID);
        verify(reserveService, never()).reserve(ACCOUNT_ID);
    }

    @Test
    void accountWithFutureForceActivationTimeIsNotReservedAfterPreflight() {
        when(reconcileService.reconcileOne(ACCOUNT_ID))
                .thenReturn(AccountStatusReconcileOutcome.IDEMPOTENT_NOOP);
        account.setForceActivateAt(NOW.plusSeconds(1));
        when(accountMapper.selectById(ACCOUNT_ID)).thenReturn(account);

        job.scan();

        verify(accountMapper).selectById(ACCOUNT_ID);
        verify(reserveService, never()).reserve(ACCOUNT_ID);
    }

    private static ServiceAccount eligibleAccount() {
        ServiceAccount account = new ServiceAccount();
        account.setId(ACCOUNT_ID);
        account.setVersion(0L);
        account.setCorsAccountId("cors-account-21");
        account.setCorsActivationStatus("WAITING_ACTIVATION");
        account.setForceActivateAt(NOW.minusSeconds(1));
        return account;
    }
}
