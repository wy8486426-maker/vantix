package com.sinognss.cloud.vantix.application.config;

import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.OperatorIdentity;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.domain.config.AccountConfig;
import com.sinognss.cloud.vantix.infrastructure.mapper.AccountConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountConfigService {
    private static final Logger log = LoggerFactory.getLogger(AccountConfigService.class);
    private static final long GLOBAL_CONFIG_ID = 1L;
    private final AccountConfigMapper mapper;
    private final UserHolderBridge userHolder;

    public AccountConfigService(AccountConfigMapper mapper, UserHolderBridge userHolder) {
        this.mapper = mapper;
        this.userHolder = userHolder;
    }

    public AccountConfigView get() {
        AccountConfig config = mapper.selectById(GLOBAL_CONFIG_ID);
        if (config == null) {
            throw new BusinessException(ErrorCode.CONFIG_INVALID, "账号沉默配置尚未初始化");
        }
        return AccountConfigView.from(config);
    }

    @Transactional
    public AccountConfigView update(Integer silenceMonths) {
        if (silenceMonths == null || silenceMonths < 0) {
            throw new BusinessException(ErrorCode.CONFIG_INVALID, "账号沉默时长不能为负数");
        }
        AccountConfig config = mapper.selectById(GLOBAL_CONFIG_ID);
        if (config == null) {
            config = new AccountConfig();
            config.setId(GLOBAL_CONFIG_ID);
        }
        OperatorIdentity operator = userHolder.getOperator();
        config.setAccountSilenceMonths(silenceMonths);
        config.setUpdatedBy(operator.userId());
        if (config.getId().equals(GLOBAL_CONFIG_ID) && mapper.selectById(GLOBAL_CONFIG_ID) == null) {
            mapper.insert(config);
        } else {
            mapper.updateById(config);
        }
        log.info("Account silence config updated, accountSilenceMonths={}, operatorUserId={}", silenceMonths, operator.userId());
        return AccountConfigView.from(config);
    }
}
