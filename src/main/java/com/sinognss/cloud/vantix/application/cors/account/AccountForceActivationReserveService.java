package com.sinognss.cloud.vantix.application.cors.account;

import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.infrastructure.mapper.CorsOperationMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public class AccountForceActivationReserveService {
    private final ServiceAccountMapper accountMapper;
    private final CorsOperationMapper operationMapper;
    private final Clock clock;

    public AccountForceActivationReserveService(ServiceAccountMapper accountMapper,
                                                CorsOperationMapper operationMapper,
                                                Clock clock) {
        this.accountMapper = accountMapper;
        this.operationMapper = operationMapper;
        this.clock = clock;
    }

    @Transactional
    public AccountForceActivationReserveOutcome reserve(Long serviceAccountId) {
        if (serviceAccountId == null) {
            return AccountForceActivationReserveOutcome.NOT_ELIGIBLE;
        }
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
        ServiceAccount account = accountMapper.selectByIdForUpdate(serviceAccountId);
        if (!AccountForceActivationConstants.isEligibleAccount(account, now)) {
            return AccountForceActivationReserveOutcome.NOT_ELIGIBLE;
        }

        CorsOperation existing = operationMapper.selectByBusinessForUpdate(
                AccountForceActivationConstants.BIZ_TYPE, serviceAccountId);
        if (existing != null) {
            return AccountForceActivationConstants.isOperationForAccount(existing, serviceAccountId)
                    ? AccountForceActivationReserveOutcome.EXISTING
                    : AccountForceActivationReserveOutcome.CONFLICT;
        }

        CorsOperation operation = new CorsOperation();
        operation.setRequestId("FA-" + UUID.randomUUID());
        operation.setOperationType(AccountForceActivationConstants.OPERATION_TYPE);
        operation.setBizType(AccountForceActivationConstants.BIZ_TYPE);
        operation.setBizId(serviceAccountId);
        operation.setServiceAccountId(serviceAccountId);
        operation.setStatus(AccountForceActivationConstants.PENDING);
        operation.setRetryCount(0);
        operation.setVersion(0L);
        operation.setCreatedAt(now);
        operation.setUpdatedAt(now);
        try {
            if (operationMapper.insert(operation) != 1) {
                throw new IllegalStateException("Force-activation operation insert failed");
            }
            return AccountForceActivationReserveOutcome.CREATED;
        } catch (DuplicateKeyException duplicate) {
            CorsOperation raced = operationMapper.selectByBusinessForUpdate(
                    AccountForceActivationConstants.BIZ_TYPE, serviceAccountId);
            if (raced == null) {
                throw duplicate;
            }
            return AccountForceActivationConstants.isOperationForAccount(raced, serviceAccountId)
                    ? AccountForceActivationReserveOutcome.EXISTING
                    : AccountForceActivationReserveOutcome.CONFLICT;
        }
    }
}
