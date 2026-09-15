package com.sinognss.cloud.vantix.application.exchange;

import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.OperatorIdentity;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.common.user.UserScope;
import com.sinognss.cloud.vantix.domain.exchange.CompanyExchangeConfig;
import com.sinognss.cloud.vantix.infrastructure.mapper.CompanyExchangeConfigMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

@Service
public class ServiceCodeExchangeConfigService {
    private static final Pattern PREFIX_PATTERN = Pattern.compile("^[A-Za-z0-9]{4}$");

    private final CompanyExchangeConfigMapper mapper;
    private final UserHolderBridge userHolder;
    private final Clock clock;

    public ServiceCodeExchangeConfigService(CompanyExchangeConfigMapper mapper,
                                            UserHolderBridge userHolder, Clock clock) {
        this.mapper = mapper;
        this.userHolder = userHolder;
        this.clock = clock;
    }

    public ServiceCodeExchangeConfigView getCurrent() {
        Long companyId = currentCompanyId();
        CompanyExchangeConfig config = mapper.selectByCompanyId(companyId);
        return config == null ? ServiceCodeExchangeConfigView.unconfigured(companyId)
                : ServiceCodeExchangeConfigView.from(config);
    }

    @Transactional
    public ServiceCodeExchangeConfigView configure(String inputPrefix) {
        Long companyId = currentCompanyId();
        String accountPrefix = normalizePrefix(inputPrefix);

        OperatorIdentity operator = userHolder.getOperatorOrNull();
        CompanyExchangeConfig config = new CompanyExchangeConfig();
        config.setCompanyId(companyId);
        config.setAccountPrefix(accountPrefix);
        config.setOperatorUserId(operator.userId());
        config.setOperatorUserName(operator.userName());
        config.setCreatedAt(LocalDateTime.now(clock));
        try {
            if (mapper.insert(config) != 1) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "兑换配置写入失败");
            }
            return ServiceCodeExchangeConfigView.from(config);
        } catch (DuplicateKeyException duplicate) {
            CompanyExchangeConfig concurrent = mapper.selectByCompanyIdForUpdate(companyId);
            if (concurrent == null) {
                throw duplicate;
            }
            return resolveExisting(concurrent, accountPrefix);
        }
    }

    static String normalizePrefix(String inputPrefix) {
        String prefix = inputPrefix == null ? null : inputPrefix.trim();
        if (prefix == null || !PREFIX_PATTERN.matcher(prefix).matches()) {
            throw new BusinessException(ErrorCode.EXCHANGE_PREFIX_INVALID,
                    "accountPrefix 必须是 4 位数字或英文字母");
        }
        return prefix;
    }

    private Long currentCompanyId() {
        UserScope scope = userHolder.getUserScope();
        if (scope.type() != UserScope.Type.COMPANY && scope.type() != UserScope.Type.PERSONAL) {
            throw new BusinessException(ErrorCode.GLOBAL_SCOPE_REQUIRED,
                    "兑换配置需要 COMPANY 或 PERSONAL 数据范围");
        }
        if (scope.companyId() == null || scope.companyId() <= 0) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_USER_SCOPE, "当前用户缺少公司范围");
        }
        return scope.companyId();
    }

    private ServiceCodeExchangeConfigView resolveExisting(CompanyExchangeConfig existing, String requestedPrefix) {
        if (!requestedPrefix.equals(existing.getAccountPrefix())) {
            throw new BusinessException(ErrorCode.EXCHANGE_CONFIG_LOCKED,
                    "兑换前缀已配置并永久锁定，不能修改");
        }
        return ServiceCodeExchangeConfigView.from(existing);
    }
}
