package com.sinognss.cloud.vantix.application.exchange;

import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.OperatorIdentity;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.domain.exchange.CompanyExchangeConfig;
import com.sinognss.cloud.vantix.application.company.DealerCompanySyncService;
import com.sinognss.cloud.vantix.infrastructure.mapper.CompanyExchangeConfigMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

@Service
public class ServiceCodeExchangeConfigService {
    private static final Pattern PREFIX_PATTERN = Pattern.compile("^[A-Za-z0-9]{4}$");

    private final CompanyExchangeConfigMapper mapper;
    private final DealerCompanySyncService dealerCompanySyncService;
    private final UserHolderBridge userHolder;
    private final Clock clock;

    public ServiceCodeExchangeConfigService(CompanyExchangeConfigMapper mapper,
                                            DealerCompanySyncService dealerCompanySyncService,
                                            UserHolderBridge userHolder, Clock clock) {
        this.mapper = mapper;
        this.dealerCompanySyncService = dealerCompanySyncService;
        this.userHolder = userHolder;
        this.clock = clock;
    }

    public ServiceCodeExchangeConfigView getCurrent() {
        Long companyId = userHolder.getCurrentCompanyId();
        CompanyExchangeConfig config = mapper.selectByCompanyId(companyId);
        return config == null ? ServiceCodeExchangeConfigView.unconfigured(companyId)
                : ServiceCodeExchangeConfigView.from(config);
    }

    public ServiceCodeExchangeConfigView configure(String inputPrefix) {
        Long companyId = userHolder.getCurrentCompanyId();
        String accountPrefix = normalizePrefix(inputPrefix);

        CompanyExchangeConfig existing = mapper.selectByCompanyId(companyId);
        if (existing != null) {
            return resolveExisting(existing, accountPrefix);
        }

        dealerCompanySyncService.ensurePresent(companyId);

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
            CompanyExchangeConfig concurrent = mapper.selectByCompanyId(companyId);
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

    private ServiceCodeExchangeConfigView resolveExisting(CompanyExchangeConfig existing, String requestedPrefix) {
        if (!requestedPrefix.equals(existing.getAccountPrefix())) {
            throw new BusinessException(ErrorCode.EXCHANGE_CONFIG_LOCKED,
                    "兑换前缀已配置并永久锁定，不能修改");
        }
        return ServiceCodeExchangeConfigView.from(existing);
    }
}
