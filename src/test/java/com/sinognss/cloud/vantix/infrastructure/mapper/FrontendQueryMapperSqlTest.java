package com.sinognss.cloud.vantix.infrastructure.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontendQueryMapperSqlTest {
    @Test
    void allFrontendQueryMapperXmlFilesParseAndExposeTheirStatements() throws Exception {
        Configuration configuration = new Configuration();

        parse(configuration, "/mapper/GenerationOrderQueryMapper.xml");
        parse(configuration, "/mapper/CompanyFrontendQueryMapper.xml");
        parse(configuration, "/mapper/ServiceCodeTransferQueryMapper.xml");
        parse(configuration, "/mapper/ServiceAccountQueryMapper.xml");
        parse(configuration, "/mapper/ExchangeLogQueryMapper.xml");
        parse(configuration, "/mapper/AccountRenewalLogQueryMapper.xml");
        parse(configuration, "/mapper/DashboardQueryMapper.xml");

        assertNotNull(configuration.getMappedStatement(
                "com.sinognss.cloud.vantix.infrastructure.mapper.GenerationOrderQueryMapper.pageForFrontend"));
        assertNotNull(configuration.getMappedStatement(
                "com.sinognss.cloud.vantix.infrastructure.mapper.GenerationOrderQueryMapper.statistics"));
        assertNotNull(configuration.getMappedStatement(
                "com.sinognss.cloud.vantix.infrastructure.mapper.CompanyFrontendQueryMapper.pageForFrontend"));
        assertNotNull(configuration.getMappedStatement(
                "com.sinognss.cloud.vantix.infrastructure.mapper.CompanyFrontendQueryMapper.partners"));
        assertNotNull(configuration.getMappedStatement(
                "com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeTransferQueryMapper.pageForFrontend"));
        assertNotNull(configuration.getMappedStatement(
                "com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeTransferQueryMapper.detailBatch"));
        assertNotNull(configuration.getMappedStatement(
                "com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeTransferQueryMapper.detailItems"));
        assertNotNull(configuration.getMappedStatement(
                "com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountQueryMapper.pageForFrontend"));
        assertNotNull(configuration.getMappedStatement(
                "com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountQueryMapper.statistics"));
        assertNotNull(configuration.getMappedStatement(
                "com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountQueryMapper.detailForFrontend"));
        assertNotNull(configuration.getMappedStatement(
                "com.sinognss.cloud.vantix.infrastructure.mapper.ExchangeLogQueryMapper.pageForFrontend"));
        assertNotNull(configuration.getMappedStatement(
                "com.sinognss.cloud.vantix.infrastructure.mapper.ExchangeLogQueryMapper.detail"));
        assertNotNull(configuration.getMappedStatement(
                "com.sinognss.cloud.vantix.infrastructure.mapper.ExchangeLogQueryMapper.detailItems"));
        assertNotNull(configuration.getMappedStatement(
                "com.sinognss.cloud.vantix.infrastructure.mapper.AccountRenewalLogQueryMapper.pageForFrontend"));
        assertNotNull(configuration.getMappedStatement(
                "com.sinognss.cloud.vantix.infrastructure.mapper.AccountRenewalLogQueryMapper.detailForFrontend"));
        assertNotNull(configuration.getMappedStatement(
                "com.sinognss.cloud.vantix.infrastructure.mapper.DashboardQueryMapper.statistics"));
    }

    @Test
    void newFrontendQueriesStayWithinMySql57AndCarryScopeParameters() throws Exception {
        List<String> resources = List.of("/mapper/ServiceAccountQueryMapper.xml",
                "/mapper/ExchangeLogQueryMapper.xml", "/mapper/AccountRenewalLogQueryMapper.xml",
                "/mapper/DashboardQueryMapper.xml");
        for (String resource : resources) {
            String sql;
            try (InputStream input = getClass().getResourceAsStream(resource)) {
                assertNotNull(input, resource);
                sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
            }
            assertFalse(Pattern.compile("\\bwith\\s+", Pattern.CASE_INSENSITIVE).matcher(sql).find(), resource);
            assertFalse(sql.contains(" over "), resource);
            assertFalse(sql.contains("skip locked"), resource);
            assertFalse(sql.contains("row_number"), resource);
            assertTrue(sql.contains("scopecompanyid"), resource);
        }
        String accountSql = read("/mapper/ServiceAccountQueryMapper.xml");
        assertTrue(accountSql.contains("scopeassigneduserid"));
        String renewalSql = read("/mapper/AccountRenewalLogQueryMapper.xml");
        assertTrue(renewalSql.contains("r.assigned_user_id = #{scopeassigneduserid}"));
    }

    private String read(String resource) throws Exception {
        try (InputStream input = getClass().getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
        }
    }

    private void parse(Configuration configuration, String resource) throws Exception {
        try (InputStream mapperXml = getClass().getResourceAsStream(resource)) {
            assertNotNull(mapperXml, resource);
            new XMLMapperBuilder(mapperXml, configuration, resource, configuration.getSqlFragments()).parse();
        }
    }
}
