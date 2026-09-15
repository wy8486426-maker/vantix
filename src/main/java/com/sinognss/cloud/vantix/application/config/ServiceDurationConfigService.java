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
    private static final String IDENTITY_IMMUTABLE_MESSAGE =
            "服务规格创建后不可修改，请停用旧规格并新建规格";

    private final ServiceDurationConfigMapper mapper;
    private final UserHolderBridge userHolder;

    public ServiceDurationConfigService(ServiceDurationConfigMapper mapper, UserHolderBridge userHolder) {
        this.mapper = mapper;
        this.userHolder = userHolder;
    }

    public List<ServiceDurationConfigView> list() {
        return mapper.selectList(Wrappers.<ServiceDurationConfig>lambdaQuery()
                        .orderByAsc(ServiceDurationConfig::getServiceType)
                        .orderByAsc(ServiceDurationConfig::getDurationDays)
                        .orderByAsc(ServiceDurationConfig::getDisplayName))
                .stream().map(ServiceDurationConfigView::from).toList();
    }

    @Transactional
    public ServiceDurationConfigView create(ServiceDurationConfigCommand command) {
        validate(command);
        ensureUniqueDisplayName(command.displayName().trim());
        OperatorIdentity operator = userHolder.getOperator();
        ServiceDurationConfig config = new ServiceDurationConfig();
        config.setSpecCode(createOpaqueSpecCode());
        config.setDisplayName(command.displayName().trim());
        config.setServiceType(command.serviceType().trim());
        config.setDurationDays(command.durationDays());
        config.setCodeSilenceDays(command.codeSilenceDays());
        config.setAccountSilenceDays(command.accountSilenceDays());
        config.setEnabled(command.enabled());
        config.setRemark(command.remark());
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
    public ServiceDurationConfigView update(Long id, ServiceDurationConfigCommand command) {
        validate(command);
        ServiceDurationConfig config = mapper.selectById(id);
        if (config == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "服务规格不存在: " + id);
        }
        ensureIdentityUnchanged(config, command);
        ensureUniqueDisplayNameForUpdate(id, command.displayName().trim());
        OperatorIdentity operator = userHolder.getOperator();
        config.setDisplayName(command.displayName().trim());
        config.setCodeSilenceDays(command.codeSilenceDays());
        config.setAccountSilenceDays(command.accountSilenceDays());
        config.setEnabled(command.enabled());
        config.setRemark(command.remark());
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

    private void validate(ServiceDurationConfigCommand command) {
        if (command == null || command.displayName() == null || command.displayName().isBlank()
                || command.displayName().trim().length() > 128
                || command.serviceType() == null || command.serviceType().isBlank()
                || command.serviceType().trim().length() > 64
                || command.durationDays() == null || command.durationDays() <= 0
                || command.codeSilenceDays() == null || command.codeSilenceDays() < 0
                || command.accountSilenceDays() == null || command.accountSilenceDays() < 0
                || command.enabled() == null) {
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

    private void ensureIdentityUnchanged(ServiceDurationConfig config, ServiceDurationConfigCommand command) {
        if (!java.util.Objects.equals(config.getServiceType(), command.serviceType().trim())
                || !java.util.Objects.equals(config.getDurationDays(), command.durationDays())) {
            throw new BusinessException(ErrorCode.CONFIG_INVALID, IDENTITY_IMMUTABLE_MESSAGE);
        }
    }
}
