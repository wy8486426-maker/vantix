package com.sinognss.cloud.vantix.application.renewal;

import com.sinognss.cloud.vantix.application.cors.account.AccountStatusSyncScheduleService;
import com.sinognss.cloud.vantix.application.cors.account.CorsAccountStateApplyOutcome;
import com.sinognss.cloud.vantix.application.cors.account.CorsAccountStateApplyService;
import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.domain.renewal.AccountRenewal;
import com.sinognss.cloud.vantix.domain.renewal.AccountRenewalStatus;
import com.sinognss.cloud.vantix.domain.servicecode.ProcessingType;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCode;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeStatus;
import com.sinognss.cloud.vantix.integration.cors.CorsOutcome;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountRenewalResult;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountSnapshot;
import com.sinognss.cloud.vantix.infrastructure.mapper.AccountRenewalMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.CorsOperationMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeMapper;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public class AccountRenewalFinalizeService {
    private static final ZoneId CORS_ZONE = ZoneId.of("Asia/Shanghai");

    private final CorsOperationMapper operationMapper;
    private final AccountRenewalMapper renewalMapper;
    private final ServiceAccountMapper accountMapper;
    private final ServiceCodeMapper serviceCodeMapper;
    private final CorsAccountStateApplyService applyService;
    private final AccountStatusSyncScheduleService scheduleService;
    private final Clock clock;

    public AccountRenewalFinalizeService(CorsOperationMapper operationMapper,
                                         AccountRenewalMapper renewalMapper,
                                         ServiceAccountMapper accountMapper,
                                         ServiceCodeMapper serviceCodeMapper,
                                         CorsAccountStateApplyService applyService,
                                         AccountStatusSyncScheduleService scheduleService,
                                         Clock clock) {
        this.operationMapper = operationMapper;
        this.renewalMapper = renewalMapper;
        this.accountMapper = accountMapper;
        this.serviceCodeMapper = serviceCodeMapper;
        this.applyService = applyService;
        this.scheduleService = scheduleService;
        this.clock = clock;
    }

    @Transactional
    public void finalizeSuccess(Long operationId, Long claimedVersion, CorsAccountRenewalResult result) {
        CorsOperation operation = operationId == null ? null
                : operationMapper.selectByIdForUpdate(operationId);
        if (operation == null || claimedVersion == null || operation.getVersion() == null
                || !claimedVersion.equals(operation.getVersion())
                || !AccountRenewalConstants.CLAIMED.equals(operation.getStatus())
                || !isRenewalOperation(operation)) {
            throw new IllegalStateException("Claimed renewal operation changed before finalize");
        }

        AccountRenewal renewal = renewalMapper.selectByIdForUpdate(operation.getBizId());
        if (renewal == null || renewal.getVersion() == null
                || !AccountRenewalStatus.PROCESSING.equals(renewal.getStatus())
                || !hasRenewalLink(operation, renewal)) {
            throw new IllegalStateException("Account renewal changed before finalize");
        }

        ServiceAccount account = accountMapper.selectByIdForUpdate(renewal.getServiceAccountId());
        if (!hasAccountIdentity(operation, renewal, account)) {
            throw new IllegalStateException("Renewal account identity changed before finalize");
        }

        ServiceCode code = serviceCodeMapper.selectByIdForUpdate(renewal.getServiceCodeId());
        if (!isReservedForRenewal(code, renewal)) {
            throw new IllegalStateException("Renewal service code reservation changed before finalize");
        }
        validateSuccess(operation, renewal, account, result);

        CorsAccountStateApplyOutcome applyOutcome = applyService.apply(
                account, result.account(), scheduleService.successSchedule());
        if (applyOutcome == CorsAccountStateApplyOutcome.STALE_IGNORED) {
            verifyNewerLocalSnapshot(operation, renewal, account, result.account());
        } else if (applyOutcome != CorsAccountStateApplyOutcome.UPDATED
                && applyOutcome != CorsAccountStateApplyOutcome.IDEMPOTENT_NOOP) {
            throw new IllegalStateException("CORS account snapshot could not be applied during renewal finalize");
        }

        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        if (serviceCodeMapper.consumeRenewalCode(code.getId(), renewal.getRequestId(), code.getVersion(), now) != 1) {
            throw new IllegalStateException("Renewal service code could not be consumed");
        }
        if (renewalMapper.complete(renewal.getId(), renewal.getVersion(), now) != 1) {
            throw new IllegalStateException("Account renewal could not be completed");
        }
        if (operationMapper.markSucceeded(operation.getId(), operation.getVersion(), now) != 1) {
            throw new IllegalStateException("CORS renewal operation could not be marked succeeded");
        }
    }

    private static void validateSuccess(CorsOperation operation, AccountRenewal renewal,
                                        ServiceAccount account, CorsAccountRenewalResult result) {
        if (result == null || result.outcome() != CorsOutcome.SUCCESS
                || result.account() == null
                || !same(operation.getRequestId(), result.requestId())
                || !same(renewal.getRequestId(), result.requestId())) {
            throw new IllegalStateException("CORS renewal success correlation is invalid");
        }
        CorsAccountSnapshot snapshot = result.account();
        if (!same(account.getCorsAccountId(), snapshot.accountId())
                || !same(account.getAccount(), snapshot.account())
                || !"ACTIVE".equals(snapshot.activationStatus())
                || "DISABLED".equalsIgnoreCase(snapshot.accountStatus())
                || snapshot.activatedAt() == null
                || snapshot.expireAt() == null
                || snapshot.updatedAt() == null
                || snapshot.activatedAt().isAfter(snapshot.expireAt())) {
            throw new IllegalStateException("CORS renewal success snapshot is invalid");
        }
        if (renewal.getServiceAccountId() == null || renewal.getServiceCodeId() == null
                || renewal.getDurationValue() == null || renewal.getDurationValue() <= 0
                || renewal.getDurationUnit() == null || renewal.getDurationUnit().isBlank()
                || !same(renewal.getServiceType(), account.getServiceType())) {
            throw new IllegalStateException("Account renewal snapshot is invalid");
        }
    }

    private static boolean hasAccountIdentity(CorsOperation operation, AccountRenewal renewal,
                                              ServiceAccount account) {
        return account != null && account.getId() != null && account.getVersion() != null
                && account.getId().equals(renewal.getServiceAccountId())
                && account.getId().equals(operation.getServiceAccountId())
                && !blank(account.getCorsAccountId()) && !blank(account.getAccount());
    }

    private static boolean hasRenewalLink(CorsOperation operation, AccountRenewal renewal) {
        return isRenewalOperation(operation) && renewal.getId() != null
                && operation.getBizId().equals(renewal.getId())
                && operation.getServiceAccountId() != null
                && operation.getServiceAccountId().equals(renewal.getServiceAccountId())
                && operation.getRequestId() != null
                && operation.getRequestId().equals(renewal.getRequestId());
    }

    private static boolean isRenewalOperation(CorsOperation operation) {
        return operation != null
                && AccountRenewalConstants.OPERATION_TYPE.equals(operation.getOperationType())
                && AccountRenewalConstants.BIZ_TYPE.equals(operation.getBizType())
                && operation.getBizId() != null
                && operation.getServiceAccountId() != null;
    }

    private static boolean isReservedForRenewal(ServiceCode code, AccountRenewal renewal) {
        return code != null && code.getId() != null && code.getVersion() != null
                && renewal.getServiceCodeId() != null && renewal.getServiceCodeId().equals(code.getId())
                && code.getStatus() == ServiceCodeStatus.PROCESSING
                && code.getProcessingType() == ProcessingType.RENEWAL
                && renewal.getRequestId() != null
                && renewal.getRequestId().equals(code.getProcessingRequestId());
    }

    private void verifyNewerLocalSnapshot(CorsOperation operation, AccountRenewal renewal,
                                          ServiceAccount original, CorsAccountSnapshot remote) {
        ServiceAccount current = accountMapper.selectByIdForUpdate(renewal.getServiceAccountId());
        if (!hasAccountIdentity(operation, renewal, current)
                || !same(original.getCorsAccountId(), current.getCorsAccountId())
                || !same(original.getAccount(), current.getAccount())
                || current.getCorsUpdatedAt() == null) {
            throw new IllegalStateException("Newer local CORS account snapshot identity is invalid");
        }

        LocalDateTime remoteUpdatedAt = toCorsLocalDateTime(remote.updatedAt());
        if (current.getCorsUpdatedAt().isBefore(remoteUpdatedAt)) {
            throw new IllegalStateException("Local CORS account snapshot is older than renewal success response");
        }
        if (blank(current.getCorsStatus()) || blank(current.getCorsActivationStatus())
                || (current.getActivatedAt() != null && current.getExpireAt() != null
                && current.getActivatedAt().isAfter(current.getExpireAt()))
                || ("ACTIVE".equals(current.getCorsActivationStatus())
                && (current.getActivatedAt() == null || current.getExpireAt() == null
                || current.getActivatedAt().isAfter(current.getExpireAt())))) {
            throw new IllegalStateException("Newer local CORS account state is invalid");
        }
    }

    private static LocalDateTime toCorsLocalDateTime(OffsetDateTime value) {
        return value == null ? null : value.atZoneSameInstant(CORS_ZONE).toLocalDateTime();
    }

    private static boolean same(String left, String right) {
        return left != null && left.equals(right);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
