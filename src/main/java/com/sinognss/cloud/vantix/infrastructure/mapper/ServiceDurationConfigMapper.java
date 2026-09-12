package com.sinognss.cloud.vantix.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sinognss.cloud.vantix.domain.config.ServiceDurationConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ServiceDurationConfigMapper extends BaseMapper<ServiceDurationConfig> {
    @Select("SELECT * FROM service_duration_config WHERE enabled = 1 "
            + "ORDER BY duration_value, duration_unit, service_type")
    List<ServiceDurationConfig> selectEnabled();

    @Select("SELECT * FROM service_duration_config WHERE spec_code = #{specCode} "
            + "AND enabled = 1 LIMIT 1 FOR UPDATE")
    ServiceDurationConfig selectEnabledBySpecCode(@Param("specCode") String specCode);
}