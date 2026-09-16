package com.sinognss.cloud.vantix.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sinognss.cloud.vantix.domain.exchange.CompanyExchangeConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CompanyExchangeConfigMapper extends BaseMapper<CompanyExchangeConfig> {
    @Select("SELECT * FROM company_exchange_config WHERE company_id = #{companyId} LIMIT 1")
    CompanyExchangeConfig selectByCompanyId(@Param("companyId") Long companyId);
}
