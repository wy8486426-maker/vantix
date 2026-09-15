package com.sinognss.cloud.vantix.common;

import com.sinognss.cloud.vantix.domain.config.DurationUnit;

/**
 * Read-only compatibility helpers for rows and JSON created before the days model.
 * New runtime writes must use explicit day fields and must not call these helpers.
 */
public final class LegacyDurationCompatibility {
    private LegacyDurationCompatibility() {
    }

    public static Integer toDays(Integer value, DurationUnit unit) {
        if (value == null || unit == null) {
            return null;
        }
        try {
            return Math.multiplyExact(value, multiplier(unit));
        } catch (ArithmeticException exception) {
            return null;
        }
    }

    public static Integer toDays(Integer value, String unit) {
        if (value == null || unit == null) {
            return null;
        }
        try {
            return toDays(value, DurationUnit.valueOf(unit.trim().toUpperCase(java.util.Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public static Integer monthsToDays(Integer months) {
        if (months == null) {
            return null;
        }
        try {
            return Math.multiplyExact(months, 30);
        } catch (ArithmeticException exception) {
            return null;
        }
    }

    public static String displayName(Integer value, DurationUnit unit) {
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

    private static int multiplier(DurationUnit unit) {
        return switch (unit) {
            case DAY -> 1;
            case WEEK -> 7;
            case MONTH -> 30;
            case YEAR -> 365;
        };
    }
}
