package com.sinognss.cloud.vantix.application.company;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sinognss.cloud.vantix.application.config.SystemCompanyResolver;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.domain.company.DealerCompany;
import com.sinognss.cloud.vantix.infrastructure.mapper.DealerCompanyMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CompanyTransferTargetService {
    private final DealerCompanyMapper companyMapper;
    private final SystemCompanyResolver systemCompanyResolver;
    private final UserHolderBridge userHolder;

    public CompanyTransferTargetService(DealerCompanyMapper companyMapper,
                                        SystemCompanyResolver systemCompanyResolver,
                                        UserHolderBridge userHolder) {
        this.companyMapper = companyMapper;
        this.systemCompanyResolver = systemCompanyResolver;
        this.userHolder = userHolder;
    }

    public List<TransferTargetCompanyView> list() {
        Long currentCompanyId = userHolder.getCurrentCompanyId();
        Long systemCompanyId = systemCompanyResolver.requireId();
        List<DealerCompany> companies = loadCompanies();
        Map<Long, DealerCompany> companiesById = indexByCompanyId(companies);

        DealerCompany currentCompany = companiesById.get(currentCompanyId);
        if (currentCompany == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "company not found: " + currentCompanyId);
        }
        DealerCompany systemCompany = companiesById.get(systemCompanyId);
        if (systemCompany == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "system company not found: " + systemCompanyId);
        }

        if (currentCompanyId.equals(systemCompanyId)) {
            return companies.stream()
                    .filter(company -> !systemCompanyId.equals(company.getCompanyId()))
                    .map(company -> TransferTargetCompanyView.from(company, "DEALER"))
                    .toList();
        }

        Map<Long, TransferTargetCompanyView> targets = new LinkedHashMap<>();
        addTarget(targets, systemCompany, "SYSTEM");

        if (currentCompany.getParentCompanyId() != null
                && !systemCompanyId.equals(currentCompany.getParentCompanyId())) {
            DealerCompany parent = companiesById.get(currentCompany.getParentCompanyId());
            if (parent != null) {
                addTarget(targets, parent, "PARENT");
            }
        }
        companies.stream()
                .filter(company -> currentCompanyId.equals(company.getParentCompanyId()))
                .filter(company -> !systemCompanyId.equals(company.getCompanyId()))
                .forEach(company -> addTarget(targets, company, "CHILD"));
        return List.copyOf(targets.values());
    }

    public void validateTarget(Long currentCompanyId, Long targetCompanyId) {
        if (currentCompanyId == null || targetCompanyId == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "invalid transfer target");
        }
        if (currentCompanyId.equals(targetCompanyId)) {
            throw new BusinessException(ErrorCode.TRANSFER_NOT_ALLOWED, "transfer source and target must differ");
        }

        Long systemCompanyId = systemCompanyResolver.requireId();
        Map<Long, DealerCompany> companiesById = indexByCompanyId(loadCompanies());
        DealerCompany currentCompany = companiesById.get(currentCompanyId);
        if (currentCompany == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "company not found: " + currentCompanyId);
        }
        if (targetCompanyId.equals(systemCompanyId)) {
            return;
        }
        DealerCompany targetCompany = companiesById.get(targetCompanyId);
        if (targetCompany == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "target company not found: " + targetCompanyId);
        }
        if (currentCompanyId.equals(systemCompanyId)) {
            return;
        }
        boolean directRelation = targetCompanyId.equals(currentCompany.getParentCompanyId())
                || currentCompanyId.equals(targetCompany.getParentCompanyId());
        if (!directRelation) {
            throw new BusinessException(ErrorCode.TRANSFER_NOT_ALLOWED, "companies are not directly related");
        }
    }

    private List<DealerCompany> loadCompanies() {
        return companyMapper.selectList(Wrappers.<DealerCompany>lambdaQuery()
                .orderByAsc(DealerCompany::getCompanyId));
    }

    private Map<Long, DealerCompany> indexByCompanyId(List<DealerCompany> companies) {
        return companies.stream()
                .filter(company -> company.getCompanyId() != null)
                .collect(java.util.stream.Collectors.toMap(DealerCompany::getCompanyId,
                        company -> company, (first, ignored) -> first, LinkedHashMap::new));
    }

    private void addTarget(Map<Long, TransferTargetCompanyView> targets,
                           DealerCompany company,
                           String relationshipType) {
        targets.putIfAbsent(company.getCompanyId(),
                TransferTargetCompanyView.from(company, relationshipType));
    }
}
