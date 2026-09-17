package com.sinognss.cloud.vantix.domain.account;

import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;

/** Keeps source-specific provenance fields mutually exclusive at the application boundary. */
public final class AccountSourceInvariant {
    private AccountSourceInvariant() { }

    public static void validate(ServiceAccount account) {
        if (account == null || account.getAccountSource() == null
                || account.getOwnerCompanyId() == null
                || blank(account.getCorsAccountId()) || blank(account.getAccount())) {
            invalid();
        }
        boolean exchange = account.getAccountSource() == AccountSource.EXCHANGE;
        boolean test = account.getAccountSource() == AccountSource.TEST;
        boolean history = account.getAccountSource() == AccountSource.HISTORY_IMPORT;
        if (exchange && (account.getSourceServiceCodeId() == null || account.getExchangeBatchId() == null
                || account.getExchangeDetailId() == null || account.getExchangeAt() == null
                || account.getTestIssueBatchId() != null || account.getHistoryImportBatchId() != null)) {
            invalid();
        }
        if (test && (account.getSourceServiceCodeId() != null || account.getExchangeBatchId() != null
                || account.getExchangeDetailId() != null || account.getExchangeAt() != null
                || account.getTestIssueBatchId() == null || account.getHistoryImportBatchId() != null)) {
            invalid();
        }
        if (history && (account.getSourceServiceCodeId() != null || account.getExchangeBatchId() != null
                || account.getExchangeDetailId() != null || account.getExchangeAt() != null
                || account.getTestIssueBatchId() != null || account.getHistoryImportBatchId() == null)) {
            invalid();
        }
    }

    private static void invalid() {
        throw new BusinessException(ErrorCode.ACCOUNT_SOURCE_STATE_INCONSISTENT,
                "服务账号来源字段关系不一致");
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
