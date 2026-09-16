package com.sinognss.cloud.vantix.application.exchange;

public record ExchangeLogItemView(Long serviceCodeId, String serviceCode, Long serviceAccountId,
                                  String corsAccountId, String accountName, String status) {
    public static ExchangeLogItemView from(ExchangeLogItemQueryRow row) {
        return new ExchangeLogItemView(row.getServiceCodeId(), row.getServiceCode(), row.getServiceAccountId(),
                row.getCorsAccountId(), row.getAccountName(), row.getStatus());
    }
}
