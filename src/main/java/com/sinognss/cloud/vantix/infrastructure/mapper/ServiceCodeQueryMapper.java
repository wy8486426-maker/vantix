package com.sinognss.cloud.vantix.infrastructure.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sinognss.cloud.vantix.application.servicecode.ServiceCodeQueryRow;
import com.sinognss.cloud.vantix.application.servicecode.ServiceCodeStatisticsRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface ServiceCodeQueryMapper {
    IPage<ServiceCodeQueryRow> pageForFrontend(
            IPage<?> page,
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("displayStatus") String displayStatus,
            @Param("specCode") String specCode,
            @Param("durationDays") Integer durationDays,
            @Param("sourceOrderNo") String sourceOrderNo,
            @Param("ownerCompanyId") Long ownerCompanyId,
            @Param("scopeCompanyId") Long scopeCompanyId,
            @Param("now") LocalDateTime now,
            @Param("upcomingAt") LocalDateTime upcomingAt);

    ServiceCodeQueryRow detailForFrontend(@Param("id") Long id);

    ServiceCodeStatisticsRow statistics(
            @Param("keyword") String keyword,
            @Param("specCode") String specCode,
            @Param("durationDays") Integer durationDays,
            @Param("sourceOrderNo") String sourceOrderNo,
            @Param("ownerCompanyId") Long ownerCompanyId,
            @Param("scopeCompanyId") Long scopeCompanyId,
            @Param("now") LocalDateTime now,
            @Param("upcomingAt") LocalDateTime upcomingAt);
}
