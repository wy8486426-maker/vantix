package com.sinognss.cloud.vantix.application.servicecode.generation;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.common.user.UserScope;
import com.sinognss.cloud.vantix.domain.servicecode.GenerationSource;
import com.sinognss.cloud.vantix.infrastructure.mapper.GenerationOrderQueryMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class GenerationOrderQueryService {
    private final GenerationOrderQueryMapper mapper;
    private final UserHolderBridge userHolder;

    public GenerationOrderQueryService(GenerationOrderQueryMapper mapper, UserHolderBridge userHolder) {
        this.mapper = mapper;
        this.userHolder = userHolder;
    }

    public com.sinognss.cloud.vantix.application.servicecode.PageResponse<GenerationOrderView> page(
            GenerationOrderPageQuery input) {
        GenerationOrderPageQuery query = validate(input);
        UserScope scope = userHolder.getUserScope();
        Long scopeCompanyId = resolveScopeCompanyId(scope, query.ownerCompanyId());
        IPage<GenerationOrderQueryRow> page = mapper.pageForFrontend(
                new Page<>(query.current(), query.size()), query.keyword(), name(query.generationSource()),
                query.status(), query.ownerCompanyId(), scopeCompanyId, query.createdFrom(), query.createdTo());
        return new com.sinognss.cloud.vantix.application.servicecode.PageResponse<>(
                page.getRecords().stream().map(GenerationOrderView::from).toList(), page.getCurrent(),
                page.getSize(), page.getTotal(), page.getPages());
    }

    public GenerationOrderStatistics statistics(GenerationOrderPageQuery input) {
        GenerationOrderPageQuery query = validateStatistics(input);
        UserScope scope = userHolder.getUserScope();
        Long scopeCompanyId = resolveScopeCompanyId(scope, query.ownerCompanyId());
        GenerationOrderStatisticsRow result = mapper.statistics(query.keyword(), null,
                query.status(), query.ownerCompanyId(), scopeCompanyId, query.createdFrom(), query.createdTo());
        if (result == null) return new GenerationOrderStatistics(0, 0, 0);
        return new GenerationOrderStatistics(value(result.getTotal()), value(result.getB2b()), value(result.getOffline()));
    }

    private GenerationOrderPageQuery validate(GenerationOrderPageQuery input) {
        if (input == null || input.current() < 1 || input.size() < 1 || input.size() > 500) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "分页参数非法");
        }
        validateDates(input.createdFrom(), input.createdTo());
        return normalized(input);
    }

    private GenerationOrderPageQuery validateStatistics(GenerationOrderPageQuery input) {
        if (input == null) throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "统计查询参数不能为空");
        validateDates(input.createdFrom(), input.createdTo());
        return normalized(input);
    }

    private GenerationOrderPageQuery normalized(GenerationOrderPageQuery input) {
        String keyword = normalize(input.keyword(), 100, "keyword");
        String status = normalize(input.status(), 32, "status");
        if (input.ownerCompanyId() != null && input.ownerCompanyId() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "ownerCompanyId 必须大于 0");
        }
        return new GenerationOrderPageQuery(input.current(), input.size(), keyword,
                input.generationSource(), status, input.ownerCompanyId(), input.createdFrom(), input.createdTo());
    }

    private Long resolveScopeCompanyId(UserScope scope, Long requestedCompanyId) {
        if (!scope.isSupported()) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_USER_SCOPE, "当前用户数据范围不受支持");
        }
        if (!scope.isGlobal() && requestedCompanyId != null
                && !Objects.equals(requestedCompanyId, scope.companyId())) {
            throw new BusinessException(ErrorCode.SERVICE_CODE_NOT_OWNED, "无权查询指定公司的生成订单");
        }
        return scope.isGlobal() ? null : scope.companyId();
    }

    private String normalize(String value, int maxLength, String name) {
        if (value == null) return null;
        String result = value.trim();
        if (result.isEmpty()) return null;
        if (result.length() > maxLength || result.codePoints().anyMatch(Character::isISOControl)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, name + " 参数非法");
        }
        return result;
    }

    private void validateDates(LocalDateTime from, LocalDateTime to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "createdFrom 不能晚于 createdTo");
        }
    }

    private String name(GenerationSource source) {
        return source == null ? null : source.name();
    }

    private long value(Long value) {
        return value == null ? 0 : value;
    }
}
