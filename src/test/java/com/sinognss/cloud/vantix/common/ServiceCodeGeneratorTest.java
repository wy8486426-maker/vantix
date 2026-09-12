package com.sinognss.cloud.vantix.common;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceCodeGeneratorTest {
    @Test
    void generatesHighEntropyCodesInExpectedHumanInputFormat() {
        ServiceCodeGenerator generator = new ServiceCodeGenerator();
        Set<String> codes = new HashSet<>();
        for (int index = 0; index < 1000; index++) {
            codes.add(generator.generate(LocalDate.of(2026, 9, 12)));
        }
        assertEquals(1000, codes.size());
        assertTrue(codes.stream().allMatch(code ->
                code.matches("^VX260912[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{20}$")));
    }
}