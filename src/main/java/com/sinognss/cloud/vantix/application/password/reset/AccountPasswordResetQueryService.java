package com.sinognss.cloud.vantix.application.password.reset;

import com.sinognss.cloud.vantix.application.password.AccountPasswordAccessPolicy;
import com.sinognss.cloud.vantix.application.password.AccountPasswordRequestIds;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.domain.password.AccountPasswordAction;
import com.sinognss.cloud.vantix.domain.password.AccountPasswordActionConstants;
import com.sinognss.cloud.vantix.infrastructure.mapper.AccountPasswordActionMapper;

public class AccountPasswordResetQueryService {
    private final AccountPasswordActionMapper actionMapper;
    private final UserHolderBridge userHolder;

    public AccountPasswordResetQueryService(AccountPasswordActionMapper actionMapper, UserHolderBridge userHolder) {
        this.actionMapper = actionMapper;
        this.userHolder = userHolder;
    }

    /** Reads only the local action row; this method has no CORS Gateway dependency. */
    public AccountPasswordResetView get(String inputRequestId) {
        String requestId = AccountPasswordRequestIds.normalize(inputRequestId);
        AccountPasswordAction action = actionMapper.selectByRequestId(requestId);
        if (action == null || !AccountPasswordActionConstants.RESET.equals(action.getActionType())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "密码重置请求不存在");
        }
        AccountPasswordAccessPolicy.assertAccess(userHolder.getUserScope(),
                action.getOwnerCompanyId(), action.getAssignedUserId());
        return new AccountPasswordResetView(action.getRequestId(), action.getServiceAccountId(), action.getAccount(),
                action.getStatus(), action.getLastErrorCode(), action.getLastErrorMessage(), action.getCreatedAt(),
                action.getUpdatedAt(), action.getCompletedAt());
    }
}
