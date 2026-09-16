package com.sinognss.cloud.vantix.infrastructure.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V1SchemaStaticCompatibilityTest {
    @Test
    void baselineContainsNoDatabaseProgramObjects() throws IOException {
        String sql;
        try (InputStream migration = getClass().getResourceAsStream("/db/migration/V1__init_schema.sql")) {
            assertNotNull(migration);
            sql = new String(migration.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceAll("(?s)/\\*.*?\\*/", "")
                    .replaceAll("(?m)--[^\\r\\n]*", "")
                    .toLowerCase(Locale.ROOT);
        }

        for (String objectType : new String[]{"trigger", "procedure", "function", "event"}) {
            assertFalse(Pattern.compile("\\bcreate\\s+" + objectType + "\\b")
                    .matcher(sql).find(), "V1 must not create a " + objectType);
        }
        assertFalse(sql.contains("signal sqlstate"), "V1 must not rely on SIGNAL outside a trigger");
        assertFalse(sql.contains("generated always"), "V1 must not use generated columns");
        assertFalse(sql.contains("stored"), "V1 must not use STORED generated columns");
        assertFalse(sql.contains("virtual"), "V1 must not use VIRTUAL generated columns");
        assertTrue(Pattern.compile("\\bjson\\b").matcher(sql).find(),
                "V1 must retain JSON columns");
    }
}
