package com.sinognss.cloud.vantix.application.offline;

import java.time.LocalDateTime;

public record ParsedOfflineOrder(int rowNumber, String orderNo, String specCode, String displayName,
                                 int quantity, LocalDateTime orderTime, String remark) {
}