package com.sinognss.cloud.vantix.infrastructure.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sinognss.cloud.vantix.application.renewal.AccountRenewalLogQueryRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface AccountRenewalLogQueryMapper {
    IPage<AccountRenewalLogQueryRow> pageForFrontend(
            IPage<?> page,
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("ownerCompanyId") Long ownerCompanyId,
            @Param("createdFrom") LocalDateTime createdFrom,
            @Param("createdTo") LocalDateTime createdTo,
            @Param("scopeCompanyId") Long scopeCompanyId,
            @Param("scopeAssignedUserId") Long scopeAssignedUserId);

    AccountRenewalLogQueryRow detailForFrontend(@Param("requestId") String requestId,
                                                @Param("scopeCompanyId") Long scopeCompanyId,
                                                @Param("scopeAssignedUserId") Long scopeAssignedUserId);
}
