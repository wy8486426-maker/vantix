package com.sinognss.cloud.vantix.infrastructure.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sinognss.cloud.vantix.application.servicecode.generation.GenerationOrderQueryRow;
import com.sinognss.cloud.vantix.application.servicecode.generation.GenerationOrderStatistics;
import com.sinognss.cloud.vantix.application.servicecode.generation.GenerationOrderStatisticsRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface GenerationOrderQueryMapper {
    IPage<GenerationOrderQueryRow> pageForFrontend(
            IPage<?> page,
            @Param("keyword") String keyword,
            @Param("generationSource") String generationSource,
            @Param("status") String status,
            @Param("ownerCompanyId") Long ownerCompanyId,
            @Param("scopeCompanyId") Long scopeCompanyId,
            @Param("createdFrom") LocalDateTime createdFrom,
            @Param("createdTo") LocalDateTime createdTo);

    GenerationOrderStatisticsRow statistics(
            @Param("keyword") String keyword,
            @Param("generationSource") String generationSource,
            @Param("status") String status,
            @Param("ownerCompanyId") Long ownerCompanyId,
            @Param("scopeCompanyId") Long scopeCompanyId,
            @Param("createdFrom") LocalDateTime createdFrom,
            @Param("createdTo") LocalDateTime createdTo);
}
