package com.sinognss.cloud.vantix.infrastructure.mapper;

import com.sinognss.cloud.vantix.application.dashboard.DashboardQueryRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface DashboardQueryMapper {
    DashboardQueryRow statistics(@Param("scopeCompanyId") Long scopeCompanyId,
                                 @Param("scopeAssignedUserId") Long scopeAssignedUserId,
                                 @Param("now") LocalDateTime now,
                                 @Param("upcomingAt") LocalDateTime upcomingAt);
}
