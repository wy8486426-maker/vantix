package com.sinognss.cloud.vantix.application.company;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.OperatorIdentity;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.common.user.UserScope;
import com.sinognss.cloud.vantix.domain.company.CompanyStatus;
import com.sinognss.cloud.vantix.domain.company.DealerCompany;
import com.sinognss.cloud.vantix.domain.company.DealerRelationLog;
import com.sinognss.cloud.vantix.infrastructure.mapper.DealerCompanyMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.DealerRelationLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class CompanyService {
    private static final Logger log = LoggerFactory.getLogger(CompanyService.class);

    private final DealerCompanyMapper companyMapper;
    private final DealerRelationLogMapper relationLogMapper;
    private final UserHolderBridge userHolder;
    private final Clock clock;

    public CompanyService(DealerCompanyMapper companyMapper,
                          DealerRelationLogMapper relationLogMapper,
                          UserHolderBridge userHolder,
                          Clock clock) {
        this.companyMapper = companyMapper;
        this.relationLogMapper = relationLogMapper;
        this.userHolder = userHolder;
        this.clock = clock;
    }

    public List<CompanyView> list(String name, CompanyStatus status) {
        UserScope scope = userHolder.getUserScope();
        var query = Wrappers.<DealerCompany>lambdaQuery()
                .like(StringUtils.hasText(name), DealerCompany::getCompanyName, name)
                .eq(status != null, DealerCompany::getCompanyStatus, status)
                .orderByAsc(DealerCompany::getCompanyId);
        if (!scope.isGlobal()) {
            query.eq(DealerCompany::getCompanyId, scope.companyId());
        }
        return companyMapper.selectList(query).stream().map(CompanyView::from).toList();
    }

    public CompanyView get(Long companyId) {
        assertCompanyAccess(companyId);
        return CompanyView.from(getRequired(companyId));
    }

    public List<CompanyView> directChildren(Long companyId) {
        assertCompanyAccess(companyId);
        return companyMapper.selectList(Wrappers.<DealerCompany>lambdaQuery()
                        .eq(DealerCompany::getParentCompanyId, companyId)
                        .orderByAsc(DealerCompany::getCompanyId))
                .stream().map(CompanyView::from).toList();
    }

    @Transactional
    public CompanyView updateParent(UpdateCompanyParentCommand command) {
        Long companyId = command.companyId();
        assertCompanyAccess(companyId);
        DealerCompany company = getRequired(companyId);
        Long parentId = command.parentCompanyId();
        if (companyId.equals(parentId)) {
            throw new BusinessException(ErrorCode.COMPANY_RELATION_INVALID, "公司不能设置自己为直接上级");
        }
        if (parentId != null) {
            DealerCompany parent = getRequired(parentId);
            validateParentChain(companyId, parent);
        }

        Long oldParentId = company.getParentCompanyId();
        company.setParentCompanyId(parentId);
        companyMapper.updateById(company);

        OperatorIdentity operator = userHolder.getOperator();
        DealerRelationLog relationLog = new DealerRelationLog();
        relationLog.setCompanyId(companyId);
        relationLog.setOldParentCompanyId(oldParentId);
        relationLog.setNewParentCompanyId(parentId);
        relationLog.setOperatorUserId(operator.userId());
        relationLog.setOperatorUserName(operator.userName());
        relationLog.setReason(command.reason());
        relationLog.setCreatedAt(LocalDateTime.now(clock));
        relationLogMapper.insert(relationLog);

        log.info("Company parent relation changed, companyId={}, oldParentCompanyId={}, newParentCompanyId={}, operatorUserId={}",
                companyId, oldParentId, parentId, operator.userId());
        return CompanyView.from(company);
    }

    public DealerCompany getRequired(Long companyId) {
        if (companyId == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公司不存在");
        }
        DealerCompany company = companyMapper.selectOne(Wrappers.<DealerCompany>lambdaQuery()
                .eq(DealerCompany::getCompanyId, companyId));
        if (company == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公司不存在: " + companyId);
        }
        return company;
    }

    public boolean exists(Long companyId) {
        return companyId != null && companyMapper.selectCount(Wrappers.<DealerCompany>lambdaQuery()
                .eq(DealerCompany::getCompanyId, companyId)) > 0;
    }

    private void assertCompanyAccess(Long companyId) {
        UserScope scope = userHolder.getUserScope();
        if (!scope.canAccessCompany(companyId)) {
            throw new BusinessException(ErrorCode.SERVICE_CODE_NOT_OWNED, "无权访问该公司的数据");
        }
    }

    private void validateParentChain(Long companyId, DealerCompany parent) {
        if (parent.getParentCompanyId() != null) {
            throw new BusinessException(ErrorCode.COMPANY_RELATION_INVALID, "二级公司不能继续挂下级公司");
        }
        Set<Long> visited = new HashSet<>();
        Long cursor = parent.getCompanyId();
        while (cursor != null) {
            if (!visited.add(cursor) || cursor.equals(companyId)) {
                throw new BusinessException(ErrorCode.COMPANY_RELATION_INVALID, "公司上下级关系会形成循环");
            }
            DealerCompany ancestor = companyMapper.selectOne(Wrappers.<DealerCompany>lambdaQuery()
                    .eq(DealerCompany::getCompanyId, cursor));
            cursor = ancestor == null ? null : ancestor.getParentCompanyId();
        }
    }
}
