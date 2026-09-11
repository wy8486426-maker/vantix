package com.sinognss.cloud.vantix.common.user;

import org.springframework.stereotype.Component;

import com.sinognss.cloud.base.dto.UserCacheDTO;
import com.sinognss.cloud.base.filter.UserHolder;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Adapter for the user-center component. The base library is deployment-provided,
 * so this service does not duplicate a user table or authentication implementation.
 */
@Component
public class UserHolderBridge {
    public UserScope getUserScope() {
        UserCacheDTO user = requireUser();
        Pair<Long, Long> pair = UserHolder.getUserAndCompanyId();
        if (pair == null) {
            // A non-null base user with a global data type is the only supported
            // way to arrive here. An empty UserHolder is rejected above.
            return new UserScope(null, null);
        }
        UserScope scope = new UserScope(pair.getLeft(), pair.getRight());
        if (!scope.isSupported()) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_USER_SCOPE,
                    "当前用户数据范围不受支持");
        }
        return scope;
    }

    public OperatorIdentity getOperator() {
        UserCacheDTO user = requireUser();
        if (user.getUserId() == null) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED,
                    "当前用户缺少操作人身份");
        }
        return new OperatorIdentity(user.getUserId(), user.getUserNickname());
    }

    private UserCacheDTO requireUser() {
        UserCacheDTO user = UserHolder.getUser();
        if (user == null) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED,
                    "缺少用户上下文");
        }
        return user;
    }
}
