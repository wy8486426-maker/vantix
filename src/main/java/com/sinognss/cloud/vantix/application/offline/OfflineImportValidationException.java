package com.sinognss.cloud.vantix.application.offline;

import java.util.List;

public class OfflineImportValidationException extends RuntimeException {
    private final List<OfflineImportError> errors;

    public OfflineImportValidationException(List<OfflineImportError> errors) {
        super("线下订单 Excel 校验失败");
        this.errors = List.copyOf(errors);
    }

    public List<OfflineImportError> getErrors() {
        return errors;
    }
}