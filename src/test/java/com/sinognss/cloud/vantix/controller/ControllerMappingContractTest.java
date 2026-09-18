package com.sinognss.cloud.vantix.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

import com.sinognss.cloud.vantix.integration.cors.account.CorsPasswordGateway;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountRenewalGateway;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountStatusGateway;

import static org.mockito.Mockito.mock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "vantix.cors-operation.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:mapping-contract;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.cloud.nacos.config.import-check.enabled=false",
        "vantix.cors.password.enabled=true",
        "vantix.cors.renewal.enabled=true"
})
@Import(ControllerMappingContractTest.GatewayMocks.class)
class ControllerMappingContractTest {
    private static final String CONTROLLER_PACKAGE = "com.sinognss.cloud.vantix.controller";

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @TestConfiguration(proxyBeanMethods = false)
    static class GatewayMocks {
        @Bean
        @Primary
        CorsPasswordGateway passwordOperationsGateway() {
            return mock(CorsPasswordGateway.class);
        }

        @Bean
        CorsAccountRenewalGateway renewalGateway() {
            return mock(CorsAccountRenewalGateway.class);
        }

        @Bean
        CorsAccountStatusGateway statusGateway() {
            return mock(CorsAccountStatusGateway.class);
        }
    }

    @Test
    void controllerPermissionUrlsAreStaticAndGloballyUnique() {
        Map<String, ArrayList<String>> handlersByPath = new TreeMap<>();

        handlerMapping.getHandlerMethods().forEach((mapping, handler) -> {
            if (!isVantixController(handler)) {
                return;
            }
            mapping.getPatternValues().forEach(pattern -> {
                String normalizedPath = normalize(pattern);
                assertFalse(normalizedPath.matches(".*\\{[^}]+}.*"),
                        () -> "dynamic permission URL: " + normalizedPath
                                + " handler: " + describe(handler));
                handlersByPath.computeIfAbsent(normalizedPath, ignored -> new ArrayList<>())
                        .add(describe(handler));
            });
        });

        String duplicateUrls = handlersByPath.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> "duplicate permission URL: " + entry.getKey()
                        + " handlers: " + String.join(" | ", entry.getValue()))
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse("");

        assertTrue(duplicateUrls.isEmpty(), duplicateUrls);
    }

    private boolean isVantixController(HandlerMethod handler) {
        return handler.getBeanType().getPackageName().startsWith(CONTROLLER_PACKAGE);
    }

    private String normalize(String pattern) {
        if (pattern.length() > 1 && pattern.endsWith("/")) {
            return pattern.substring(0, pattern.length() - 1);
        }
        return pattern;
    }

    private String describe(HandlerMethod handler) {
        return handler.getBeanType().getSimpleName() + "." + handler.getMethod().getName();
    }
}
