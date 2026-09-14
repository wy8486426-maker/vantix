package com.sinognss.cloud.vantix.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ServiceCodeMapper extends BaseMapper<ServiceCode> {

    int insertBatch(@Param("codes") List<ServiceCode> codes);

    @Update("UPDATE service_code "
            + "SET owner_company_id = #{toCompanyId}, version = version + 1, updated_at = #{updatedAt} "
            + "WHERE id = #{id} AND owner_company_id = #{fromCompanyId} "
            + "AND status = 'PENDING' AND expire_at > #{now} AND version = #{version}")
    int transferWithCas(@Param("id") Long id,
                        @Param("fromCompanyId") Long fromCompanyId,
                        @Param("toCompanyId") Long toCompanyId,
                        @Param("version") Long version,
                        @Param("now") LocalDateTime now,
                        @Param("updatedAt") LocalDateTime updatedAt);

    @Select("SELECT * FROM service_code WHERE generate_batch_id = #{batchId} ORDER BY id")
    List<ServiceCode> selectByGenerateBatchId(@Param("batchId") Long batchId);

    @Select("SELECT code.* FROM service_code code "
            + "JOIN service_code_generate_batch batch ON batch.id = code.generate_batch_id "
            + "WHERE code.owner_company_id = #{companyId} AND code.status = 'PENDING' "
            + "AND code.expire_at > #{now} AND batch.spec_code = #{specCode} "
            + "AND batch.generation_source = #{generationSource} "
            + "ORDER BY code.expire_at ASC, code.id ASC LIMIT #{quantity} FOR UPDATE")
    List<ServiceCode> selectAvailableForExchange(@Param("companyId") Long companyId,
                                                  @Param("specCode") String specCode,
                                                  @Param("generationSource") String generationSource,
                                                  @Param("now") LocalDateTime now,
                                                  @Param("quantity") int quantity);

    @Update({"<script>",
            "UPDATE service_code SET status = 'PROCESSING', processing_type = 'EXCHANGE',",
            "processing_request_id = #{requestId}, version = version + 1, updated_at = #{now}",
            "WHERE owner_company_id = #{companyId} AND status = 'PENDING' AND expire_at &gt; #{now}",
            "AND id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"})
    int reserveForExchange(@Param("ids") List<Long> ids, @Param("companyId") Long companyId,
                           @Param("requestId") String requestId, @Param("now") LocalDateTime now);

    @Update({"<script>",
            "UPDATE service_code SET status = 'CONSUMED', consume_type = 'EXCHANGE', consumed_at = #{now},",
            "processing_type = NULL, processing_request_id = NULL, version = version + 1, updated_at = #{now}",
            "WHERE status = 'PROCESSING' AND processing_type = 'EXCHANGE'",
            "AND processing_request_id = #{requestId} AND id IN",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"})
    int consumeForExchange(@Param("ids") List<Long> ids, @Param("requestId") String requestId,
                           @Param("now") LocalDateTime now);

    @Update({"<script>",
            "UPDATE service_code SET status = 'PENDING', processing_type = NULL, processing_request_id = NULL,",
            "version = version + 1, updated_at = #{now}",
            "WHERE status = 'PROCESSING' AND processing_type = 'EXCHANGE'",
            "AND processing_request_id = #{requestId} AND id IN",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"})
    int releaseExchangeCodes(@Param("ids") List<Long> ids, @Param("requestId") String requestId,
                             @Param("now") LocalDateTime now);

}