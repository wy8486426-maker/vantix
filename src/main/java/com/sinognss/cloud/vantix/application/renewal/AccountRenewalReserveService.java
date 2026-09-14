package com.sinognss.cloud.vantix.application.renewal;

import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.OperatorIdentity;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.common.user.UserScope;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.domain.renewal.AccountRenewal;
import com.sinognss.cloud.vantix.infrastructure.mapper.AccountRenewalMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.CorsOperationMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.util.StringUtils;

public class AccountRenewalReserveService {
    private final AccountRenewalReserveTransaction transaction;
    private final AccountRenewalMapper renewalMapper;
    private final CorsOperationMapper operationMapper;
    private final UserHolderBridge userHolder;

    public AccountRenewalReserveService(AccountRenewalReserveTransaction transaction,
                                        AccountRenewalMapper renewalMapper,
                                        CorsOperationMapper operationMapper,
                                        UserHolderBridge userHolder) {
        this.transaction = transaction;
        this.renewalMapper = renewalMapper;
        this.operationMapper = operationMapper;
        this.userHolder = userHolder;
    }

    public AccountRenewalReservation reserve(CreateAccountRenewalCommand input) {
        CreateAccountRenewalCommand command = normalize(input);
        UserScope scope = userHolder.getUserScope();
        OperatorIdentity operator = userHolder.getOperator();
        try {
            return transaction.reserve(command, scope, operator);
        } catch (DuplicateKeyException duplicate) {
            return resolveDuplicate(command, scope);
        }
    }

    private AccountRenewalReservation resolveDuplicate(CreateAccountRenewalCommand command, UserScope scope) {
        AccountRenewal existing = renewalMapper.selectByRequestId(command.requestId());
        if (existing != null) {
            assertAccess(scope, existing.getOwnerCompanyId(), existing.getAssignedUserId());
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
        AccountRenewal active = renewalMapper.selectActiveByAccount(command.serviceAccountId());
        if (active != null) {
            assertAccess(scope, active.getOwnerCompanyId(), active.getAssignedUserId());
            throw new BusinessException(ErrorCode.ACCOUNT_RENEWAL_IN_PROGRESS,
                    "该账号已有未决续期");
        }
        throw new BusinessException(ErrorCode.RENEWAL_IDEMPOTENCY_CONFLICT,
                "requestId 已被其他操作占用");
    }

    static CreateAccountRenewalCommand normalize(CreateAccountRenewalCommand input) {
        if (input == null || input.requestId() == null || input.serviceAccountId() == null
                || input.serviceCodeId() == null || input.serviceAccountId() <= 0 || input.serviceCodeId() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "续期参数不完整");
        }
        String requestId = input.requestId().trim();
        if (!StringUtils.hasText(requestId) || requestId.length() > 128
                || requestId.codePoints().anyMatch(Character::isISOControl)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "requestId 非法");
        }
        return new CreateAccountRenewalCommand(requestId, input.serviceAccountId(), input.serviceCodeId());
    }

    static void assertAccess(UserScope scope, Long ownerCompanyId, Long assignedUserId) {
        if (scope == null || !scope.isSupported()) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_USER_SCOPE, "当前用户范围不受支持");
        }
        if (!scope.canAccessCompany(ownerCompanyId)
                || (scope.type() == UserScope.Type.PERSONAL
                && !scope.userId().equals(assignedUserId))) {
            throw new BusinessException(ErrorCode.SERVICE_CODE_NOT_OWNED, "无权访问该公司的账号续期");
        }
    }

}
