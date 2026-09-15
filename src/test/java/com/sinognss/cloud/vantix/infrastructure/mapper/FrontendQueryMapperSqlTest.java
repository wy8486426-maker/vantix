package com.sinognss.cloud.vantix.infrastructure.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class FrontendQueryMapperSqlTest {
    @Test
    void allFrontendQueryMapperXmlFilesParseAndExposeTheirStatements() throws Exception {
        Configuration configuration = new Configuration();

        parse(configuration, "/mapper/GenerationOrderQueryMapper.xml");
        parse(configuration, "/mapper/CompanyFrontendQueryMapper.xml");
        parse(configuration, "/mapper/ServiceCodeTransferQueryMapper.xml");

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
    }

    private void parse(Configuration configuration, String resource) throws Exception {
        try (InputStream mapperXml = getClass().getResourceAsStream(resource)) {
            assertNotNull(mapperXml, resource);
            new XMLMapperBuilder(mapperXml, configuration, resource, configuration.getSqlFragments()).parse();
        }
    }
}
