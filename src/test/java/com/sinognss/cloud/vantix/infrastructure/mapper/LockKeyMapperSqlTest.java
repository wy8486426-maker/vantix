package com.sinognss.cloud.vantix.infrastructure.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LockKeyMapperSqlTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 16, 12, 0);

    @Test
    void exchangeDetailTransitionsUpdateStatusAndLockKeyTogether() {
        Configuration configuration = new MybatisConfiguration();
        configuration.addMapper(ExchangeDetailMapper.class);

        String completeSql = sql(configuration, "com.sinognss.cloud.vantix.infrastructure.mapper.ExchangeDetailMapper.completeBatchDetails",
                Map.of("batchId", 9L, "accounts",
                        List.of(new ExchangeDetailMapper.CompletedAccountRow(1, "cors-1", "account-1")),
                        "completedAt", NOW));
        assertTrue(completeSql.contains("active_service_code_id = service_code_id"), completeSql);
        assertTrue(completeSql.contains("status = 'PROCESSING'"), completeSql);

        String failSql = sql(configuration, "com.sinognss.cloud.vantix.infrastructure.mapper.ExchangeDetailMapper.failByBatchId",
                Map.of("batchId", 9L, "errorCode", "FAILED", "errorMessage", "failed", "updatedAt", NOW));
        assertTrue(failSql.contains("status = 'FAILED'"), failSql);
        assertTrue(failSql.contains("active_service_code_id = NULL"), failSql);
    }

    @Test
    void renewalTransitionsPreserveTheRequiredLockKeySemanticsAndCasGuards() {
        Configuration configuration = new MybatisConfiguration();
        configuration.addMapper(AccountRenewalMapper.class);

        assertSqlContains(configuration, "com.sinognss.cloud.vantix.infrastructure.mapper.AccountRenewalMapper.complete",
                Map.of("id", 1L, "expectedVersion", 2L, "now", NOW),
                "status = 'COMPLETED'", "active_service_code_id = service_code_id",
                "active_service_account_id = NULL", "status = 'PROCESSING'", "version = ?");
        assertSqlContains(configuration, "com.sinognss.cloud.vantix.infrastructure.mapper.AccountRenewalMapper.fail",
                Map.of("id", 1L, "expectedVersion", 2L, "errorCode", "FAILED",
                        "errorMessage", "failed", "now", NOW),
                "status = 'FAILED'", "active_service_code_id = NULL",
                "active_service_account_id = NULL", "status = 'PROCESSING'");
        assertSqlContains(configuration, "com.sinognss.cloud.vantix.infrastructure.mapper.AccountRenewalMapper.markManualReview",
                Map.of("id", 1L, "expectedVersion", 2L, "errorCode", "REVIEW",
                        "errorMessage", "review", "now", NOW),
                "status = 'MANUAL_REVIEW'", "active_service_code_id = service_code_id",
                "active_service_account_id = service_account_id", "status = 'PROCESSING'");
    }

    @Test
    void passwordTransitionAcceptsAnExplicitLockKeyInTheSameCasUpdate() {
        Configuration configuration = new MybatisConfiguration();
        configuration.addMapper(AccountPasswordActionMapper.class);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("id", 1L);
        parameters.put("expectedVersion", 2L);
        parameters.put("newStatus", "MANUAL_REVIEW");
        parameters.put("errorCode", "REVIEW");
        parameters.put("errorMessage", "review");
        parameters.put("completedAt", NOW);
        parameters.put("now", NOW);
        parameters.put("activeResetAccountId", 31L);

        String sql = sql(configuration,
                "com.sinognss.cloud.vantix.infrastructure.mapper.AccountPasswordActionMapper.transitionFromProcessing",
                parameters);
        assertTrue(sql.contains("active_reset_account_id = ?"), sql);
        assertTrue(sql.contains("status = 'PROCESSING'"), sql);
        assertTrue(sql.contains("version = ?"), sql);
    }

    private static void assertSqlContains(Configuration configuration, String statementId,
                                          Map<String, ?> parameters, String... fragments) {
        String sql = sql(configuration, statementId, parameters);
        for (String fragment : fragments) {
            assertTrue(sql.contains(fragment), statementId + " missing: " + fragment + " in " + sql);
        }
    }

    private static String sql(Configuration configuration, String statementId, Map<String, ?> parameters) {
        MappedStatement statement = configuration.getMappedStatement(statementId);
        BoundSql boundSql = statement.getBoundSql(parameters);
        return boundSql.getSql().replaceAll("\\s+", " ").trim();
    }
}
