package com.sinognss.cloud.vantix.config;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorsAccountRenewalPropertiesTest {
    @Test
    void defaultsAreDisabledAndUseBoundedRetryConfiguration() {
        CorsAccountRenewalProperties properties = new CorsAccountRenewalProperties();

        assertFalse(properties.isEnabled());
        assertEquals(Duration.ofSeconds(5), properties.getPollInterval());
        assertEquals(20, properties.getWorkerBatchSize());
        assertEquals(10, properties.getMaxRetries());
        assertEquals(Duration.ofSeconds(30), properties.getRetryBaseDelay());
        assertEquals(Duration.ofHours(1), properties.getRetryMaxDelay());
        assertEquals(Duration.ofMinutes(2), properties.getClaimTimeout());
    }

    @Test
    void rejectsInvalidWorkerRetryAndDelaySettings() {
        CorsAccountRenewalProperties properties = new CorsAccountRenewalProperties();
        assertThrows(IllegalArgumentException.class, () -> properties.setWorkerBatchSize(0));
        assertThrows(IllegalArgumentException.class, () -> properties.setMaxRetries(-1));
        assertThrows(IllegalArgumentException.class, () -> properties.setPollInterval(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> properties.setClaimTimeout(null));
        properties.setRetryBaseDelay(Duration.ofMinutes(2));
        properties.setRetryMaxDelay(Duration.ofMinutes(1));
        try (var validator = Validation.buildDefaultValidatorFactory()) {
            assertTrue(validator.getValidator().validate(properties).stream()
                    .anyMatch(violation -> "retryDelayRangeValid".equals(
                            violation.getPropertyPath().toString())));
        }
    }
}
