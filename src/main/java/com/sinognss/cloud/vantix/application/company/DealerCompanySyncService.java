package com.sinognss.cloud.vantix.application.company;

import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.config.CompanySyncProperties;
import com.sinognss.cloud.vantix.domain.company.DealerCompany;
import com.sinognss.cloud.vantix.infrastructure.mapper.DealerCompanyMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class DealerCompanySyncService {
    private static final Logger log = LoggerFactory.getLogger(DealerCompanySyncService.class);

    private final DealerCompanyMapper companyMapper;
    private final UserCenterCompanyGateway userCenterCompanyGateway;
    private final CompanySyncProperties properties;
    private final Clock clock;

    public DealerCompanySyncService(DealerCompanyMapper companyMapper,
                                    UserCenterCompanyGateway userCenterCompanyGateway,
                                    CompanySyncProperties properties,
                                    Clock clock) {
        this.companyMapper = companyMapper;
        this.userCenterCompanyGateway = userCenterCompanyGateway;
        this.properties = properties;
        this.clock = clock;
    }

    public void ensurePresent(Long companyId) {
        validateCompanyId(companyId);
        DealerCompany existing = companyMapper.selectByCompanyId(companyId);
        if (existing != null) {
            return;
        }

        UserCenterCompany remote = userCenterCompanyGateway.findByCompanyId(companyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "公司不存在: " + companyId));
        if (!remote.isValid()) {
            throw syncFailure("用户中心返回的公司资料无效");
        }

        DealerCompany company = toDealerCompany(remote, LocalDateTime.now(clock));
        companyMapper.upsertSyncedCompanies(List.of(company));
    }

    public SyncSummary syncAllCompanies() {
        long currentPage = 1;
        int pageCount = 0;
        int companyCount = 0;
        while (true) {
            UserCenterCompanyPage page = userCenterCompanyGateway.page(currentPage, properties.getPageSize());
            validatePage(page, currentPage);
            if (page.sourceItemCount() == 0) {
                log.warn("Dealer company sync stopped on empty page; page={}", currentPage);
                break;
            }

            if (page.companies().isEmpty()) {
                log.warn("Dealer company sync page contained no valid companies; page={} sourceItemCount={}",
                        currentPage, page.sourceItemCount());
            }

            List<DealerCompany> validCompanies = new ArrayList<>();
            for (int index = 0; index < page.companies().size(); index++) {
                UserCenterCompany remote = page.companies().get(index);
                if (remote == null || !remote.isValid()) {
                    log.warn("Dealer company sync skipped invalid company; page={} itemIndex={}",
                            currentPage, index);
                    continue;
                }
                validCompanies.add(toDealerCompany(remote, LocalDateTime.now(clock)));
            }
            if (!validCompanies.isEmpty()) {
                companyMapper.upsertSyncedCompanies(validCompanies);
            }
            pageCount++;
            companyCount += validCompanies.size();
            log.info("Dealer company page synced; page={} count={}", currentPage, validCompanies.size());

            if (currentPage >= page.totalPage()) {
                break;
            }
            currentPage++;
        }
        return new SyncSummary(pageCount, companyCount);
    }

    private DealerCompany toDealerCompany(UserCenterCompany remote, LocalDateTime syncedAt) {
        DealerCompany company = new DealerCompany();
        company.setCompanyId(remote.companyId());
        company.setCompanyName(remote.companyName());
        company.setParentCompanyId(null);
        company.setCompanySyncedAt(syncedAt);
        return company;
    }

    private void validatePage(UserCenterCompanyPage page, long requestedPage) {
        if (page == null || page.companies() == null || page.currentPage() != requestedPage
                || page.currentPage() < 1 || page.totalPage() < 0
                || page.sourceItemCount() < 0
                || (page.totalPage() == 0 && page.sourceItemCount() > 0)
                || page.sourceItemCount() < page.companies().size()
                || (page.totalPage() > 0 && page.currentPage() > page.totalPage())) {
            throw syncFailure("用户中心公司分页返回无效");
        }
    }

    private void validateCompanyId(Long companyId) {
        if (companyId == null || companyId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "companyId 必须大于 0");
        }
    }

    private BusinessException syncFailure(String message) {
        return new BusinessException(ErrorCode.COMPANY_SYNC_FAILED, message);
    }

    public record SyncSummary(int pageCount, int companyCount) {
    }
}
