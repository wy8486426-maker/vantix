package com.sinognss.cloud.vantix.application.exchange;

import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.domain.exchange.ExchangeBatch;
import com.sinognss.cloud.vantix.domain.exchange.ExchangeStatus;
import com.sinognss.cloud.vantix.infrastructure.mapper.ExchangeBatchMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class ExchangeQueryService {
    private final ExchangeBatchMapper batchMapper;
    private final ServiceAccountMapper accountMapper;

    public ExchangeQueryService(ExchangeBatchMapper batchMapper,
                                ServiceAccountMapper accountMapper) {
        this.batchMapper = batchMapper;
        this.accountMapper = accountMapper;
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
        List<ExchangeAccountView> accounts = List.of();
        if (batch.getStatus() == ExchangeStatus.COMPLETED) {
            List<ServiceAccount> serviceAccounts = accountMapper.selectByExchangeBatchId(batch.getId());
            if (serviceAccounts.stream()
                    .anyMatch(account -> !Objects.equals(account.getAssignedUserId(), batch.getAssignedUserId()))) {
                throw new BusinessException(ErrorCode.EXCHANGE_STATE_INCONSISTENT,
                        "兑换批次账号归属与批次不一致");
            }
            accounts = serviceAccounts.stream().map(ExchangeQueryService::toAccountView).toList();
        }
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
