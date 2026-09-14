package com.sinognss.cloud.vantix.application.exchange;

import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.domain.exchange.ExchangeBatch;
import com.sinognss.cloud.vantix.domain.exchange.ExchangeStatus;
import com.sinognss.cloud.vantix.infrastructure.mapper.ExchangeBatchMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ExchangeQueryService {
    private final ExchangeBatchMapper batchMapper;
    private final ServiceAccountMapper accountMapper;
    private final UserHolderBridge userHolder;

    public ExchangeQueryService(ExchangeBatchMapper batchMapper,
                                ServiceAccountMapper accountMapper,
                                UserHolderBridge userHolder) {
        this.batchMapper = batchMapper;
        this.accountMapper = accountMapper;
        this.userHolder = userHolder;
    }

    public ServiceCodeExchangeView get(String requestId) {
        if (requestId == null || requestId.isBlank() || requestId.trim().length() > 128
                || requestId.codePoints().anyMatch(Character::isISOControl)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "requestId 非法");
        }
        ExchangeBatch batch = batchMapper.selectByRequestId(requestId.trim());
        if (batch == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "兑换请求不存在: " + requestId);
        }
        if (!userHolder.getUserScope().canAccessCompany(batch.getOwnerCompanyId())) {
            throw new BusinessException(ErrorCode.SERVICE_CODE_NOT_OWNED, "无权访问该公司的兑换记录");
        }
        List<ExchangeAccountView> accounts = batch.getStatus() == ExchangeStatus.COMPLETED
                ? accountMapper.selectByExchangeBatchId(batch.getId()).stream()
                        .map(ExchangeQueryService::toAccountView).toList()
                : List.of();
        return new ServiceCodeExchangeView(batch.getRequestId(), batch.getExchangeBatchNo(),
                batch.getOwnerCompanyId(), batch.getSpecCode(), batch.getGenerationSource(),
                batch.getQuantity(), batch.getStatus().name(), batch.getAccountPrefix(),
                batch.getCreatedAt(), batch.getCompletedAt(), accounts);
    }

    private static ExchangeAccountView toAccountView(ServiceAccount account) {
        return new ExchangeAccountView(account.getCorsAccountId(), account.getAccount(),
                account.getCorsStatus(), account.getCorsActivationStatus(),
                account.getActivatedAt(), account.getExpireAt());
    }
}
