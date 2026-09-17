package com.sinognss.cloud.vantix.infrastructure.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sinognss.cloud.vantix.application.account.ServiceAccountQueryRow;
import com.sinognss.cloud.vantix.application.account.ServiceAccountStatisticsRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ServiceAccountQueryMapper {
    IPage<ServiceAccountQueryRow> pageForFrontend(
            IPage<?> page,
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("specCode") String specCode,
            @Param("durationDays") Integer durationDays,
            @Param("ownerCompanyId") Long ownerCompanyId,
            @Param("assignedUserId") Long assignedUserId,
            @Param("scopeCompanyId") Long scopeCompanyId,
            @Param("scopeAssignedUserId") Long scopeAssignedUserId);

    IPage<ServiceAccountQueryRow> pageForFrontendWithSource(
            IPage<?> page,
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("specCode") String specCode,
            @Param("durationDays") Integer durationDays,
            @Param("ownerCompanyId") Long ownerCompanyId,
            @Param("assignedUserId") Long assignedUserId,
            @Param("scopeCompanyId") Long scopeCompanyId,
            @Param("scopeAssignedUserId") Long scopeAssignedUserId,
            @Param("accountSource") com.sinognss.cloud.vantix.domain.account.AccountSource accountSource);

    ServiceAccountStatisticsRow statistics(
            @Param("keyword") String keyword,
            @Param("specCode") String specCode,
            @Param("durationDays") Integer durationDays,
            @Param("ownerCompanyId") Long ownerCompanyId,
            @Param("assignedUserId") Long assignedUserId,
            @Param("scopeCompanyId") Long scopeCompanyId,
            @Param("scopeAssignedUserId") Long scopeAssignedUserId);

    ServiceAccountQueryRow detailForFrontend(@Param("id") Long id,
                                             @Param("scopeCompanyId") Long scopeCompanyId,
                                             @Param("scopeAssignedUserId") Long scopeAssignedUserId);
}
