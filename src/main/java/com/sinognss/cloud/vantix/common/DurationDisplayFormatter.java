package com.sinognss.cloud.vantix.common;

import com.sinognss.cloud.vantix.domain.config.DurationUnit;

import java.util.Locale;

public final class DurationDisplayFormatter {
    private DurationDisplayFormatter() {
    }

    public static String format(Integer value, DurationUnit unit) {
        if (value == null || unit == null) {
            return null;
        }
        String suffix = switch (unit) {
            case DAY -> "天";
            case WEEK -> "周";
            case MONTH -> "个月";
            case YEAR -> "年";
        };
        return value + suffix;
    }

    public static DurationUnit parseUnit(String unit) {
        if (unit == null) {
            return null;
        }
        try {
            return DurationUnit.valueOf(unit.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}