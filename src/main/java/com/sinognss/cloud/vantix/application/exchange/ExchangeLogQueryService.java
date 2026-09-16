package com.sinognss.cloud.vantix.application.exchange;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sinognss.cloud.vantix.application.servicecode.PageResponse;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.common.user.UserScope;
import com.sinognss.cloud.vantix.infrastructure.mapper.ExchangeLogQueryMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ExchangeLogQueryService {
    private final ExchangeLogQueryMapper mapper;
    private final UserHolderBridge userHolder;

    public ExchangeLogQueryService(ExchangeLogQueryMapper mapper, UserHolderBridge userHolder) {
        this.mapper = mapper;
        this.userHolder = userHolder;
    }

    public PageResponse<ExchangeLogView> page(ExchangeLogPageQuery input) {
        ExchangeLogPageQuery query = validate(input);
        Long scopeCompanyId = scopeCompanyId(query.ownerCompanyId());
        IPage<ExchangeLogQueryRow> page = mapper.pageForFrontend(new Page<>(query.current(), query.size()),
                query.keyword(), name(query.status()), query.specCode(), query.ownerCompanyId(),
                query.createdFrom(), query.createdTo(), scopeCompanyId);
        return new PageResponse<>(page.getRecords().stream().map(ExchangeLogView::from).toList(),
                page.getCurrent(), page.getSize(), page.getTotal(), page.getPages());
    }

    public ExchangeLogDetailView detail(String inputRequestId) {
        String requestId = normalize(inputRequestId, 128, "requestId");
        Long scopeCompanyId = scopeCompanyId(null);
        ExchangeLogQueryRow batch = mapper.detail(requestId, scopeCompanyId);
        if (batch == null) throw new BusinessException(ErrorCode.NOT_FOUND, "兑换请求不存在: " + requestId);
        return new ExchangeLogDetailView(ExchangeLogView.from(batch), mapper.detailItems(requestId, scopeCompanyId)
                .stream().map(ExchangeLogItemView::from).toList());
    }

    private Long scopeCompanyId(Long requestedCompanyId) {
        UserScope scope = userHolder.getUserScope();
        if (scope == null || !scope.isSupported()) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_USER_SCOPE, "当前用户数据范围不受支持");
        }
        if (!scope.isGlobal() && requestedCompanyId != null
                && !scope.companyId().equals(requestedCompanyId)) {
            throw new BusinessException(ErrorCode.SERVICE_CODE_NOT_OWNED, "无权查询指定公司的兑换记录");
        }
        return scope.isGlobal() ? null : scope.companyId();
    }

    private ExchangeLogPageQuery validate(ExchangeLogPageQuery input) {
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
        return new ExchangeLogPageQuery(input.current(), input.size(), normalize(input.keyword(), 100, "keyword"),
                input.status(), normalize(input.specCode(), 32, "specCode"), input.ownerCompanyId(),
                input.createdFrom(), input.createdTo());
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

    private String name(Enum<?> value) { return value == null ? null : value.name(); }
}
