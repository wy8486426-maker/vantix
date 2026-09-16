package com.sinognss.cloud.vantix.application.account;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sinognss.cloud.vantix.application.servicecode.PageResponse;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.common.user.UserScope;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountQueryMapper;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class ServiceAccountQueryService {
    private static final Set<String> STATUS_FILTERS = Set.of(
            "WAITING_ACTIVATION", "ACTIVE", "EXPIRED", "DISABLED");

    private final ServiceAccountQueryMapper mapper;
    private final UserHolderBridge userHolder;

    public ServiceAccountQueryService(ServiceAccountQueryMapper mapper, UserHolderBridge userHolder) {
        this.mapper = mapper;
        this.userHolder = userHolder;
    }

    public PageResponse<ServiceAccountView> page(ServiceAccountPageQuery input) {
        ServiceAccountPageQuery query = validatePage(input);
        Scope scope = resolveScope(query.ownerCompanyId(), query.assignedUserId());
        IPage<ServiceAccountQueryRow> page = mapper.pageForFrontend(new Page<>(query.current(), query.size()),
                query.keyword(), query.status(), query.specCode(), query.durationDays(), query.ownerCompanyId(),
                query.assignedUserId(), scope.companyId(), scope.assignedUserId());
        return new PageResponse<>(page.getRecords().stream().map(ServiceAccountView::from).toList(),
                page.getCurrent(), page.getSize(), page.getTotal(), page.getPages());
    }

    public ServiceAccountStatistics statistics(ServiceAccountStatisticsQuery input) {
        ServiceAccountStatisticsQuery query = validateStatistics(input);
        Scope scope = resolveScope(query.ownerCompanyId(), query.assignedUserId());
        ServiceAccountStatisticsRow row = mapper.statistics(query.keyword(), query.specCode(), query.durationDays(),
                query.ownerCompanyId(), query.assignedUserId(), scope.companyId(), scope.assignedUserId());
        if (row == null) return new ServiceAccountStatistics(0, 0, 0, 0, 0);
        return new ServiceAccountStatistics(value(row.getTotal()), value(row.getWaiting()), value(row.getActive()),
                value(row.getExpired()), value(row.getDisabled()));
    }

    public ServiceAccountView get(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "账号 ID 非法");
        }
        Scope scope = resolveScope(null, null);
        ServiceAccountQueryRow row = mapper.detailForFrontend(id, scope.companyId(), scope.assignedUserId());
        if (row == null) throw new BusinessException(ErrorCode.NOT_FOUND, "服务账号不存在: " + id);
        return ServiceAccountView.from(row);
    }

    private ServiceAccountPageQuery validatePage(ServiceAccountPageQuery input) {
        if (input == null || input.current() < 1 || input.size() < 1 || input.size() > 500) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "分页参数非法");
        }
        return new ServiceAccountPageQuery(input.current(), input.size(), normalize(input.keyword(), 100, "keyword"),
                normalizeStatus(input.status()), normalize(input.specCode(), 32, "specCode"),
                validatePositive(input.durationDays(), "durationDays"), validatePositive(input.ownerCompanyId(),
                "ownerCompanyId"), validatePositive(input.assignedUserId(), "assignedUserId"));
    }

    private ServiceAccountStatisticsQuery validateStatistics(ServiceAccountStatisticsQuery input) {
        if (input == null) throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "统计查询参数不能为空");
        return new ServiceAccountStatisticsQuery(normalize(input.keyword(), 100, "keyword"),
                normalize(input.specCode(), 32, "specCode"), validatePositive(input.durationDays(), "durationDays"),
                validatePositive(input.ownerCompanyId(), "ownerCompanyId"),
                validatePositive(input.assignedUserId(), "assignedUserId"));
    }

    private Scope resolveScope(Long requestedCompanyId, Long requestedAssignedUserId) {
        UserScope scope = userHolder.getUserScope();
        if (scope == null || !scope.isSupported()) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_USER_SCOPE, "当前用户数据范围不受支持");
        }
        if (!scope.isGlobal() && requestedCompanyId != null
                && !scope.companyId().equals(requestedCompanyId)) {
            throw new BusinessException(ErrorCode.SERVICE_CODE_NOT_OWNED, "无权查询指定公司的服务账号");
        }
        if (scope.type() == UserScope.Type.PERSONAL && requestedAssignedUserId != null
                && !scope.userId().equals(requestedAssignedUserId)) {
            throw new BusinessException(ErrorCode.SERVICE_CODE_NOT_OWNED, "无权查询指定用户的服务账号");
        }
        return new Scope(scope.isGlobal() ? null : scope.companyId(),
                scope.type() == UserScope.Type.PERSONAL ? scope.userId() : null);
    }

    private String normalizeStatus(String value) {
        String normalized = normalize(value, 32, "status");
        if (normalized != null && !STATUS_FILTERS.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "status 仅支持 WAITING_ACTIVATION、ACTIVE、EXPIRED、DISABLED");
        }
        return normalized;
    }

    private String normalize(String value, int maxLength, String name) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.isEmpty()) return null;
        if (normalized.length() > maxLength || normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, name + " 参数非法");
        }
        return normalized;
    }

    private <T extends Number> T validatePositive(T value, String name) {
        if (value != null && value.longValue() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, name + " 必须大于 0");
        }
        return value;
    }

    private long value(Long value) { return value == null ? 0 : value; }

    private record Scope(Long companyId, Long assignedUserId) { }
}
