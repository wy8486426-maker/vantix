package com.sinognss.cloud.vantix.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface ServiceCodeMapper extends BaseMapper<ServiceCode> {

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
}
