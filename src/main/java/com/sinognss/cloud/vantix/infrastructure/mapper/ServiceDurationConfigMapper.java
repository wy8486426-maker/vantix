package com.sinognss.cloud.vantix.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sinognss.cloud.vantix.domain.config.ServiceDurationConfig;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ServiceDurationConfigMapper extends BaseMapper<ServiceDurationConfig> {
}
