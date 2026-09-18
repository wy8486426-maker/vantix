package com.sinognss.cloud.vantix.application.servicecode;

public record ServiceCodeSpecStatisticsItem(String specCode,
                                            String displayName,
                                            Integer durationDays,
                                            long total,
                                            long waiting,
                                            long expiring,
                                            long processing,
                                            long consumed,
                                            long expired) {
}
