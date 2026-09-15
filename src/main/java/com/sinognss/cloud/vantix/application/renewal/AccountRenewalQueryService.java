package com.sinognss.cloud.vantix.application.renewal;

import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.domain.renewal.AccountRenewal;
import com.sinognss.cloud.vantix.infrastructure.mapper.AccountRenewalMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import org.springframework.util.StringUtils;

public class AccountRenewalQueryService {
    private final AccountRenewalMapper renewalMapper;
    private final ServiceAccountMapper accountMapper;
    private final UserHolderBridge userHolder;

    public AccountRenewalQueryService(AccountRenewalMapper renewalMapper,
                                      ServiceAccountMapper accountMapper,
                                      UserHolderBridge userHolder) {
        this.renewalMapper = renewalMapper;
        this.accountMapper = accountMapper;
        this.userHolder = userHolder;
    }

    public AccountRenewalView get(String inputRequestId) {
        if (!StringUtils.hasText(inputRequestId) || inputRequestId.trim().length() > 128
                || inputRequestId.codePoints().anyMatch(Character::isISOControl)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "requestId 非法");
        }
        AccountRenewal renewal = renewalMapper.selectByRequestId(inputRequestId.trim());
        if (renewal == null) throw new BusinessException(ErrorCode.NOT_FOUND, "续期请求不存在");
        AccountRenewalReserveService.assertAccess(userHolder.getUserScope(), renewal.getOwnerCompanyId(),
                renewal.getAssignedUserId());
        ServiceAccount account = accountMapper.selectById(renewal.getServiceAccountId());
        return new AccountRenewalView(renewal.getId(), renewal.getRequestId(), renewal.getServiceAccountId(),
                renewal.getServiceCodeId(), renewal.getSpecCode(), renewal.getServiceType(),
                renewal.getDurationDays(), renewal.getCodeSilenceDays(), renewal.getStatus(), renewal.getLastErrorCode(),
                renewal.getLastErrorMessage(), account == null ? null : account.getExpireAt(),
                renewal.getCreatedAt(), renewal.getUpdatedAt(), renewal.getCompletedAt());
    }
}
