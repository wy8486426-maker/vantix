package com.sinognss.cloud.vantix.application.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.OperatorIdentity;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.domain.config.SystemConfig;
import com.sinognss.cloud.vantix.infrastructure.mapper.SystemConfigMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.DealerCompanyMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemConfigService {
    public static final String SYSTEM_COMPANY_ID_KEY = "SYSTEM_COMPANY_ID";
    private static final Logger log = LoggerFactory.getLogger(SystemConfigService.class);
    private final SystemConfigMapper mapper;
    private final UserHolderBridge userHolder;
    private final DealerCompanyMapper companyMapper;

    public SystemConfigService(SystemConfigMapper mapper, UserHolderBridge userHolder,
                               DealerCompanyMapper companyMapper) {
        this.mapper = mapper;
        this.userHolder = userHolder;
        this.companyMapper = companyMapper;
    }

    public String get(String key) {
        SystemConfig config = mapper.selectOne(Wrappers.<SystemConfig>lambdaQuery()
                .eq(SystemConfig::getConfigKey, key));
        return config == null ? null : config.getConfigValue();
    }

    public Long getSystemCompanyId() {
        requireGlobalScope();
        return readSystemCompanyId();
    }

    Long requireConfiguredSystemCompanyId() {
        return readSystemCompanyId();
    }

    private Long readSystemCompanyId() {
        String value = get(SYSTEM_COMPANY_ID_KEY);
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.SYSTEM_COMPANY_NOT_CONFIGURED, "系统公司尚未配置");
        }
        try {
            long companyId = Long.parseLong(value);
            if (companyId <= 0) {
                throw new NumberFormatException("must be positive");
            }
            return companyId;
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.CONFIG_INVALID, "系统公司配置不是合法的公司 ID");
        }
    }

    @Transactional
    public Long updateSystemCompany(Long companyId) {
        requireGlobalScope();
        if (companyId == null || companyId <= 0) {
            throw new BusinessException(ErrorCode.CONFIG_INVALID, "系统公司 ID 非法");
        }
        if (companyMapper.selectCount(Wrappers.<com.sinognss.cloud.vantix.domain.company.DealerCompany>lambdaQuery()
                .eq(com.sinognss.cloud.vantix.domain.company.DealerCompany::getCompanyId, companyId)) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "系统公司不存在: " + companyId);
        }
        update(SYSTEM_COMPANY_ID_KEY, String.valueOf(companyId));
        return companyId;
    }

    public String update(String key, String value) {
        requireGlobalScope();
        if (key == null || key.isBlank() || value == null || value.isBlank()
                || key.codePoints().anyMatch(Character::isISOControl)
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new BusinessException(ErrorCode.CONFIG_INVALID, "系统配置参数不能为空或包含控制字符");
        }
        SystemConfig config = mapper.selectOne(Wrappers.<SystemConfig>lambdaQuery()
                .eq(SystemConfig::getConfigKey, key));
        OperatorIdentity operator = userHolder.getOperator();
        if (config == null) {
            config = new SystemConfig();
            config.setConfigKey(key);
            config.setConfigValue(value);
            config.setUpdatedBy(operator.userId());
            mapper.insert(config);
        } else {
            config.setConfigValue(value);
            config.setUpdatedBy(operator.userId());
            mapper.updateById(config);
        }
        log.info("System config updated, configKey={}, operatorUserId={}", key, operator.userId());
        return config.getConfigValue();
    }

    private void requireGlobalScope() {
        if (!userHolder.getUserScope().isGlobal()) {
            throw new BusinessException(ErrorCode.GLOBAL_SCOPE_REQUIRED, "配置中心仅允许 GLOBAL 数据范围访问");
        }
    }
}
