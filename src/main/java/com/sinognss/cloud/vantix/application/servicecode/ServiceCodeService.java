package com.sinognss.cloud.vantix.application.servicecode;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.common.user.UserScope;
import com.sinognss.cloud.vantix.domain.config.ServiceDurationConfig;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCode;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeStatus;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceDurationConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class ServiceCodeService {
    private final ServiceCodeMapper serviceCodeMapper;
    private final ServiceDurationConfigMapper durationConfigMapper;
    private final UserHolderBridge userHolder;
    private final Clock clock;
    private final com.sinognss.cloud.vantix.config.VantixProperties properties;

    public ServiceCodeService(ServiceCodeMapper serviceCodeMapper,
                              ServiceDurationConfigMapper durationConfigMapper,
                              UserHolderBridge userHolder,
                              Clock clock,
                              com.sinognss.cloud.vantix.config.VantixProperties properties) {
        this.serviceCodeMapper = serviceCodeMapper;
        this.durationConfigMapper = durationConfigMapper;
        this.userHolder = userHolder;
        this.clock = clock;
        this.properties = properties;
    }

    public PageResponse<ServiceCodeView> page(long current, long size, ServiceCodeStatus status) {
        if (current < 1 || size < 1 || size > 500) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "分页参数非法");
        }
        UserScope scope = userHolder.getUserScope();
        var query = Wrappers.<ServiceCode>lambdaQuery()
                .eq(status != null, ServiceCode::getStatus, status)
                .orderByAsc(ServiceCode::getId);
        if (!scope.isGlobal()) {
            query.eq(ServiceCode::getOwnerCompanyId, scope.companyId());
        }
        IPage<ServiceCode> page = serviceCodeMapper.selectPage(new Page<>(current, size), query);
        LocalDateTime now = LocalDateTime.now(clock);
        var records = page.getRecords().stream()
                .map(code -> ServiceCodeView.from(code, displayStatus(code, now)))
                .toList();
        return new PageResponse<>(records, page.getCurrent(), page.getSize(), page.getTotal(), page.getPages());
    }

    public ServiceCodeView get(Long id) {
        ServiceCode code = getRequired(id);
        assertCompanyAccess(code.getOwnerCompanyId());
        return ServiceCodeView.from(code, displayStatus(code, LocalDateTime.now(clock)));
    }

    @Transactional
    public ServiceCode create(CreateServiceCodeCommand command) {
        if (command == null || command.code() == null || command.code().isBlank()
                || command.ownerCompanyId() == null || command.durationConfigId() == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "服务码创建参数非法");
        }
        ServiceDurationConfig config = durationConfigMapper.selectById(command.durationConfigId());
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            throw new BusinessException(ErrorCode.CONFIG_INVALID, "服务时长配置不存在或已停用");
        }
        LocalDateTime createdAt = LocalDateTime.now(clock);
        ServiceCode code = new ServiceCode();
        code.setCode(command.code());
        code.setSourceOrderId(command.sourceOrderId());
        code.setSourceOrderNo(command.sourceOrderNo());
        code.setOwnerCompanyId(command.ownerCompanyId());
        code.setServiceType(config.getServiceType());
        code.setDurationValue(config.getDurationValue());
        code.setDurationUnit(config.getDurationUnit().name());
        code.setCodeSilenceMonths(config.getCodeSilenceMonths());
        code.setExpireAt(createdAt.plusMonths(config.getCodeSilenceMonths()));
        code.setStatus(ServiceCodeStatus.PENDING);
        code.setVersion(0L);
        code.setCreatedAt(createdAt);
        code.setUpdatedAt(createdAt);
        serviceCodeMapper.insert(code);
        return code;
    }

    public ServiceCode getRequired(Long id) {
        ServiceCode code = id == null ? null : serviceCodeMapper.selectById(id);
        if (code == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "服务码不存在: " + id);
        }
        return code;
    }

    DisplayStatus displayStatus(ServiceCode code, LocalDateTime now) {
        if (code.getStatus() == ServiceCodeStatus.PROCESSING) {
            return DisplayStatus.PROCESSING;
        }
        if (code.getStatus() == ServiceCodeStatus.CONSUMED) {
            return DisplayStatus.CONSUMED;
        }
        if (!code.getExpireAt().isAfter(now)) {
            return DisplayStatus.EXPIRED;
        }
        return code.getExpireAt().isAfter(now.plusDays(properties.getUpcomingDays()))
                ? DisplayStatus.WAITING : DisplayStatus.EXPIRING;
    }

    private void assertCompanyAccess(Long companyId) {
        if (!userHolder.getUserScope().canAccessCompany(companyId)) {
            throw new BusinessException(ErrorCode.SERVICE_CODE_NOT_OWNED, "服务码不属于当前公司");
        }
    }
}
