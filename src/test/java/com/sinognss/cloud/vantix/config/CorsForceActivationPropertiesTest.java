package com.sinognss.cloud.vantix.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorsForceActivationPropertiesTest {

    @Test
    void defaultsAreDisabledAndHaveExpectedValues() {
        CorsForceActivationProperties properties = new CorsForceActivationProperties();

        assertFalse(properties.isEnabled());
        assertEquals(Duration.ofMinutes(1), properties.getScanInterval());
        assertEquals(100, properties.getCandidateBatchSize());
        assertEquals(Duration.ofSeconds(5), properties.getPollInterval());
        assertEquals(20, properties.getWorkerBatchSize());
        assertEquals(10, properties.getMaxRetries());
        assertEquals(Duration.ofSeconds(30), properties.getRetryBaseDelay());
        assertEquals(Duration.ofHours(1), properties.getRetryMaxDelay());
        assertEquals(Duration.ofMinutes(2), properties.getClaimTimeout());
    }

    @ParameterizedTest
    @MethodSource("positiveDurationSetters")
    void scanPollRetryAndClaimDurationsMustBePositive(
            BiConsumer<CorsForceActivationProperties, Duration> setter) {
        assertDoesNotThrow(() -> setter.accept(new CorsForceActivationProperties(), Duration.ofNanos(1)));
        assertThrows(IllegalArgumentException.class,
                () -> setter.accept(new CorsForceActivationProperties(), Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> setter.accept(new CorsForceActivationProperties(), Duration.ofNanos(-1)));
        assertThrows(IllegalArgumentException.class,
                () -> setter.accept(new CorsForceActivationProperties(), null));
    }

    static Stream<Arguments> positiveDurationSetters() {
        return Stream.of(
                Arguments.of((BiConsumer<CorsForceActivationProperties, Duration>)
                        CorsForceActivationProperties::setScanInterval),
                Arguments.of((BiConsumer<CorsForceActivationProperties, Duration>)
                        CorsForceActivationProperties::setPollInterval),
                Arguments.of((BiConsumer<CorsForceActivationProperties, Duration>)
                        CorsForceActivationProperties::setRetryBaseDelay),
                Arguments.of((BiConsumer<CorsForceActivationProperties, Duration>)
                        CorsForceActivationProperties::setRetryMaxDelay),
                Arguments.of((BiConsumer<CorsForceActivationProperties, Duration>)
                        CorsForceActivationProperties::setClaimTimeout));
    }

    @ParameterizedTest
    @MethodSource("batchSizeSetters")
    void batchSizeMustBeBetweenOneAndFiveHundred(
            BiConsumer<CorsForceActivationProperties, Integer> setter) {
        assertDoesNotThrow(() -> setter.accept(new CorsForceActivationProperties(), 1));
        assertDoesNotThrow(() -> setter.accept(new CorsForceActivationProperties(), 500));
        assertThrows(IllegalArgumentException.class,
                () -> setter.accept(new CorsForceActivationProperties(), 0));
        assertThrows(IllegalArgumentException.class,
                () -> setter.accept(new CorsForceActivationProperties(), 501));
    }

    static Stream<Arguments> batchSizeSetters() {
        return Stream.of(
                Arguments.of((BiConsumer<CorsForceActivationProperties, Integer>)
                        CorsForceActivationProperties::setCandidateBatchSize),
                Arguments.of((BiConsumer<CorsForceActivationProperties, Integer>)
                        CorsForceActivationProperties::setWorkerBatchSize));
    }

    @Test
    void maxRetriesMayBeZeroButMustNotBeNegative() {
        CorsForceActivationProperties properties = new CorsForceActivationProperties();

        assertDoesNotThrow(() -> properties.setMaxRetries(0));
        assertEquals(0, properties.getMaxRetries());
        assertThrows(IllegalArgumentException.class, () -> properties.setMaxRetries(-1));
    }

    @Test
    void retryMaximumMustBeAtLeastRetryBase() {
        CorsForceActivationProperties properties = new CorsForceActivationProperties();
        properties.setRetryBaseDelay(Duration.ofMinutes(2));
        properties.setRetryMaxDelay(Duration.ofMinutes(2));

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(properties).isEmpty());

            properties.setRetryMaxDelay(Duration.ofMinutes(1));
            Set<ConstraintViolation<CorsForceActivationProperties>> violations =
                    factory.getValidator().validate(properties);
            assertTrue(violations.stream()
                    .anyMatch(violation -> "retryDelayRangeValid".equals(
                            violation.getPropertyPath().toString())));
        }
    }
}
