package com.sinognss.cloud.vantix.application.dashboard;

import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.common.user.UserScope;
import com.sinognss.cloud.vantix.config.VantixProperties;
import com.sinognss.cloud.vantix.infrastructure.mapper.DashboardQueryMapper;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class DashboardQueryService {
    private final DashboardQueryMapper mapper;
    private final UserHolderBridge userHolder;
    private final Clock clock;
    private final VantixProperties properties;

    public DashboardQueryService(DashboardQueryMapper mapper, UserHolderBridge userHolder,
                                 Clock clock, VantixProperties properties) {
        this.mapper = mapper;
        this.userHolder = userHolder;
        this.clock = clock;
        this.properties = properties;
    }

    public DashboardView get() {
        UserScope scope = userHolder.getUserScope();
        if (scope == null || !scope.isSupported()) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_USER_SCOPE, "当前用户数据范围不受支持");
        }
        Long companyId = scope.isGlobal() ? null : scope.companyId();
        Long assignedUserId = scope.type() == UserScope.Type.PERSONAL ? scope.userId() : null;
        LocalDateTime now = LocalDateTime.now(clock);
        DashboardQueryRow row = mapper.statistics(companyId, assignedUserId, now,
                now.plusDays(properties.getUpcomingDays()));
        if (row == null) return empty();
        return new DashboardView(value(row.getServiceCodeTotal()), value(row.getServiceCodeWaiting()),
                value(row.getServiceCodeExpiring()), value(row.getServiceCodeExpired()),
                value(row.getServiceCodeProcessing()), value(row.getServiceCodeConsumed()),
                value(row.getAccountTotal()), value(row.getAccountWaiting()), value(row.getAccountActive()),
                value(row.getAccountExpired()), value(row.getAccountDisabled()),
                value(row.getGenerationOrderTotal()), value(row.getExchangeTotal()), value(row.getRenewalTotal()),
                value(row.getTransferTotal()));
    }

    private DashboardView empty() {
        return new DashboardView(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private long value(Long value) { return value == null ? 0 : value; }
}
