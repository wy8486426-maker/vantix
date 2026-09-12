package com.sinognss.cloud.vantix.application.offline;

import java.time.LocalDateTime;
import java.util.List;

public record ParsedOfflineOrderGroup(int rowNumber, String orderNo, LocalDateTime orderTime,
                                      List<ParsedOfflineOrder> items) {
}
