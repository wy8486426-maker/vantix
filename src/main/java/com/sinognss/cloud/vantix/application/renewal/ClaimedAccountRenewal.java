package com.sinognss.cloud.vantix.application.renewal;

import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.domain.renewal.AccountRenewal;

public record ClaimedAccountRenewal(CorsOperation operation, AccountRenewal renewal) {
}
