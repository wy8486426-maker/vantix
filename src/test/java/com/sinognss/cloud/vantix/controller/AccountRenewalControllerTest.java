package com.sinognss.cloud.vantix.controller;

import com.sinognss.cloud.vantix.application.renewal.AccountRenewalLogQueryService;
import com.sinognss.cloud.vantix.application.renewal.AccountRenewalQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AccountRenewalControllerTest {
    @Test
    void logControllerOwnsTheAlwaysAvailableDetailRoute() {
        AccountRenewalLogQueryService logQueryService = mock(AccountRenewalLogQueryService.class);
        AccountRenewalQueryService queryService = mock(AccountRenewalQueryService.class);

        new AccountRenewalLogController(logQueryService, queryService).detail("RENEWAL-1");

        verify(queryService).get("RENEWAL-1");
        Method detail = Arrays.stream(AccountRenewalLogController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("detail")).findFirst().orElseThrow();
        assertNotNull(detail.getAnnotation(GetMapping.class));
    }

    @Test
    void writeControllerRemainsConditionalAndContainsNoGetRoute() {
        ConditionalOnProperty condition = AccountRenewalController.class
                .getAnnotation(ConditionalOnProperty.class);

        assertEquals("vantix.cors.renewal", condition.prefix());
        assertArrayEquals(new String[]{"enabled"}, condition.name());
        assertEquals("true", condition.havingValue());
        assertFalse(Arrays.stream(AccountRenewalController.class.getDeclaredMethods())
                .anyMatch(method -> method.isAnnotationPresent(GetMapping.class)));
        assertNotNull(AccountRenewalQueryService.class.getAnnotation(Service.class));
    }
}
