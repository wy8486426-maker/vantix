package com.sinognss.cloud.vantix.infrastructure.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sinognss.cloud.vantix.application.company.CompanyPageRow;
import com.sinognss.cloud.vantix.application.company.CompanyPartnerRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CompanyFrontendQueryMapper {
    IPage<CompanyPageRow> pageForFrontend(IPage<?> page,
                                          @Param("keyword") String keyword,
                                          @Param("status") String status,
                                          @Param("parentCompanyId") Long parentCompanyId,
                                          @Param("level") String level);

    List<CompanyPartnerRow> partners(@Param("companyId") Long companyId);
}
