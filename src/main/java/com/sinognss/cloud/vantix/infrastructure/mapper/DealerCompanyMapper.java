package com.sinognss.cloud.vantix.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sinognss.cloud.vantix.domain.company.DealerCompany;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface DealerCompanyMapper extends BaseMapper<DealerCompany> {
    @Select({"<script>",
            "SELECT id, company_id, company_name, parent_company_id, company_status,",
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
