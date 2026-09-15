package com.sinognss.cloud.vantix.infrastructure.mapper;

import com.sinognss.cloud.vantix.domain.servicecode.ServiceCode;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeStatus;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.EnumTypeHandler;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceCodeMapperBatchInsertSqlTest {
    @Test
    void insertBatchBuildsOneBoundMultiRowValuesStatement() throws Exception {
        Configuration configuration = new Configuration();
        try (InputStream mapperXml = getClass().getResourceAsStream("/mapper/ServiceCodeMapper.xml")) {
            assertTrue(mapperXml != null);
            new XMLMapperBuilder(mapperXml, configuration, "mapper/ServiceCodeMapper.xml",
                    configuration.getSqlFragments()).parse();
        }

        MappedStatement statement = configuration.getMappedStatement(
                "com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeMapper.insertBatch");
        BoundSql boundSql = statement.getBoundSql(Map.of("codes", List.of(code("CODE-ONE"), code("CODE-TWO"))));
        String sql = boundSql.getSql().replaceAll("\\s+", " ").trim();

        assertTrue(sql.contains("INSERT INTO service_code"));
        assertEquals(1, sql.split("\\)\\s*,\\s*\\(", -1).length - 1, sql);
        assertEquals(36, boundSql.getParameterMappings().size());
        assertEquals(6, boundSql.getParameterMappings().stream()
                .filter(mapping -> mapping.getTypeHandler() instanceof EnumTypeHandler)
                .count());
        assertFalse(sql.contains("CODE-ONE"));
        assertFalse(sql.contains("CODE-TWO"));
    }

    private ServiceCode code(String value) {
        ServiceCode code = new ServiceCode();
        code.setCode(value);
        code.setSourceOrderNo("ORDER-1");
        code.setGenerateBatchId(10L);
        code.setOwnerCompanyId(100L);
        code.setServiceType("CORS");
        code.setSpecCode("M1");
        code.setDurationDays(30);
        code.setCodeSilenceDays(180);
        code.setExpireAt(LocalDateTime.parse("2027-03-13T00:00:00"));
        code.setStatus(ServiceCodeStatus.PENDING);
        code.setVersion(0L);
        code.setCreatedAt(LocalDateTime.parse("2026-09-13T00:00:00"));
        code.setUpdatedAt(LocalDateTime.parse("2026-09-13T00:00:00"));
        return code;
    }
}
