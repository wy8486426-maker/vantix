package com.sinognss.cloud.vantix.infrastructure.mapper;

import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceCodeMapperImmutableSqlTest {
    @Test
    void everyServiceCodeUpdateKeepsCodeImmutable() {
        int serviceCodeUpdates = 0;
        for (Method method : ServiceCodeMapper.class.getDeclaredMethods()) {
            Update update = method.getAnnotation(Update.class);
            if (update == null) {
                continue;
            }
            String sql = String.join(" ", update.value())
                    .replaceAll("\\s+", " ")
                    .toLowerCase(Locale.ROOT);
            if (!sql.matches("(?s).*\\bupdate\\s+service_code\\b.*")) {
                continue;
            }
            serviceCodeUpdates++;
            assertFalse(sql.matches("(?s).*\\bcode\\s*=.*"), method.getName() + ": " + sql);
        }

        assertTrue(serviceCodeUpdates > 0, "expected ServiceCodeMapper to declare service_code updates");
    }
}
