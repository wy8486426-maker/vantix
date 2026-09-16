package com.sinognss.cloud.vantix.infrastructure.mapper;

import com.sinognss.cloud.vantix.domain.company.DealerCompany;
import org.apache.ibatis.annotations.Insert;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DealerCompanySyncMapperSqlTest {
    @Test
    void upsertOnlyUpdatesIdentityNameAndSyncTime() throws NoSuchMethodException {
        Method method = DealerCompanyMapper.class.getMethod("upsertSyncedCompanies", Collection.class);
        Insert insert = method.getAnnotation(Insert.class);
        String sql = String.join(" ", insert.value()).toLowerCase();
        String updateClause = sql.substring(sql.indexOf("on duplicate key update"));

        assertTrue(sql.contains("insert into dealer_company"));
        assertTrue(sql.contains("parent_company_id"));
        assertTrue(updateClause.contains("company_name = values(company_name)"));
        assertTrue(updateClause.contains("company_synced_at = values(company_synced_at)"));
        assertFalse(updateClause.contains("parent_company_id"));
        assertFalse(updateClause.contains("company_status"));
    }
}
