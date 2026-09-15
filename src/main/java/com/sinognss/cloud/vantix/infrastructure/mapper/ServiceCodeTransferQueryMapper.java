package com.sinognss.cloud.vantix.infrastructure.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sinognss.cloud.vantix.application.servicecode.TransferBatchQueryRow;
import com.sinognss.cloud.vantix.application.servicecode.TransferItemQueryRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ServiceCodeTransferQueryMapper {
    IPage<TransferBatchQueryRow> pageForFrontend(
            IPage<?> page,
            @Param("keyword") String keyword,
            @Param("transferType") String transferType,
            @Param("fromCompanyId") Long fromCompanyId,
            @Param("toCompanyId") Long toCompanyId,
            @Param("createdFrom") LocalDateTime createdFrom,
            @Param("createdTo") LocalDateTime createdTo,
            @Param("specCode") String specCode,
            @Param("durationDays") Integer durationDays,
            @Param("scopeCompanyId") Long scopeCompanyId);

    TransferBatchQueryRow detailBatch(@Param("transferNo") String transferNo,
                                      @Param("scopeCompanyId") Long scopeCompanyId);

    List<TransferItemQueryRow> detailItems(@Param("transferNo") String transferNo);
}
