package com.sinognss.cloud.vantix.infrastructure.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sinognss.cloud.vantix.application.exchange.ExchangeLogItemQueryRow;
import com.sinognss.cloud.vantix.application.exchange.ExchangeLogQueryRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ExchangeLogQueryMapper {
    IPage<ExchangeLogQueryRow> pageForFrontend(
            IPage<?> page,
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("specCode") String specCode,
            @Param("ownerCompanyId") Long ownerCompanyId,
            @Param("createdFrom") LocalDateTime createdFrom,
            @Param("createdTo") LocalDateTime createdTo,
            @Param("scopeCompanyId") Long scopeCompanyId);

    ExchangeLogQueryRow detail(@Param("requestId") String requestId,
                               @Param("scopeCompanyId") Long scopeCompanyId);

    List<ExchangeLogItemQueryRow> detailItems(@Param("requestId") String requestId,
                                              @Param("scopeCompanyId") Long scopeCompanyId);
}
