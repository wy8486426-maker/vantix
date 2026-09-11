package com.sinognss.cloud.vantix.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ServiceAccountMapper extends BaseMapper<ServiceAccount> {
}
