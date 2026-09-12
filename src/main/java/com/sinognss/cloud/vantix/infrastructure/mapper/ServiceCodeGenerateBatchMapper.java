package com.sinognss.cloud.vantix.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeGenerateBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ServiceCodeGenerateBatchMapper extends BaseMapper<ServiceCodeGenerateBatch> {
    @Select("SELECT * FROM service_code_generate_batch WHERE request_id = #{requestId} LIMIT 1 FOR UPDATE")
    ServiceCodeGenerateBatch selectByRequestIdForUpdate(@Param("requestId") String requestId);

    @Select("SELECT * FROM service_code_generate_batch WHERE request_id = #{requestId} LIMIT 1")
    ServiceCodeGenerateBatch selectByRequestId(@Param("requestId") String requestId);

    @Select("SELECT * FROM service_code_generate_batch WHERE generation_source = #{source} "
            + "AND owner_company_id = #{companyId} AND business_key_hash = #{businessKeyHash} "
            + "LIMIT 1 FOR UPDATE")
    ServiceCodeGenerateBatch selectByBusinessKeyForUpdate(@Param("source") String source,
                                                           @Param("companyId") Long companyId,
                                                           @Param("businessKeyHash") String businessKeyHash);

    @Select("SELECT * FROM service_code_generate_batch WHERE generation_source = #{source} "
            + "AND owner_company_id = #{companyId} AND business_key_hash = #{businessKeyHash} LIMIT 1")
    ServiceCodeGenerateBatch selectByBusinessKey(@Param("source") String source,
                                                  @Param("companyId") Long companyId,
                                                  @Param("businessKeyHash") String businessKeyHash);

    @Select("SELECT * FROM service_code_generate_batch WHERE batch_no = #{batchNo} LIMIT 1")
    ServiceCodeGenerateBatch selectByBatchNo(@Param("batchNo") String batchNo);

    @Select({"<script>",
            "SELECT * FROM service_code_generate_batch WHERE source_order_no = #{orderNo}",
            "<if test='companyId != null'>AND owner_company_id = #{companyId}</if>",
            "ORDER BY created_at DESC",
            "</script>"})
    List<ServiceCodeGenerateBatch> selectByOrderNo(@Param("orderNo") String orderNo,
                                                    @Param("companyId") Long companyId);

    @Select("SELECT * FROM service_code_generate_batch WHERE generate_order_id = #{orderId} "
            + "ORDER BY spec_code")
    List<ServiceCodeGenerateBatch> selectByGenerateOrderId(@Param("orderId") Long orderId);

    @Update("UPDATE service_code_generate_batch SET generated_count = #{generatedCount}, "
            + "status = 'COMPLETED', updated_at = #{updatedAt} WHERE id = #{id}")
    int complete(@Param("id") Long id, @Param("generatedCount") int generatedCount,
                 @Param("updatedAt") LocalDateTime updatedAt);
}