package com.sinognss.cloud.vantix.infrastructure.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceCodeQueryMapperSqlTest {
    private static final String NAMESPACE = "com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeQueryMapper";

    @Test
    void statisticsBuildsWithOnlyItsDeclaredParameters() throws Exception {
        MappedStatement statement = mapperStatement("statistics");
        BoundSql boundSql = statement.getBoundSql(statisticsParameters());

        String sql = boundSql.getSql().replaceAll("\\s+", " ").trim();
        assertTrue(sql.contains("sc.owner_company_id = ?"), sql);
        assertTrue(sql.contains("sc.code LIKE CONCAT('%', ?, '%')"), sql);
        assertTrue(sql.contains("sc.spec_code = ?"), sql);
        assertTrue(sql.contains("sc.duration_days = ?"), sql);
        assertTrue(sql.contains("sc.source_order_no = ?"), sql);
        assertFalse(boundSql.getParameterMappings().stream()
                .anyMatch(mapping -> mapping.getProperty().equals("status")
                        || mapping.getProperty().equals("displayStatus")), sql);
        assertFalse(sql.contains("#{status}"), sql);
        assertFalse(sql.contains("#{displayStatus}"), sql);
    }

    @Test
    void listStillBuildsStatusAndDisplayStatusFilters() throws Exception {
        MappedStatement statement = mapperStatement("pageForFrontend");
        Map<String, Object> parameters = statisticsParameters();
        parameters.put("status", "PENDING");
        parameters.put("displayStatus", "EXPIRING");
        BoundSql boundSql = statement.getBoundSql(parameters);

        String sql = boundSql.getSql().replaceAll("\\s+", " ").trim();
        assertTrue(sql.contains("sc.status = ?"), sql);
        assertTrue(sql.contains("sc.expire_at > ?"), sql);
        assertTrue(sql.contains("sc.expire_at <= ?"), sql);
    }

    private MappedStatement mapperStatement(String id) throws Exception {
        Configuration configuration = new Configuration();
        try (InputStream mapperXml = getClass().getResourceAsStream("/mapper/ServiceCodeQueryMapper.xml")) {
            assertTrue(mapperXml != null);
            new XMLMapperBuilder(mapperXml, configuration, "mapper/ServiceCodeQueryMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        return configuration.getMappedStatement(NAMESPACE + "." + id);
    }

    private Map<String, Object> statisticsParameters() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("keyword", "code");
        parameters.put("specCode", "SC001");
        parameters.put("durationDays", 90);
        parameters.put("sourceOrderNo", "ORDER-1");
        parameters.put("ownerCompanyId", 10L);
        parameters.put("scopeCompanyId", null);
        parameters.put("now", LocalDateTime.of(2026, 9, 15, 10, 0));
        parameters.put("upcomingAt", LocalDateTime.of(2026, 9, 22, 10, 0));
        return parameters;
    }
}
