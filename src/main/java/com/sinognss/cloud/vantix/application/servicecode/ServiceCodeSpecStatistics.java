package com.sinognss.cloud.vantix.application.servicecode;

import java.util.List;

public record ServiceCodeSpecStatistics(long total, List<ServiceCodeSpecStatisticsItem> items) {
}
