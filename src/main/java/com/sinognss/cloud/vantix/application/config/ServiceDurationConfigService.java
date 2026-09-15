package com.sinognss.cloud.vantix.application.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.OperatorIdentity;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.domain.config.ServiceDurationConfig;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceDurationConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ServiceDurationConfigService {
    private static final Logger log = LoggerFactory.getLogger(ServiceDurationConfigService.class);
    private final ServiceDurationConfigMapper mapper;
    private final UserHolderBridge userHolder;

    public ServiceDurationConfigService(ServiceDurationConfigMapper mapper, UserHolderBridge userHolder) {
        this.mapper = mapper;
        this.userHolder = userHolder;
    }

    public List<ServiceDurationConfigView> list() {
        requireGlobalScope();
        return mapper.selectList(Wrappers.<ServiceDurationConfig>lambdaQuery()
                        .orderByAsc(ServiceDurationConfig::getServiceType)
                        .orderByAsc(ServiceDurationConfig::getDurationDays)
                        .orderByAsc(ServiceDurationConfig::getDisplayName))
                .stream().map(ServiceDurationConfigView::from).toList();
    }

    public ServiceDurationConfigView get(Long id) {
        requireGlobalScope();
        ServiceDurationConfig config = id == null ? null : mapper.selectById(id);
        if (config == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "服务规格不存在: " + id);
        }
        return ServiceDurationConfigView.from(config);
    }

    @Transactional
    public ServiceDurationConfigView create(CreateServiceDurationConfigCommand command) {
        requireGlobalScope();
        validate(command);
        String displayName = normalizeText(command.displayName(), 128, "displayName");
        String serviceType = normalizeText(command.serviceType(), 64, "serviceType");
        String remark = normalizeOptionalText(command.remark(), 512, "remark");
        ensureUniqueDisplayName(displayName);
        OperatorIdentity operator = userHolder.getOperator();
        ServiceDurationConfig config = new ServiceDurationConfig();
        config.setSpecCode(createOpaqueSpecCode());
        config.setDisplayName(displayName);
        config.setServiceType(serviceType);
        config.setDurationDays(command.durationDays());
        config.setCodeSilenceDays(command.codeSilenceDays());
        config.setAccountSilenceDays(command.accountSilenceDays());
        config.setEnabled(command.enabled());
        config.setRemark(remark);
        config.setCreatedBy(operator.userId());
        config.setUpdatedBy(operator.userId());
        try {
            mapper.insert(config);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CONFIG_INVALID, "服务规格编码或展示名称已存在");
        }
        log.info("Service duration config created, configId={}, specCode={}, operatorUserId={}",
                config.getId(), config.getSpecCode(), operator.userId());
        return ServiceDurationConfigView.from(config);
    }

    @Transactional
    public ServiceDurationConfigView update(Long id, UpdateServiceDurationConfigCommand command) {
        requireGlobalScope();
        validate(command);
        ServiceDurationConfig config = mapper.selectById(id);
        if (config == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "服务规格不存在: " + id);
        }
        String displayName = normalizeText(command.displayName(), 128, "displayName");
        String remark = normalizeOptionalText(command.remark(), 512, "remark");
        ensureUniqueDisplayNameForUpdate(id, displayName);
        OperatorIdentity operator = userHolder.getOperator();
        config.setDisplayName(displayName);
        config.setCodeSilenceDays(command.codeSilenceDays());
        config.setAccountSilenceDays(command.accountSilenceDays());
        config.setEnabled(command.enabled());
        config.setRemark(remark);
        config.setUpdatedBy(operator.userId());
        mapper.updateById(config);
        log.info("Service duration config updated, configId={}, specCode={}, operatorUserId={}",
                id, config.getSpecCode(), operator.userId());
        return ServiceDurationConfigView.from(config);
    }

    private String createOpaqueSpecCode() {
        return "SC" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toUpperCase(java.util.Locale.ROOT);
    }

    private void validate(CreateServiceDurationConfigCommand command) {
        if (command == null || command.displayName() == null || command.displayName().isBlank()
                || command.displayName().trim().length() > 128 || hasControl(command.displayName())
                || command.serviceType() == null || command.serviceType().isBlank()
                || command.serviceType().trim().length() > 64 || hasControl(command.serviceType())
                || command.durationDays() == null || command.durationDays() <= 0
                || command.codeSilenceDays() == null || command.codeSilenceDays() < 0
                || command.accountSilenceDays() == null || command.accountSilenceDays() < 0
                || command.enabled() == null || invalidOptionalText(command.remark(), 512)) {
            throw new BusinessException(ErrorCode.CONFIG_INVALID, "服务规格配置参数非法");
        }
    }

    private void validate(UpdateServiceDurationConfigCommand command) {
        if (command == null || command.displayName() == null || command.displayName().isBlank()
                || command.displayName().trim().length() > 128 || hasControl(command.displayName())
                || command.codeSilenceDays() == null || command.codeSilenceDays() < 0
                || command.accountSilenceDays() == null || command.accountSilenceDays() < 0
                || command.enabled() == null || invalidOptionalText(command.remark(), 512)) {
            throw new BusinessException(ErrorCode.CONFIG_INVALID, "服务规格配置参数非法");
        }
    }

    private void ensureUniqueDisplayName(String displayName) {
        if (mapper.selectCount(Wrappers.<ServiceDurationConfig>lambdaQuery()
                .eq(ServiceDurationConfig::getDisplayName, displayName)) > 0) {
            throw new BusinessException(ErrorCode.CONFIG_INVALID, "展示名称已存在");
        }
    }

    private void ensureUniqueDisplayNameForUpdate(Long id, String displayName) {
        if (mapper.selectCount(Wrappers.<ServiceDurationConfig>lambdaQuery()
                .eq(ServiceDurationConfig::getDisplayName, displayName)
                .ne(ServiceDurationConfig::getId, id)) > 0) {
            throw new BusinessException(ErrorCode.CONFIG_INVALID, "展示名称已存在");
        }
    }

    private void requireGlobalScope() {
        if (!userHolder.getUserScope().isGlobal()) {
            throw new BusinessException(ErrorCode.GLOBAL_SCOPE_REQUIRED, "配置中心仅允许 GLOBAL 数据范围访问");
        }
    }

    private String normalizeText(String value, int maxLength, String field) {
        String result = value == null ? null : value.trim();
        if (result == null || result.isEmpty() || result.length() > maxLength || hasControl(result)) {
            throw new BusinessException(ErrorCode.CONFIG_INVALID, field + " 参数非法");
        }
        return result;
    }

    private String normalizeOptionalText(String value, int maxLength, String field) {
        if (value == null) return null;
        String result = value.trim();
        if (result.isEmpty()) return null;
        if (result.length() > maxLength || hasControl(result)) {
            throw new BusinessException(ErrorCode.CONFIG_INVALID, field + " 参数非法");
        }
        return result;
    }

    private boolean invalidOptionalText(String value, int maxLength) {
        return value != null && (value.trim().length() > maxLength || hasControl(value));
    }

    private boolean hasControl(String value) {
        return value != null && value.codePoints().anyMatch(Character::isISOControl);
    }
}
