package com.sinognss.cloud.vantix.application.renewal;

import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.common.user.UserScope;
import com.sinognss.cloud.vantix.domain.renewal.AccountRenewal;
import com.sinognss.cloud.vantix.infrastructure.mapper.AccountRenewalMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.AccountRenewalLogQueryMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AccountRenewalQueryService {
    private final AccountRenewalMapper renewalMapper;
    private final AccountRenewalLogQueryMapper logQueryMapper;
    private final UserHolderBridge userHolder;

    public AccountRenewalQueryService(AccountRenewalMapper renewalMapper,
                                      AccountRenewalLogQueryMapper logQueryMapper,
                                      UserHolderBridge userHolder) {
        this.renewalMapper = renewalMapper;
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
        Long scopeCompanyId = scope.isGlobal() ? null : scope.companyId();
        Long scopeAssignedUserId = scope.type() == UserScope.Type.PERSONAL ? scope.userId() : null;
        AccountRenewalLogQueryRow row = logQueryMapper.detailForFrontend(inputRequestId.trim(),
                scopeCompanyId, scopeAssignedUserId);
        if (row == null) throw new BusinessException(ErrorCode.NOT_FOUND, "续期请求不存在");
        return new AccountRenewalView(row.getRenewalId(), row.getRequestId(), row.getServiceAccountId(),
                row.getServiceCodeId(), row.getSpecCode(), row.getServiceType(), row.getDurationDays(),
                row.getCodeSilenceDays(), row.getStatus(), row.getLastErrorCode(), row.getLastErrorMessage(),
                row.getCurrentAccountExpireAt(), row.getCreatedAt(), row.getUpdatedAt(), row.getCompletedAt(),
                row.getAccountName(), row.getCorsAccountId(), row.getOwnerCompanyId(), row.getOwnerCompanyName(),
                row.getAssignedUserId(), row.getServiceCode(), row.getDisplayName());
    }
}
