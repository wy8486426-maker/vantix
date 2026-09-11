package com.sinognss.cloud.vantix.config;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClockConfigTest {
    @Test
    void businessClockUsesShanghaiZone() {
        assertEquals(ZoneId.of("Asia/Shanghai"), new ClockConfig().clock().getZone());
    }
}
