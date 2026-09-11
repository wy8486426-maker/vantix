package com.sinognss.cloud.vantix.common.user;

import org.springframework.stereotype.Component;

import com.sinognss.cloud.base.dto.UserCacheDTO;
import com.sinognss.cloud.base.filter.UserHolder;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Adapter for the user-center component. The base library is deployment-provided,
 * so this service does not duplicate a user table or authentication implementation.
 */
@Component
public class UserHolderBridge {
    public UserScope getUserScope() {
        Pair<Long, Long> pair = UserHolder.getUserAndCompanyId();
        if (pair == null) {
            return new UserScope(null, null);
        }
        return new UserScope(pair.getLeft(), pair.getRight());
    }

    public OperatorIdentity getOperator() {
        UserCacheDTO user = UserHolder.getUser();
        if (user == null) {
            return OperatorIdentity.system();
        }
        return new OperatorIdentity(user.getUserId(), user.getUserNickname());
    }
}
