package com.sinognss.cloud.vantix.common.exception;

public class BusinessException extends com.sinognss.cloud.base.common.exception.BusinessException {
    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode, String message) {
        super(errorCode.value(), message, com.sinognss.cloud.base.common.exception.ErrorEnum.ERROR);
        this.errorCode = errorCode;
    }

    public ErrorCode getVantixErrorCode() {
        return errorCode;
    }
}
