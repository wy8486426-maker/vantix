package com.sinognss.cloud.vantix.application.company;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.common.user.UserScope;
import com.sinognss.cloud.vantix.domain.company.DealerCompany;
import com.sinognss.cloud.vantix.infrastructure.mapper.CompanyFrontendQueryMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.DealerCompanyMapper;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class CompanyFrontendQueryService {
    private final CompanyFrontendQueryMapper queryMapper;
    private final DealerCompanyMapper companyMapper;
    private final UserHolderBridge userHolder;

    public CompanyFrontendQueryService(CompanyFrontendQueryMapper queryMapper,
                                       DealerCompanyMapper companyMapper,
                                       UserHolderBridge userHolder) {
        this.queryMapper = queryMapper;
        this.companyMapper = companyMapper;
        this.userHolder = userHolder;
    }

    public com.sinognss.cloud.vantix.application.servicecode.PageResponse<CompanyPageView> page(
            CompanyPageQuery input) {
        requireGlobal();
        CompanyPageQuery query = validate(input);
        IPage<CompanyPageRow> page = queryMapper.pageForFrontend(new Page<>(query.current(), query.size()),
                query.keyword(), query.status() == null ? null : query.status().name(),
                query.parentCompanyId(), query.level() == null ? null : query.level().name());
        return new com.sinognss.cloud.vantix.application.servicecode.PageResponse<>(
                page.getRecords().stream().map(CompanyPageView::from).toList(), page.getCurrent(),
                page.getSize(), page.getTotal(), page.getPages());
    }

    public java.util.List<CompanyPartnerView> partners(Long requestedCompanyId) {
        UserScope scope = userHolder.getUserScope();
        if (!scope.isSupported()) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_USER_SCOPE, "当前用户数据范围不受支持");
        }
        Long companyId = requestedCompanyId;
        if (scope.isGlobal()) {
            if (companyId == null || companyId <= 0) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "GLOBAL 查询合作伙伴必须传 companyId");
            }
        } else {
            if (requestedCompanyId != null && !Objects.equals(requestedCompanyId, scope.companyId())) {
                throw new BusinessException(ErrorCode.SERVICE_CODE_NOT_OWNED, "无权查询指定公司的合作伙伴");
            }
            companyId = scope.companyId();
        }
        if (companyId == null || companyMapper.selectCount(Wrappers.<DealerCompany>lambdaQuery()
                .eq(DealerCompany::getCompanyId, companyId)) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公司不存在: " + companyId);
        }
        return queryMapper.partners(companyId).stream().map(CompanyPartnerView::from).toList();
    }

    private void requireGlobal() {
        if (!userHolder.getUserScope().isGlobal()) {
            throw new BusinessException(ErrorCode.GLOBAL_SCOPE_REQUIRED, "客户分页仅允许 GLOBAL 数据范围访问");
        }
    }

    private CompanyPageQuery validate(CompanyPageQuery input) {
        if (input == null || input.current() < 1 || input.size() < 1 || input.size() > 500) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "分页参数非法");
        }
        if (input.parentCompanyId() != null && input.parentCompanyId() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "parentCompanyId 必须大于 0");
        }
        String keyword = normalize(input.keyword());
        return new CompanyPageQuery(input.current(), input.size(), keyword, input.status(),
                input.parentCompanyId(), input.level());
    }

    private String normalize(String value) {
        if (value == null) return null;
        String result = value.trim();
        if (result.isEmpty()) return null;
        if (result.length() > 100 || result.codePoints().anyMatch(Character::isISOControl)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "keyword 参数非法");
        }
        return result;
    }
}
