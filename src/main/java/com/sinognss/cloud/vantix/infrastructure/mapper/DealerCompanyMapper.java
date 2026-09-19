package com.sinognss.cloud.vantix.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sinognss.cloud.vantix.domain.company.DealerCompany;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface DealerCompanyMapper extends BaseMapper<DealerCompany> {
    @Select("SELECT id, company_id, company_name, parent_company_id, company_status, "
            + "manager_id AS managerId, manager_tel AS managerTel, company_synced_at, created_at, updated_at "
            + "FROM dealer_company WHERE company_id = #{companyId} LIMIT 1")
    DealerCompany selectByCompanyId(@Param("companyId") Long companyId);

    @Insert({"<script>",
            "INSERT INTO dealer_company (company_id, company_name, manager_id, manager_tel, "
                    + "parent_company_id, company_synced_at)",
            "VALUES",
            "<foreach collection='companies' item='company' separator=','>",
            "(#{company.companyId}, #{company.companyName}, #{company.managerId}, #{company.managerTel}, "
                    + "NULL, #{company.companySyncedAt})",
            "</foreach>",
            "ON DUPLICATE KEY UPDATE",
            "company_name = VALUES(company_name),",
            "manager_id = VALUES(manager_id),",
            "manager_tel = VALUES(manager_tel),",
            "company_synced_at = VALUES(company_synced_at)",
            "</script>"})
    int upsertSyncedCompanies(@Param("companies") Collection<DealerCompany> companies);

    @Select({"<script>",
            "SELECT id, company_id, company_name, manager_id AS managerId, manager_tel AS managerTel, "
                    + "parent_company_id, company_status,",
            "company_synced_at, created_at, updated_at",
            "FROM dealer_company",
            "WHERE company_id IN",
            "<foreach collection='companyIds' item='companyId' open='(' separator=',' close=')'>",
            "#{companyId}",
            "</foreach>",
            "ORDER BY company_id",
            "FOR UPDATE",
            "</script>"})
    List<DealerCompany> selectForUpdate(@Param("companyIds") Collection<Long> companyIds);
}
