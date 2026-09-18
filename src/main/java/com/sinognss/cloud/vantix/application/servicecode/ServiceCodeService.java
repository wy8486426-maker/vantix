package com.sinognss.cloud.vantix.application.servicecode;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.common.user.UserScope;
import com.sinognss.cloud.vantix.config.VantixProperties;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCode;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeStatus;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeQueryMapper;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class ServiceCodeService {
    private final ServiceCodeMapper serviceCodeMapper;
    private final ServiceCodeQueryMapper queryMapper;
    private final UserHolderBridge userHolder;
    private final Clock clock;
    private final VantixProperties properties;

    public ServiceCodeService(ServiceCodeMapper serviceCodeMapper,
                              ServiceCodeQueryMapper queryMapper,
                              UserHolderBridge userHolder,
                              Clock clock,
                              VantixProperties properties) {
        this.serviceCodeMapper = serviceCodeMapper;
        this.queryMapper = queryMapper;
        this.userHolder = userHolder;
        this.clock = clock;
        this.properties = properties;
    }

    public PageResponse<ServiceCodeView> page(long current, long size, ServiceCodeStatus status) {
        return page(new ServiceCodePageQuery(current, size, null, status, null,
                null, null, null, null));
    }

    public PageResponse<ServiceCodeView> page(long current, long size,
                                              ServiceCodeStatus status,
                                              DisplayStatus displayStatus) {
        return page(new ServiceCodePageQuery(current, size, null, status, displayStatus,
                null, null, null, null));
    }

    public PageResponse<ServiceCodeView> page(ServiceCodePageQuery request) {
        ServiceCodePageQuery query = validatePageQuery(request);
        UserScope scope = resolveScope(query.ownerCompanyId());
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime upcomingAt = now.plusDays(properties.getUpcomingDays());
        IPage<ServiceCodeQueryRow> page = queryMapper.pageForFrontend(
                new Page<>(query.current(), query.size()),
                query.keyword(), enumName(query.status()), enumName(query.displayStatus()), query.specCode(),
                query.durationDays(), query.sourceOrderNo(), query.ownerCompanyId(), scopedCompanyId(scope),
                now, upcomingAt);
        var records = page.getRecords().stream()
                .map(row -> ServiceCodeView.from(row, displayStatus(row.getStatus(), row.getExpireAt(), now)))
                .toList();
        return new PageResponse<>(records, page.getCurrent(), page.getSize(), page.getTotal(), page.getPages());
    }

    public ServiceCodeStatistics statistics(ServiceCodeStatisticsQuery request) {
        ServiceCodeStatisticsQuery query = validateStatisticsQuery(request);
        UserScope scope = resolveScope(query.ownerCompanyId());
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime upcomingAt = now.plusDays(properties.getUpcomingDays());
        ServiceCodeStatisticsRow row = queryMapper.statistics(
                query.keyword(), query.specCode(), query.durationDays(), query.sourceOrderNo(),
                query.ownerCompanyId(), scopedCompanyId(scope), now, upcomingAt);
        if (row == null) {
            return new ServiceCodeStatistics(0, 0, 0, 0, 0, 0);
        }
        return new ServiceCodeStatistics(value(row.getTotal()), value(row.getWaiting()), value(row.getExpiring()),
                value(row.getExpired()), value(row.getProcessing()), value(row.getConsumed()));
    }

    public ServiceCodeSpecStatistics specStatistics(Long requestedOwnerCompanyId) {
        Long ownerCompanyId = validateCompanyId(requestedOwnerCompanyId);
        UserScope scope = resolveScope(ownerCompanyId);
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime upcomingAt = now.plusDays(properties.getUpcomingDays());
        List<ServiceCodeSpecStatisticsRow> rows = queryMapper.specStatistics(
                ownerCompanyId, scopedCompanyId(scope), now, upcomingAt);
        if (rows == null || rows.isEmpty()) {
            return new ServiceCodeSpecStatistics(0, List.of());
        }
        List<ServiceCodeSpecStatisticsItem> items = rows.stream()
                .map(row -> new ServiceCodeSpecStatisticsItem(
                        row.getSpecCode(), row.getDisplayName(), row.getDurationDays(),
                        value(row.getTotal()), value(row.getWaiting()), value(row.getExpiring()),
                        value(row.getProcessing()), value(row.getConsumed()), value(row.getExpired())))
                .toList();
        long total = items.stream().mapToLong(ServiceCodeSpecStatisticsItem::total).sum();
        return new ServiceCodeSpecStatistics(total, items);
    }

    public ServiceCodeView get(Long id) {
        ServiceCodeQueryRow row = id == null ? null : queryMapper.detailForFrontend(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "服务码不存在: " + id);
        }
        assertCompanyAccess(row.getOwnerCompanyId());
        LocalDateTime now = LocalDateTime.now(clock);
        return ServiceCodeView.from(row, displayStatus(row.getStatus(), row.getExpireAt(), now));
    }

    public ServiceCode getRequired(Long id) {
        ServiceCode code = id == null ? null : serviceCodeMapper.selectById(id);
        if (code == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "服务码不存在: " + id);
        }
        return code;
    }

    DisplayStatus displayStatus(ServiceCode code, LocalDateTime now) {
        return displayStatus(code.getStatus(), code.getExpireAt(), now);
    }

    DisplayStatus displayStatus(ServiceCodeStatus status, LocalDateTime expireAt, LocalDateTime now) {
        if (status == ServiceCodeStatus.PROCESSING) {
            return DisplayStatus.PROCESSING;
        }
        if (status == ServiceCodeStatus.CONSUMED) {
            return DisplayStatus.CONSUMED;
        }
        if (expireAt == null || !expireAt.isAfter(now)) {
            return DisplayStatus.EXPIRED;
        }
        return expireAt.isAfter(now.plusDays(properties.getUpcomingDays()))
                ? DisplayStatus.WAITING : DisplayStatus.EXPIRING;
    }

    private void assertCompanyAccess(Long companyId) {
        if (!resolveScope(null).canAccessCompany(companyId)) {
            throw new BusinessException(ErrorCode.SERVICE_CODE_NOT_OWNED, "服务码不属于当前公司");
        }
    }

    private UserScope resolveScope(Long requestedCompanyId) {
        UserScope scope = userHolder.getUserScope();
        if (scope == null || !scope.isSupported()) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_USER_SCOPE, "当前用户数据范围不受支持");
        }
        if (!scope.isGlobal() && requestedCompanyId != null
                && !Objects.equals(requestedCompanyId, scope.companyId())) {
            throw new BusinessException(ErrorCode.SERVICE_CODE_NOT_OWNED, "无权访问指定公司的服务码");
        }
        return scope;
    }

    private Long scopedCompanyId(UserScope scope) {
        return scope.isGlobal() ? null : scope.companyId();
    }

    private ServiceCodePageQuery validatePageQuery(ServiceCodePageQuery request) {
        if (request == null || request.current() < 1 || request.size() < 1 || request.size() > 500) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "分页参数非法");
        }
        String keyword = normalize(request.keyword());
        if (keyword != null && keyword.length() > 100) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "keyword 长度不能超过 100");
        }
        Integer durationDays = validateDuration(request.durationDays());
        Long ownerCompanyId = validateCompanyId(request.ownerCompanyId());
        String specCode = normalize(request.specCode());
        String sourceOrderNo = normalize(request.sourceOrderNo());
        validateStatusFilters(request.status(), request.displayStatus());
        return new ServiceCodePageQuery(request.current(), request.size(), keyword, request.status(),
                request.displayStatus(), specCode, durationDays, sourceOrderNo, ownerCompanyId);
    }

    private ServiceCodeStatisticsQuery validateStatisticsQuery(ServiceCodeStatisticsQuery request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "统计查询参数不能为空");
        }
        String keyword = normalize(request.keyword());
        if (keyword != null && keyword.length() > 100) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "keyword 长度不能超过 100");
        }
        return new ServiceCodeStatisticsQuery(keyword, normalize(request.specCode()),
                validateDuration(request.durationDays()), normalize(request.sourceOrderNo()),
                validateCompanyId(request.ownerCompanyId()));
    }

    private Integer validateDuration(Integer durationDays) {
        if (durationDays != null && durationDays <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "durationDays 必须大于 0");
        }
        return durationDays;
    }

    private Long validateCompanyId(Long companyId) {
        if (companyId != null && companyId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "ownerCompanyId 必须大于 0");
        }
        return companyId;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private long value(Long value) {
        return value == null ? 0 : value;
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

}
