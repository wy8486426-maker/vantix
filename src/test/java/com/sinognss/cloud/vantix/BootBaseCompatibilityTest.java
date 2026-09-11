package com.sinognss.cloud.vantix;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:bootcompat;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
class BootBaseCompatibilityTest {
    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void boot32ContextStartsWithBaseDependencyOnClasspath() {
        assertNotNull(applicationContext.getBean(VantixApplication.class));
    }

    @Test
    void baseInterceptorLegacyNamespaceIsExplicitlyDetected() {
        NoClassDefFoundError error = assertThrows(NoClassDefFoundError.class, () -> {
            Class<?> interceptorClass = Class.forName("com.sinognss.cloud.base.filter.UserInterceptor");
            Arrays.stream(interceptorClass.getDeclaredMethods()).filter(method -> method.getName().equals("preHandle"))
                    .findFirst().orElseThrow();
        });

        assertTrue(error.getMessage().contains("javax/servlet/http/HttpServletRequest"));
    }
}
