package com.sinognss.cloud.vantix.application.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.OperatorIdentity;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.domain.config.DurationUnit;
import com.sinognss.cloud.vantix.domain.config.ServiceDurationConfig;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceDurationConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ServiceDurationConfigService {
    private static final Logger log = LoggerFactory.getLogger(ServiceDurationConfigService.class);
    private static final String IDENTITY_IMMUTABLE_MESSAGE =
            "服务时长创建后不可修改，请停用旧规格并新建规格";

    private final ServiceDurationConfigMapper mapper;
    private final UserHolderBridge userHolder;

    public ServiceDurationConfigService(ServiceDurationConfigMapper mapper, UserHolderBridge userHolder) {
        this.mapper = mapper;
        this.userHolder = userHolder;
    }

    public List<ServiceDurationConfigView> list() {
        return mapper.selectList(Wrappers.<ServiceDurationConfig>lambdaQuery()
                        .orderByAsc(ServiceDurationConfig::getServiceType)
                        .orderByAsc(ServiceDurationConfig::getDurationValue))
                .stream().map(ServiceDurationConfigView::from).toList();
    }

    @Transactional
    public ServiceDurationConfigView create(ServiceDurationConfigCommand command) {
        validate(command);
        ensureUnique(command);
        OperatorIdentity operator = userHolder.getOperator();
        ServiceDurationConfig config = toEntity(command, operator);
        config.setSpecCode(createSpecCode(command));
        try {
            mapper.insert(config);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CONFIG_INVALID, "相同服务时长规格已存在");
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
            throw new BusinessException(ErrorCode.NOT_FOUND, "服务时长配置不存在: " + id);
        }
        ensureIdentityUnchanged(config, command);
        OperatorIdentity operator = userHolder.getOperator();
        config.setCodeSilenceMonths(command.codeSilenceMonths());
        config.setEnabled(command.enabled());
        config.setRemark(command.remark());
        config.setUpdatedBy(operator.userId());
        mapper.updateById(config);
        log.info("Service duration config updated, configId={}, specCode={}, operatorUserId={}",
                id, config.getSpecCode(), operator.userId());
        return ServiceDurationConfigView.from(config);
    }

    private String createSpecCode(ServiceDurationConfigCommand command) {
        String prefix = switch (command.durationUnit()) {
            case DAY -> "D";
            case WEEK -> "W";
            case MONTH -> "M";
            case YEAR -> "Y";
        };
        return prefix + command.durationValue();
    }

    private ServiceDurationConfig toEntity(ServiceDurationConfigCommand command, OperatorIdentity operator) {
        ServiceDurationConfig config = new ServiceDurationConfig();
        config.setServiceType(command.serviceType().trim());
        config.setDurationValue(command.durationValue());
        config.setDurationUnit(command.durationUnit());
        config.setCodeSilenceMonths(command.codeSilenceMonths());
        config.setEnabled(command.enabled());
        config.setRemark(command.remark());
        config.setCreatedBy(operator.userId());
        config.setUpdatedBy(operator.userId());
        return config;
    }

    private void validate(ServiceDurationConfigCommand command) {
        if (command == null || command.serviceType() == null || command.serviceType().isBlank()
                || command.durationValue() == null || command.durationValue() <= 0
                || command.durationUnit() == null || command.codeSilenceMonths() == null
                || command.codeSilenceMonths() < 0 || command.enabled() == null) {
            throw new BusinessException(ErrorCode.CONFIG_INVALID, "服务时长配置参数非法");
        }
    }

    private void ensureUnique(ServiceDurationConfigCommand command) {
        long count = mapper.selectCount(Wrappers.<ServiceDurationConfig>lambdaQuery()
                .eq(ServiceDurationConfig::getDurationValue, command.durationValue())
                .eq(ServiceDurationConfig::getDurationUnit, command.durationUnit()));
        if (count > 0) {
            throw new BusinessException(ErrorCode.CONFIG_INVALID, "相同服务时长规格已存在");
        }
    }

    private void ensureIdentityUnchanged(ServiceDurationConfig config, ServiceDurationConfigCommand command) {
        if (!config.getServiceType().equals(command.serviceType().trim())
                || !config.getDurationValue().equals(command.durationValue())
                || !config.getDurationUnit().equals(command.durationUnit())) {
            throw new BusinessException(ErrorCode.CONFIG_INVALID, IDENTITY_IMMUTABLE_MESSAGE);
        }
    }
}
