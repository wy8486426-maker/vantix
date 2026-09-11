package com.sinognss.cloud.vantix.application.servicecode;

import java.util.List;

public record PageResponse<T>(List<T> records, long current, long size, long total, long pages) {
}
