package com.sinognss.cloud.vantix.infrastructure.mapper;

import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceDurationConfigMapperSqlTest {
    @Test
    void mutableUpdateDoesNotWriteImmutableIdentityColumns() throws Exception {
        Method method = ServiceDurationConfigMapper.class.getMethod("updateMutableFields",
                Long.class, String.class, Integer.class, Integer.class, Boolean.class, String.class, Long.class);
        Update update = method.getAnnotation(Update.class);
        String sql = String.join(" ", update.value())
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);

        assertTrue(sql.contains("update service_duration_config"), sql);
        assertTrue(sql.contains("set display_name ="), sql);
        assertTrue(sql.contains("code_silence_days ="), sql);
        assertTrue(sql.contains("account_silence_days ="), sql);
        assertTrue(sql.contains("enabled ="), sql);
        assertTrue(sql.contains("remark ="), sql);
        assertTrue(sql.contains("updated_by ="), sql);
        assertFalse(sql.matches("(?s).*\\bspec_code\\s*=.*"), sql);
        assertFalse(sql.matches("(?s).*\\bservice_type\\s*=.*"), sql);
        assertFalse(sql.matches("(?s).*\\bduration_days\\s*=.*"), sql);
    }
}
