package com.sinognss.cloud.vantix.application.history;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sinognss.cloud.vantix.application.account.AccountIdentityView;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.OperatorIdentity;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.common.user.UserScope;
import com.sinognss.cloud.vantix.config.GenerationProperties;
import com.sinognss.cloud.vantix.domain.account.HistoryAccountImportBatch;
import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.domain.company.DealerCompany;
import com.sinognss.cloud.vantix.infrastructure.mapper.HistoryAccountImportBatchMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HistoryAccountImportService {
    private final HistoryAccountImportTransaction transaction;
    private final HistoryAccountImportBatchMapper batchMapper;
    private final ServiceAccountMapper accountMapper;
    private final UserHolderBridge userHolder;
    private final GenerationProperties generationProperties;

    public HistoryAccountImportService(HistoryAccountImportTransaction transaction,
                                       HistoryAccountImportBatchMapper batchMapper,
                                       ServiceAccountMapper accountMapper,
                                       UserHolderBridge userHolder,
                                       GenerationProperties generationProperties) {
        this.transaction = transaction;
        this.batchMapper = batchMapper;
        this.accountMapper = accountMapper;
        this.userHolder = userHolder;
        this.generationProperties = generationProperties;
    }

    public HistoryAccountImportView importAccounts(HistoryAccountImportCommand input) {
        HistoryAccountImportCommand command = normalize(input);
        assertGlobal();
        validateQuantity(command);
        String payloadHash = HistoryImportPayloadHash.calculate(command);
        HistoryAccountImportBatch existing = batchMapper.selectByRequestId(command.requestId());
        if (existing != null) {
            verifyPayload(existing, payloadHash);
            return view(existing);
        }
        try {
            OperatorIdentity operator = userHolder.getOperator();
            HistoryAccountImportBatch created = transaction.importBatch(command, payloadHash, operator);
            return view(created);
        } catch (DuplicateKeyException duplicate) {
            HistoryAccountImportBatch concurrent = batchMapper.selectByRequestId(command.requestId());
            if (concurrent == null) throw duplicate;
            verifyPayload(concurrent, payloadHash);
            return view(concurrent);
        }
    }

    static HistoryAccountImportCommand normalize(HistoryAccountImportCommand input) {
        if (input == null || input.requestId() == null || input.companyId() == null
                || input.specCode() == null || input.accounts() == null || input.accounts().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "历史账号导入参数不完整");
        }
        String requestId = input.requestId().trim();
        String specCode = input.specCode().trim();
        if (requestId.isBlank() || requestId.length() > 128 || hasControl(requestId)
                || input.companyId() <= 0 || specCode.isBlank() || specCode.length() > 32
                || hasControl(specCode)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "历史账号导入参数非法");
        }
        List<HistoryAccountIdentity> accounts = input.accounts().stream().map(item -> {
            if (item == null || item.id() == null || item.id() <= 0 || item.name() == null) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "历史账号 identity 非法");
            }
            String name = item.name().trim();
            if (name.isBlank() || name.length() > 128 || hasControl(name)) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "历史账号 name 非法");
            }
            return new HistoryAccountIdentity(item.id(), name);
        }).toList();
        return new HistoryAccountImportCommand(requestId, input.companyId(), specCode, accounts);
    }

    private HistoryAccountImportView view(HistoryAccountImportBatch batch) {
        List<AccountIdentityView> accounts = HistoryAccountImportBatchMapperStatus.COMPLETED.equals(batch.getStatus())
                ? accountMapper.selectByHistoryImportBatchId(batch.getId()).stream()
                .map(this::toIdentity).toList() : List.of();
        return new HistoryAccountImportView(batch.getRequestId(), batch.getImportBatchNo(),
                batch.getOwnerCompanyId(), batch.getSpecCode(), batch.getDurationDays(), batch.getQuantity(),
                batch.getStatus(), batch.getCreatedAt(), batch.getCompletedAt(), accounts);
    }

    private AccountIdentityView toIdentity(ServiceAccount account) {
        try {
            return new AccountIdentityView(Long.valueOf(account.getCorsAccountId()), account.getAccount());
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.HISTORY_IMPORT_STATE_INCONSISTENT,
                    "历史账号本地 CORS identity 无效");
        }
    }

    private void assertGlobal() {
        UserScope scope = userHolder.getUserScope();
        if (scope == null || !scope.isSupported()) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_USER_SCOPE, "当前用户数据范围不受支持");
        }
        if (!scope.isGlobal()) {
            throw new BusinessException(ErrorCode.GLOBAL_SCOPE_REQUIRED, "历史账号导入仅允许 GLOBAL 数据范围");
        }
    }

    private void validateQuantity(HistoryAccountImportCommand command) {
        if (command.accounts().size() > generationProperties.getMaxQuantityPerRequest()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "单次历史账号导入数量不能超过 " + generationProperties.getMaxQuantityPerRequest());
        }
    }

    private static void verifyPayload(HistoryAccountImportBatch existing, String payloadHash) {
        if (!payloadHash.equals(existing.getPayloadHash())) {
            throw new BusinessException(ErrorCode.HISTORY_IMPORT_IDEMPOTENCY_CONFLICT,
                    "requestId 已用于不同的历史账号导入参数");
        }
    }

    private static boolean hasControl(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }

    private static final class HistoryAccountImportBatchMapperStatus {
        private static final String COMPLETED = "COMPLETED";
    }
}
