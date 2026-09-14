package com.sinognss.cloud.vantix.application.password.reset;

import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.domain.password.AccountPasswordAction;

public record ClaimedAccountPasswordReset(
        CorsOperation operation,
        AccountPasswordAction action,
        boolean queryFirst) {
}
