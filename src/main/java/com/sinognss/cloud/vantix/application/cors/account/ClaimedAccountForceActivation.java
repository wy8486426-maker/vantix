package com.sinognss.cloud.vantix.application.cors.account;

import com.sinognss.cloud.vantix.domain.cors.CorsOperation;

public record ClaimedAccountForceActivation(CorsOperation operation, boolean queryFirst) {
}
