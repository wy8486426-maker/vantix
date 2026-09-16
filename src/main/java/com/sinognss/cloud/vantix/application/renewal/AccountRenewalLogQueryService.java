package com.sinognss.cloud.vantix.application.renewal;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sinognss.cloud.vantix.application.servicecode.PageResponse;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.common.user.UserScope;
import com.sinognss.cloud.vantix.infrastructure.mapper.AccountRenewalLogQueryMapper;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class AccountRenewalLogQueryService {
    private static final Set<String> STATUSES = Set.of("PROCESSING", "COMPLETED", "FAILED", "MANUAL_REVIEW");

    private final AccountRenewalLogQueryMapper mapper;
    private final UserHolderBridge userHolder;

    public AccountRenewalLogQueryService(AccountRenewalLogQueryMapper mapper, UserHolderBridge userHolder) {
        this.mapper = mapper;
        this.userHolder = userHolder;
    }

    public PageResponse<AccountRenewalLogView> page(AccountRenewalLogPageQuery input) {
        AccountRenewalLogPageQuery query = validate(input);
        Scope scope = resolveScope(query.ownerCompanyId());
        IPage<AccountRenewalLogQueryRow> page = mapper.pageForFrontend(new Page<>(query.current(), query.size()),
                query.keyword(), query.status(), query.ownerCompanyId(), query.createdFrom(), query.createdTo(),
                scope.companyId(), scope.assignedUserId());
        return new PageResponse<>(page.getRecords().stream().map(AccountRenewalLogView::from).toList(),
                page.getCurrent(), page.getSize(), page.getTotal(), page.getPages());
    }

    /** Used by the existing requestId detail endpoint when a local-only detail is needed. */
    public AccountRenewalLogView detail(String inputRequestId) {
        String requestId = normalize(inputRequestId, 128, "requestId");
        Scope scope = resolveScope(null);
        AccountRenewalLogQueryRow row = mapper.detailForFrontend(requestId, scope.companyId(), scope.assignedUserId());
        if (row == null) throw new BusinessException(ErrorCode.NOT_FOUND, "续期请求不存在: " + requestId);
        return AccountRenewalLogView.from(row);
    }

    private Scope resolveScope(Long requestedCompanyId) {
        UserScope scope = userHolder.getUserScope();
        if (scope == null || !scope.isSupported()) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_USER_SCOPE, "当前用户数据范围不受支持");
        }
        if (!scope.isGlobal() && requestedCompanyId != null
                && !scope.companyId().equals(requestedCompanyId)) {
            throw new BusinessException(ErrorCode.SERVICE_CODE_NOT_OWNED, "无权查询指定公司的续期记录");
        }
        return new Scope(scope.isGlobal() ? null : scope.companyId(),
                scope.type() == UserScope.Type.PERSONAL ? scope.userId() : null);
    }

    private AccountRenewalLogPageQuery validate(AccountRenewalLogPageQuery input) {
        if (input == null || input.current() < 1 || input.size() < 1 || input.size() > 500) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "分页参数非法");
        }
        if (input.ownerCompanyId() != null && input.ownerCompanyId() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "ownerCompanyId 必须大于 0");
        }
        if (input.createdFrom() != null && input.createdTo() != null
                && input.createdFrom().isAfter(input.createdTo())) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "createdFrom 不能晚于 createdTo");
        }
        String status = normalize(input.status(), 32, "status");
        if (status != null && !STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "status 仅支持 PROCESSING、COMPLETED、FAILED、MANUAL_REVIEW");
        }
        return new AccountRenewalLogPageQuery(input.current(), input.size(), normalize(input.keyword(), 100, "keyword"),
                status, input.ownerCompanyId(), input.createdFrom(), input.createdTo());
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

    private record Scope(Long companyId, Long assignedUserId) { }
}
