package com.sinognss.cloud.vantix.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sinognss.cloud.vantix.domain.servicecode.GenerationSource;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeGenerateOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ServiceCodeGenerateOrderMapper extends BaseMapper<ServiceCodeGenerateOrder> {
    @Select("SELECT * FROM service_code_generate_order WHERE request_id = #{requestId} LIMIT 1")
    ServiceCodeGenerateOrder selectByRequestId(@Param("requestId") String requestId);

    @Select("SELECT * FROM service_code_generate_order WHERE request_id = #{requestId} LIMIT 1 FOR UPDATE")
    ServiceCodeGenerateOrder selectByRequestIdForUpdate(@Param("requestId") String requestId);

    @Select("SELECT * FROM service_code_generate_order WHERE generation_source = #{source} "
            + "AND owner_company_id = #{companyId} AND source_order_no = #{orderNo} LIMIT 1")
    ServiceCodeGenerateOrder selectByBusinessKey(@Param("source") String source,
                                                  @Param("companyId") Long companyId,
                                                  @Param("orderNo") String orderNo);

    @Select("SELECT * FROM service_code_generate_order WHERE generation_source = #{source} "
            + "AND owner_company_id = #{companyId} AND source_order_no = #{orderNo} LIMIT 1 FOR UPDATE")
    ServiceCodeGenerateOrder selectByBusinessKeyForUpdate(@Param("source") String source,
                                                           @Param("companyId") Long companyId,
                                                           @Param("orderNo") String orderNo);

    @Select({"<script>",
            "SELECT * FROM service_code_generate_order WHERE source_order_no = #{orderNo}",
            "<if test='companyId != null'>AND owner_company_id = #{companyId}</if>",
            "ORDER BY created_at DESC",
            "</script>"})
    List<ServiceCodeGenerateOrder> selectByOrderNo(@Param("orderNo") String orderNo,
                                                    @Param("companyId") Long companyId);

    @Update("UPDATE service_code_generate_order SET status = 'COMPLETED', updated_at = #{updatedAt} "
            + "WHERE id = #{id}")
    int complete(@Param("id") Long id, @Param("updatedAt") LocalDateTime updatedAt);
}
