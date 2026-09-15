package com.sinognss.cloud.vantix.application.servicecode;

public record ServiceCodeStatistics(long total,
                                    long waiting,
                                    long expiring,
                                    long expired,
                                    long processing,
                                    long consumed) {
}
