package com.sinognss.cloud.vantix.application.servicecode;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sinognss.cloud.vantix.application.servicecode.generation.ServiceCodeBatchView;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.common.user.UserScope;
import com.sinognss.cloud.vantix.config.VantixProperties;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCode;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeGenerateBatch;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeStatus;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeGenerateBatchMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeMapper;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ServiceCodeService {
    private final ServiceCodeMapper serviceCodeMapper;
    private final ServiceCodeGenerateBatchMapper batchMapper;
    private final UserHolderBridge userHolder;
    private final Clock clock;
    private final VantixProperties properties;

    public ServiceCodeService(ServiceCodeMapper serviceCodeMapper,
                              ServiceCodeGenerateBatchMapper batchMapper,
                              UserHolderBridge userHolder,
                              Clock clock,
                              VantixProperties properties) {
        this.serviceCodeMapper = serviceCodeMapper;
        this.batchMapper = batchMapper;
        this.userHolder = userHolder;
        this.clock = clock;
        this.properties = properties;
    }

    public PageResponse<ServiceCodeView> page(long current, long size, ServiceCodeStatus status) {
        return page(current, size, status, null);
    }

    public PageResponse<ServiceCodeView> page(long current, long size,
                                              ServiceCodeStatus status,
                                              DisplayStatus displayStatus) {
        if (current < 1 || size < 1 || size > 500) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "分页参数非法");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        validateStatusFilters(status, displayStatus);
        UserScope scope = userHolder.getUserScope();
        var query = Wrappers.<ServiceCode>lambdaQuery();
        if (displayStatus == null) {
            query.eq(status != null, ServiceCode::getStatus, status);
        } else {
            applyDisplayStatus(query, displayStatus, now);
        }
        query.orderByAsc(ServiceCode::getId);
        if (!scope.isGlobal()) {
            query.eq(ServiceCode::getOwnerCompanyId, scope.companyId());
        }
        IPage<ServiceCode> page = serviceCodeMapper.selectPage(new Page<>(current, size), query);
        Map<Long, ServiceCodeGenerateBatch> batches = page.getRecords().stream()
                .map(ServiceCode::getGenerateBatchId).filter(java.util.Objects::nonNull).distinct()
                .collect(Collectors.collectingAndThen(Collectors.toList(), ids -> ids.isEmpty()
                        ? Map.of()
                        : batchMapper.selectList(Wrappers.<ServiceCodeGenerateBatch>lambdaQuery()
                                .in(ServiceCodeGenerateBatch::getId, ids))
                        .stream().collect(Collectors.toMap(ServiceCodeGenerateBatch::getId, Function.identity()))));
        var records = page.getRecords().stream()
                .map(code -> ServiceCodeView.from(code, displayStatus(code, now),
                        batches.get(code.getGenerateBatchId())))
                .toList();
        return new PageResponse<>(records, page.getCurrent(), page.getSize(), page.getTotal(), page.getPages());
    }

    public ServiceCodeView get(Long id) {
        ServiceCode code = getRequired(id);
        assertCompanyAccess(code.getOwnerCompanyId());
        ServiceCodeGenerateBatch batch = code.getGenerateBatchId() == null
                ? null : batchMapper.selectById(code.getGenerateBatchId());
        return ServiceCodeView.from(code, displayStatus(code, LocalDateTime.now(clock)), batch);
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
        if (code.getExpireAt() == null || !code.getExpireAt().isAfter(now)) {
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

    private void validateStatusFilters(ServiceCodeStatus status, DisplayStatus displayStatus) {
        if (status == null || displayStatus == null) {
            return;
        }
        ServiceCodeStatus displayDbStatus = switch (displayStatus) {
            case PROCESSING -> ServiceCodeStatus.PROCESSING;
            case CONSUMED -> ServiceCodeStatus.CONSUMED;
            case WAITING, EXPIRING, EXPIRED -> ServiceCodeStatus.PENDING;
        };
        if (status != displayDbStatus) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "status 与 displayStatus 不一致");
        }
    }

    private void applyDisplayStatus(
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ServiceCode> query,
            DisplayStatus displayStatus, LocalDateTime now) {
        LocalDateTime upcomingAt = now.plusDays(properties.getUpcomingDays());
        switch (displayStatus) {
            case WAITING -> query.eq(ServiceCode::getStatus, ServiceCodeStatus.PENDING)
                    .gt(ServiceCode::getExpireAt, upcomingAt);
            case EXPIRING -> query.eq(ServiceCode::getStatus, ServiceCodeStatus.PENDING)
                    .gt(ServiceCode::getExpireAt, now)
                    .le(ServiceCode::getExpireAt, upcomingAt);
            case EXPIRED -> query.eq(ServiceCode::getStatus, ServiceCodeStatus.PENDING)
                    .le(ServiceCode::getExpireAt, now);
            case PROCESSING -> query.eq(ServiceCode::getStatus, ServiceCodeStatus.PROCESSING);
            case CONSUMED -> query.eq(ServiceCode::getStatus, ServiceCodeStatus.CONSUMED);
        }
    }
}
