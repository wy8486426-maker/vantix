package com.sinognss.cloud.vantix.application.exchange;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sinognss.cloud.vantix.common.DurationDisplayFormatter;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.common.user.UserScope;
import com.sinognss.cloud.vantix.domain.company.DealerCompany;
import com.sinognss.cloud.vantix.domain.config.DurationUnit;
import com.sinognss.cloud.vantix.infrastructure.mapper.DealerCompanyMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeExchangeGroupMapper;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ExchangeGroupQueryService {
    private final ServiceCodeExchangeGroupMapper groupMapper;
    private final DealerCompanyMapper companyMapper;
    private final UserHolderBridge userHolder;
    private final Clock clock;

    public ExchangeGroupQueryService(ServiceCodeExchangeGroupMapper groupMapper,
                                     DealerCompanyMapper companyMapper,
                                     UserHolderBridge userHolder,
                                     Clock clock) {
        this.groupMapper = groupMapper;
        this.companyMapper = companyMapper;
        this.userHolder = userHolder;
        this.clock = clock;
    }

    public List<ServiceCodeExchangeGroupView> list(Long requestedCompanyId) {
        UserScope scope = userHolder.getUserScope();
        Long companyId = resolveCompanyId(scope, requestedCompanyId);
        if (companyMapper.selectCount(Wrappers.<DealerCompany>lambdaQuery()
                .eq(DealerCompany::getCompanyId, companyId)) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公司不存在: " + companyId);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        return groupMapper.selectAvailableGroups(companyId, now).stream()
                .map(row -> new ServiceCodeExchangeGroupView(
                        row.getSpecCode(),
                        DurationDisplayFormatter.format(row.getDurationValue(),
                                DurationDisplayFormatter.parseUnit(row.getDurationUnit())),
                        row.getServiceType(),
                        row.getGenerationSource(),
                        row.getAvailableCount(),
                        row.getEarliestExpireAt()))
                .toList();
    }

    private Long resolveCompanyId(UserScope scope, Long requestedCompanyId) {
        if (scope.isGlobal()) {
            if (requestedCompanyId == null || requestedCompanyId <= 0) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "global 用户必须指定 companyId");
            }
            return requestedCompanyId;
        }
        Long targetCompanyId = requestedCompanyId == null ? scope.companyId() : requestedCompanyId;
        if (!scope.canAccessCompany(targetCompanyId)) {
            throw new BusinessException(ErrorCode.SERVICE_CODE_NOT_OWNED, "无权访问该公司的服务码");
        }
        return targetCompanyId;
    }
}
