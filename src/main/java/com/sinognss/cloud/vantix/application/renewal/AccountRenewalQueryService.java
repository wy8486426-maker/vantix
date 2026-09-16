package com.sinognss.cloud.vantix.application.renewal;

import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.domain.renewal.AccountRenewal;
import com.sinognss.cloud.vantix.infrastructure.mapper.AccountRenewalMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.AccountRenewalLogQueryMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import org.springframework.util.StringUtils;

public class AccountRenewalQueryService {
    private final AccountRenewalMapper renewalMapper;
    private final ServiceAccountMapper accountMapper;
    private final AccountRenewalLogQueryMapper logQueryMapper;
    private final UserHolderBridge userHolder;

    public AccountRenewalQueryService(AccountRenewalMapper renewalMapper,
                                      ServiceAccountMapper accountMapper,
                                      UserHolderBridge userHolder) {
        this(renewalMapper, accountMapper, null, userHolder);
    }

    public AccountRenewalQueryService(AccountRenewalMapper renewalMapper,
                                      AccountRenewalLogQueryMapper logQueryMapper,
                                      UserHolderBridge userHolder) {
        this(renewalMapper, null, logQueryMapper, userHolder);
    }

    private AccountRenewalQueryService(AccountRenewalMapper renewalMapper,
                                       ServiceAccountMapper accountMapper,
                                       AccountRenewalLogQueryMapper logQueryMapper,
                                       UserHolderBridge userHolder) {
        this.renewalMapper = renewalMapper;
        this.accountMapper = accountMapper;
        this.logQueryMapper = logQueryMapper;
        this.userHolder = userHolder;
    }

    public AccountRenewalView get(String inputRequestId) {
        if (!StringUtils.hasText(inputRequestId) || inputRequestId.trim().length() > 128
                || inputRequestId.codePoints().anyMatch(Character::isISOControl)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "requestId 非法");
        }
        AccountRenewal renewal = renewalMapper.selectByRequestId(inputRequestId.trim());
        if (renewal == null) throw new BusinessException(ErrorCode.NOT_FOUND, "续期请求不存在");
        var scope = userHolder.getUserScope();
        AccountRenewalReserveService.assertAccess(scope, renewal.getOwnerCompanyId(),
                renewal.getAssignedUserId());
        if (logQueryMapper != null) {
            Long scopeCompanyId = scope.isGlobal() ? null : scope.companyId();
            Long scopeAssignedUserId = scope.type() == com.sinognss.cloud.vantix.common.user.UserScope.Type.PERSONAL
                    ? scope.userId() : null;
            AccountRenewalLogQueryRow row = logQueryMapper.detailForFrontend(inputRequestId.trim(),
                    scopeCompanyId, scopeAssignedUserId);
            if (row == null) throw new BusinessException(ErrorCode.NOT_FOUND, "续期请求不存在");
            return new AccountRenewalView(row.getRenewalId(), row.getRequestId(), row.getServiceAccountId(),
                    row.getServiceCodeId(), row.getSpecCode(), row.getServiceType(), row.getDurationDays(),
                    renewal.getCodeSilenceDays(), row.getStatus(), row.getLastErrorCode(), row.getLastErrorMessage(),
                    row.getCurrentAccountExpireAt(), row.getCreatedAt(), row.getUpdatedAt(), row.getCompletedAt(),
                    row.getAccountName(), row.getCorsAccountId(), row.getOwnerCompanyId(), row.getOwnerCompanyName(),
                    row.getAssignedUserId(), row.getServiceCode(), row.getDisplayName());
        }
        ServiceAccount account = accountMapper.selectById(renewal.getServiceAccountId());
        return new AccountRenewalView(renewal.getId(), renewal.getRequestId(), renewal.getServiceAccountId(),
                renewal.getServiceCodeId(), renewal.getSpecCode(), renewal.getServiceType(),
                renewal.getDurationDays(), renewal.getCodeSilenceDays(), renewal.getStatus(), renewal.getLastErrorCode(),
                renewal.getLastErrorMessage(), account == null ? null : account.getExpireAt(),
                renewal.getCreatedAt(), renewal.getUpdatedAt(), renewal.getCompletedAt(),
                account == null ? null : account.getAccount(), account == null ? null : account.getCorsAccountId(),
                renewal.getOwnerCompanyId(), null, renewal.getAssignedUserId(), null, null);
    }
}
