package com.sinognss.cloud.vantix.common.api;

import com.sinognss.cloud.base.common.api.CommonResult;

/**
 * Small application-level facade around the response type supplied by sino-cloud-base.
 */
public final class CommonResultAdapter {
    private CommonResultAdapter() {
    }

    public static <T> CommonResult<T> success(T data) {
        return CommonResult.success(data);
    }

    public static CommonResult<Void> failure(String code, String message) {
        return CommonResult.failed("[" + code + "] " + message);
    }
}
