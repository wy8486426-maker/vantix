package com.sinognss.cloud.vantix.application.renewal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.OperatorIdentity;
import com.sinognss.cloud.vantix.common.user.UserScope;
import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.domain.account.AccountSource;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.domain.renewal.AccountRenewal;
import com.sinognss.cloud.vantix.domain.renewal.AccountRenewalCodeSnapshot;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCode;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeStatus;
import com.sinognss.cloud.vantix.infrastructure.mapper.AccountRenewalMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.CorsOperationMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeMapper;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

public class AccountRenewalReserveTransaction {
    private final AccountRenewalMapper renewalMapper;
    private final CorsOperationMapper operationMapper;
    private final ServiceAccountMapper accountMapper;
    private final ServiceCodeMapper serviceCodeMapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AccountRenewalReserveTransaction(AccountRenewalMapper renewalMapper,
                                            CorsOperationMapper operationMapper,
                                            ServiceAccountMapper accountMapper,
                                            ServiceCodeMapper serviceCodeMapper,
                                            ObjectMapper objectMapper,
                                            Clock clock) {
        this.renewalMapper = renewalMapper;
        this.operationMapper = operationMapper;
        this.accountMapper = accountMapper;
        this.serviceCodeMapper = serviceCodeMapper;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AccountRenewalReservation reserve(CreateAccountRenewalCommand command, UserScope scope,
                                              OperatorIdentity operator) {
        AccountRenewal existing = renewalMapper.selectByRequestId(command.requestId());
        if (existing != null) return idempotent(command, scope, existing);

        LocalDateTime now = LocalDateTime.now(clock);
        ServiceAccount account = accountMapper.selectByIdForUpdate(command.serviceAccountId());
        if (account == null) throw new BusinessException(ErrorCode.NOT_FOUND, "服务账号不存在");
        AccountRenewalReserveService.assertAccess(scope, account.getOwnerCompanyId(), account.getAssignedUserId());
        assertRenewableSource(account);
        if (!hasIdentity(account)) {
            throw new BusinessException(ErrorCode.ACCOUNT_RENEWAL_STATE_INCONSISTENT,
                    "服务账号缺少 CORS 账号标识");
        }
        if (!"ACTIVE".equals(account.getCorsActivationStatus())
                && !"EXPIRED".equals(account.getCorsActivationStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_RENEWAL_STATE_INCONSISTENT,
                    "服务账号当前未激活，不能发起续期");
        }

        /* A locking read is a current read under MySQL REPEATABLE_READ, so it sees a
           concurrent requestId insert after this transaction waited for the account lock. */
        existing = renewalMapper.selectByRequestIdForUpdate(command.requestId());
        if (existing != null) return idempotent(command, scope, existing);
        /* The account row serializes competing reserves. Keep this a non-locking
           read because finalize locks renewal before account; locking here would
           invert the order and can deadlock with a completing renewal. */
        if (renewalMapper.selectActiveByAccount(account.getId()) != null) {
            throw new BusinessException(ErrorCode.ACCOUNT_RENEWAL_IN_PROGRESS, "该账号已有未决续期");
        }

        ServiceCode code = serviceCodeMapper.selectByIdForUpdate(command.serviceCodeId());
        if (code == null) throw new BusinessException(ErrorCode.NOT_FOUND, "服务码不存在");
        validateCode(code, account, now);

        AccountRenewal renewal = new AccountRenewal();
        renewal.setServiceAccountId(account.getId());
        renewal.setServiceCodeId(code.getId());
        renewal.setOwnerCompanyId(account.getOwnerCompanyId());
        renewal.setAssignedUserId(account.getAssignedUserId());
        renewal.setSpecCode(code.getSpecCode());
        renewal.setServiceType(code.getServiceType());
        renewal.setDurationDays(code.getDurationDays());
        renewal.setCodeSilenceDays(code.getCodeSilenceDays());
        renewal.setServiceCodeSnapshot(serialize(new AccountRenewalCodeSnapshot(code.getId(), code.getCode(),
                code.getOwnerCompanyId(), code.getSpecCode(), code.getServiceType(), code.getDurationDays(),
                code.getCodeSilenceDays(), code.getExpireAt())));
        renewal.setRequestId(command.requestId());
        renewal.setStatus(AccountRenewalConstants.PROCESSING);
        renewal.setActiveServiceCodeId(code.getId());
        renewal.setActiveServiceAccountId(account.getId());
        renewal.setOperatorUserId(operator == null ? null : operator.userId());
        renewal.setOperatorUserName(operator == null ? null : operator.userName());
        renewal.setCreatedAt(now);
        renewal.setUpdatedAt(now);
        renewal.setVersion(0L);
        if (renewalMapper.insert(renewal) != 1 || renewal.getId() == null) {
            throw new BusinessException(ErrorCode.ACCOUNT_RENEWAL_STATE_INCONSISTENT, "续期记录创建失败");
        }
        if (serviceCodeMapper.reserveForRenewal(code.getId(), account.getOwnerCompanyId(), command.requestId(),
                code.getVersion(), now) != 1) {
            throw new BusinessException(ErrorCode.SERVICE_CODE_NOT_PENDING, "服务码状态已发生变化");
        }

        CorsOperation operation = new CorsOperation();
        operation.setRequestId(command.requestId());
        operation.setOperationType(AccountRenewalConstants.OPERATION_TYPE);
        operation.setBizType(AccountRenewalConstants.BIZ_TYPE);
        operation.setBizId(renewal.getId());
        operation.setServiceAccountId(account.getId());
        operation.setStatus(AccountRenewalConstants.PENDING);
        operation.setRetryCount(0);
        operation.setVersion(0L);
        operation.setCreatedAt(now);
        operation.setUpdatedAt(now);
        if (operationMapper.insert(operation) != 1) {
            throw new BusinessException(ErrorCode.ACCOUNT_RENEWAL_STATE_INCONSISTENT, "续期 CORS 操作创建失败");
        }
        return new AccountRenewalReservation(renewal.getId(), operation.getId(), command.requestId(), true);
    }

    private AccountRenewalReservation idempotent(CreateAccountRenewalCommand command, UserScope scope,
                                                  AccountRenewal existing) {
        AccountRenewalReserveService.assertAccess(scope, existing.getOwnerCompanyId(), existing.getAssignedUserId());
        if (!command.serviceAccountId().equals(existing.getServiceAccountId())
                || !command.serviceCodeId().equals(existing.getServiceCodeId())) {
            throw new BusinessException(ErrorCode.RENEWAL_IDEMPOTENCY_CONFLICT,
                    "requestId 已用于不同的账号续期");
        }
        CorsOperation operation = operationMapper.selectByBusiness(AccountRenewalConstants.BIZ_TYPE,
                existing.getId());
        if (!AccountRenewalConstants.isOperation(operation, existing.getId(), existing.getServiceAccountId())
                || !existing.getRequestId().equals(operation.getRequestId())) {
            throw new BusinessException(ErrorCode.ACCOUNT_RENEWAL_STATE_INCONSISTENT,
                    "续期记录与 CORS 操作关系不一致");
        }
        return new AccountRenewalReservation(existing.getId(), operation.getId(), existing.getRequestId(), false);
    }

    private static boolean hasIdentity(ServiceAccount account) {
        return account.getOwnerCompanyId() != null && nonblank(account.getCorsAccountId())
                && nonblank(account.getAccount());
    }

    private static void assertRenewableSource(ServiceAccount account) {
        if (account.getAccountSource() != AccountSource.EXCHANGE) {
            String message = account.getAccountSource() == AccountSource.TEST
                    ? "测试账号暂不支持服务码续期"
                    : "历史导入账号暂不支持服务码续期";
            throw new BusinessException(ErrorCode.ACCOUNT_SOURCE_NOT_RENEWABLE, message);
        }
    }

    private static void validateCode(ServiceCode code, ServiceAccount account, LocalDateTime now) {
        if (code.getStatus() != ServiceCodeStatus.PENDING) {
            throw new BusinessException(ErrorCode.SERVICE_CODE_NOT_PENDING, "服务码当前不可用");
        }
        if (code.getProcessingType() != null || code.getProcessingRequestId() != null) {
            throw new BusinessException(ErrorCode.SERVICE_CODE_NOT_PENDING,
                    "待用服务码仍带有其他处理中标记");
        }
        if (code.getOwnerCompanyId() == null || !code.getOwnerCompanyId().equals(account.getOwnerCompanyId())) {
            throw new BusinessException(ErrorCode.SERVICE_CODE_NOT_OWNED, "服务码与账号所属公司不一致");
        }
        if (!java.util.Objects.equals(code.getServiceType(), account.getServiceType())) {
            throw new BusinessException(ErrorCode.ACCOUNT_RENEWAL_STATE_INCONSISTENT,
                    "服务码与账号服务类型不一致");
        }
        if (code.getExpireAt() == null || !code.getExpireAt().isAfter(now)) {
            throw new BusinessException(ErrorCode.SERVICE_CODE_EXPIRED, "服务码已过期");
        }
        if (code.getVersion() == null || code.getDurationDays() == null || code.getDurationDays() <= 0
                || code.getCodeSilenceDays() == null || code.getCodeSilenceDays() < 0
                || !nonblank(code.getServiceType()) || !nonblank(code.getCode())
                || !nonblank(code.getSpecCode())) {
            throw new BusinessException(ErrorCode.ACCOUNT_RENEWAL_STATE_INCONSISTENT,
                    "服务码规格快照不完整");
        }
    }

    private String serialize(AccountRenewalCodeSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "续期服务码快照序列化失败");
        }
    }

    private static boolean nonblank(String value) { return value != null && !value.isBlank(); }
}
