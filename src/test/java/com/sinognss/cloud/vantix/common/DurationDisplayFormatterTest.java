package com.sinognss.cloud.vantix.common;

import com.sinognss.cloud.vantix.domain.config.DurationUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DurationDisplayFormatterTest {
    @Test
    void formatsEverySupportedDurationUnitForPeople() {
        assertEquals("1天", DurationDisplayFormatter.format(1, DurationUnit.DAY));
        assertEquals("1周", DurationDisplayFormatter.format(1, DurationUnit.WEEK));
        assertEquals("1个月", DurationDisplayFormatter.format(1, DurationUnit.MONTH));
        assertEquals("3个月", DurationDisplayFormatter.format(3, DurationUnit.MONTH));
        assertEquals("1年", DurationDisplayFormatter.format(1, DurationUnit.YEAR));
    }
}