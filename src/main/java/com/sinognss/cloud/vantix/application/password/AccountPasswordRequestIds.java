package com.sinognss.cloud.vantix.application.password;

import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import org.springframework.util.StringUtils;

public final class AccountPasswordRequestIds {
    private AccountPasswordRequestIds() {
    }

    public static String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "requestId 非法");
        }
        String normalized = value.trim();
        if (normalized.length() > 128 || normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "requestId 非法");
        }
        return normalized;
    }
}
