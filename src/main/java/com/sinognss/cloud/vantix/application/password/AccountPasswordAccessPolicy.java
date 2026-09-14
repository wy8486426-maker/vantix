package com.sinognss.cloud.vantix.application.password;

import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.UserScope;

public final class AccountPasswordAccessPolicy {
    private AccountPasswordAccessPolicy() {
    }

    public static void assertAccess(UserScope scope, Long ownerCompanyId, Long assignedUserId) {
        if (scope == null || !scope.isSupported()) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_USER_SCOPE, "当前用户范围不受支持");
        }
        if (!scope.canAccessCompany(ownerCompanyId)
                || (scope.type() == UserScope.Type.PERSONAL
                && !scope.userId().equals(assignedUserId))) {
            throw new BusinessException(ErrorCode.SERVICE_CODE_NOT_OWNED, "无权访问该账号");
        }
    }
}
