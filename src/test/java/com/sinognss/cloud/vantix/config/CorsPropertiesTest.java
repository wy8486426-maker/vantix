package com.sinognss.cloud.vantix.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CorsPropertiesTest {
    @Test
    void baseUrlKeepsOnlyTheCorsServiceOrigin() {
        CorsProperties properties = new CorsProperties();

        properties.setBaseUrl(" https://cors.example.test:8443/ ");

        assertEquals("https://cors.example.test:8443", properties.getBaseUrl());
    }

    @Test
    void baseUrlRejectsEmbeddedApiPathAndQuery() {
        CorsProperties properties = new CorsProperties();

        assertThrows(IllegalArgumentException.class,
                () -> properties.setBaseUrl("https://cors.example.test:8443/BaseUser"));
        assertThrows(IllegalArgumentException.class,
                () -> properties.setBaseUrl("https://cors.example.test:8443?path=/BaseUser"));
    }
}
