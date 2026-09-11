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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
        return mapper.selectList(Wrappers.<ServiceDurationConfig>lambdaQuery()
                        .orderByAsc(ServiceDurationConfig::getServiceType)
                        .orderByAsc(ServiceDurationConfig::getDurationValue))
                .stream().map(ServiceDurationConfigView::from).toList();
    }

    @Transactional
    public ServiceDurationConfigView create(ServiceDurationConfigCommand command) {
        validate(command);
        ensureUnique(command, null);
        OperatorIdentity operator = userHolder.getOperator();
        ServiceDurationConfig config = toEntity(command, operator);
        mapper.insert(config);
        log.info("Service duration config created, configId={}, operatorUserId={}", config.getId(), operator.userId());
        return ServiceDurationConfigView.from(config);
    }

    @Transactional
    public ServiceDurationConfigView update(Long id, ServiceDurationConfigCommand command) {
        validate(command);
        ServiceDurationConfig config = mapper.selectById(id);
        if (config == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "服务时长配置不存在: " + id);
        }
        ensureUnique(command, id);
        OperatorIdentity operator = userHolder.getOperator();
        config.setServiceType(command.serviceType());
        config.setDurationValue(command.durationValue());
        config.setDurationUnit(command.durationUnit());
        config.setCodeSilenceMonths(command.codeSilenceMonths());
        config.setEnabled(command.enabled());
        config.setRemark(command.remark());
        config.setUpdatedBy(operator.userId());
        mapper.updateById(config);
        log.info("Service duration config updated, configId={}, operatorUserId={}", id, operator.userId());
        return ServiceDurationConfigView.from(config);
    }

    private ServiceDurationConfig toEntity(ServiceDurationConfigCommand command, OperatorIdentity operator) {
        ServiceDurationConfig config = new ServiceDurationConfig();
        config.setServiceType(command.serviceType());
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

    private void ensureUnique(ServiceDurationConfigCommand command, Long id) {
        var query = Wrappers.<ServiceDurationConfig>lambdaQuery()
                .eq(ServiceDurationConfig::getServiceType, command.serviceType())
                .eq(ServiceDurationConfig::getDurationValue, command.durationValue())
                .eq(ServiceDurationConfig::getDurationUnit, command.durationUnit());
        if (id != null) {
            query.ne(ServiceDurationConfig::getId, id);
        }
        if (mapper.selectCount(query) > 0) {
            throw new BusinessException(ErrorCode.CONFIG_INVALID, "相同服务类型和时长配置已存在");
        }
    }
}
