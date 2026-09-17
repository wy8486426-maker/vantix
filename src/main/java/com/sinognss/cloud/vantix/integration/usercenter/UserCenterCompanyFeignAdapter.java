package com.sinognss.cloud.vantix.integration.usercenter;

import com.sinognss.cloud.base.common.api.CommonResult;
import com.sinognss.cloud.vantix.application.company.UserCenterCompany;
import com.sinognss.cloud.vantix.application.company.UserCenterCompanyGateway;
import com.sinognss.cloud.vantix.application.company.UserCenterCompanyPage;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
public class UserCenterCompanyFeignAdapter implements UserCenterCompanyGateway {
    private static final Logger log = LoggerFactory.getLogger(UserCenterCompanyFeignAdapter.class);

    private final UserCenterFeignService feignService;

    public UserCenterCompanyFeignAdapter(UserCenterFeignService feignService) {
        this.feignService = feignService;
    }

    @Override
    public Optional<UserCenterCompany> findByCompanyId(Long companyId) {
        if (companyId == null || companyId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "companyId 必须大于 0");
        }
        try {
            CommonResult<List<CompanyCommonVO>> result = feignService.selectListByCompanyIdList(List.of(companyId));
            if (result == null || !result.success() || result.getData() == null) {
                throw syncFailure("用户中心公司查询失败");
            }
            for (CompanyCommonVO item : result.getData()) {
                if (item != null && Objects.equals(item.id(), companyId)) {
                    return Optional.of(toCompany(item));
                }
            }
            return Optional.empty();
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.error("User-center company lookup failed; companyId={}", companyId);
            throw syncFailure("用户中心公司查询失败");
        }
    }

    @Override
    public UserCenterCompanyPage page(long currPage, long pageSize) {
        if (currPage < 1 || pageSize < 1) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "公司同步分页参数非法");
        }
        try {
            CommonResult<PageUtil<CompanySelectVO>> result =
                    feignService.choicePage(pageSize, currPage);

            if (result == null || !result.success() || result.getData() == null) {
                throw syncFailure("用户中心公司分页查询失败");
            }

            PageUtil<CompanySelectVO> page = result.getData();

            if (page.currPage() == null
                    || page.totalPage() == null
                    || page.list() == null
                    || page.currPage() != currPage
                    || page.currPage() < 1
                    || page.totalPage() < 0
                    || (page.totalPage() > 0 && page.currPage() > page.totalPage())
                    || (page.totalPage() == 0 && !page.list().isEmpty())) {
                throw syncFailure("用户中心公司分页返回无效");
            }
            int sourceItemCount = page.list().size();
            List<UserCenterCompany> companies = new ArrayList<>();
            for (int index = 0; index < page.list().size(); index++) {
                CompanySelectVO item = page.list().get(index);
                try {
                    UserCenterCompany company = item == null
                            ? null : new UserCenterCompany(item.id(), item.name());
                    if (company == null || !company.isValid()) {
                        throw syncFailure("用户中心返回的公司资料无效");
                    }
                    companies.add(company);
                } catch (BusinessException invalidCompany) {
                    log.warn("User-center company page item skipped; page={} itemIndex={}", currPage, index);
                }
            }
            return new UserCenterCompanyPage(page.currPage(), page.totalPage(), sourceItemCount, companies);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.error("User-center company page failed; page={} pageSize={}", currPage, pageSize);
            throw syncFailure("用户中心公司分页查询失败");
        }
    }

    private UserCenterCompany toCompany(CompanyCommonVO item) {
        UserCenterCompany company = new UserCenterCompany(item.id(), item.name());
        if (!company.isValid()) {
            throw syncFailure("用户中心返回的公司资料无效");
        }
        return company;
    }

    private BusinessException syncFailure(String message) {
        return new BusinessException(ErrorCode.COMPANY_SYNC_FAILED, message);
    }
}
