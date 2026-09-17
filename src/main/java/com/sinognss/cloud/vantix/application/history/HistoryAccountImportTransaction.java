package com.sinognss.cloud.vantix.application.history;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.OperatorIdentity;
import com.sinognss.cloud.vantix.domain.account.AccountSource;
import com.sinognss.cloud.vantix.domain.account.AccountSourceInvariant;
import com.sinognss.cloud.vantix.domain.account.HistoryAccountImportBatch;
import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.domain.company.DealerCompany;
import com.sinognss.cloud.vantix.domain.config.ServiceDurationConfig;
import com.sinognss.cloud.vantix.infrastructure.mapper.DealerCompanyMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.HistoryAccountImportBatchMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceDurationConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;

@Service
public class HistoryAccountImportTransaction {
    private final HistoryAccountImportBatchMapper batchMapper;
    private final ServiceAccountMapper accountMapper;
    private final ServiceDurationConfigMapper durationMapper;
    private final DealerCompanyMapper companyMapper;
    private final Clock clock;

    public HistoryAccountImportTransaction(HistoryAccountImportBatchMapper batchMapper,
                                           ServiceAccountMapper accountMapper,
                                           ServiceDurationConfigMapper durationMapper,
                                           DealerCompanyMapper companyMapper,
                                           Clock clock) {
        this.batchMapper = batchMapper;
        this.accountMapper = accountMapper;
        this.durationMapper = durationMapper;
        this.companyMapper = companyMapper;
        this.clock = clock;
    }

    @Transactional
    public HistoryAccountImportBatch importBatch(HistoryAccountImportCommand command,
                                                  String payloadHash,
                                                  OperatorIdentity operator) {
        HistoryAccountImportBatch existing = batchMapper.selectByRequestId(command.requestId());
        if (existing != null) {
            if (!payloadHash.equals(existing.getPayloadHash())) {
                throw new BusinessException(ErrorCode.HISTORY_IMPORT_IDEMPOTENCY_CONFLICT,
                        "requestId 已用于不同的历史账号导入参数");
            }
            return existing;
        }
        if (companyMapper.selectCount(Wrappers.<DealerCompany>lambdaQuery()
                .eq(DealerCompany::getCompanyId, command.companyId())) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公司不存在: " + command.companyId());
        }
        ServiceDurationConfig spec = durationMapper.selectBySpecCodes(List.of(command.specCode())).stream()
                .filter(item -> command.specCode().equals(item.getSpecCode()))
                .findFirst().orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "服务规格不存在: " + command.specCode()));
        if (spec.getDurationDays() == null || spec.getDurationDays() <= 0
                || spec.getAccountSilenceDays() == null || spec.getAccountSilenceDays() < 0
                || spec.getServiceType() == null || spec.getServiceType().isBlank()) {
            throw new BusinessException(ErrorCode.CONFIG_INVALID, "服务规格配置无效: " + command.specCode());
        }
        validateBatchIdentities(command.accounts());
        List<String> corsIds = command.accounts().stream().map(item -> String.valueOf(item.id())).toList();
        List<String> names = command.accounts().stream().map(HistoryAccountIdentity::name).toList();
        if (!accountMapper.selectByCorsAccountIds(corsIds).isEmpty()
                || !accountMapper.selectByAccountNames(names).isEmpty()) {
            throw new BusinessException(ErrorCode.HISTORY_IMPORT_IDENTITY_CONFLICT,
                    "历史账号 CORS identity 已被纳管，不能覆盖或重复导入");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        HistoryAccountImportBatch batch = new HistoryAccountImportBatch();
        batch.setImportBatchNo("HISTORY-" + UUID.randomUUID().toString().replace("-", "").toUpperCase());
        batch.setRequestId(command.requestId());
        batch.setOwnerCompanyId(command.companyId());
        batch.setSpecCode(command.specCode());
        batch.setServiceType(spec.getServiceType());
        batch.setDurationDays(spec.getDurationDays());
        batch.setAccountSilenceDays(spec.getAccountSilenceDays());
        batch.setQuantity(command.accounts().size());
        batch.setPayloadHash(payloadHash);
        batch.setStatus("PROCESSING");
        batch.setOperatorUserId(operator == null ? null : operator.userId());
        batch.setOperatorUserName(operator == null ? null : operator.userName());
        batch.setCreatedAt(now);
        batch.setUpdatedAt(now);
        if (batchMapper.insert(batch) != 1 || batch.getId() == null) {
            throw new BusinessException(ErrorCode.HISTORY_IMPORT_STATE_INCONSISTENT,
                    "历史账号导入批次创建失败");
        }

        List<ServiceAccount> accounts = command.accounts().stream().map(input -> {
            ServiceAccount account = new ServiceAccount();
            account.setAccountSource(AccountSource.HISTORY_IMPORT);
            account.setCorsAccountId(String.valueOf(input.id()));
            account.setAccount(input.name());
            account.setOwnerCompanyId(command.companyId());
            account.setSpecCode(spec.getSpecCode());
            account.setServiceType(spec.getServiceType());
            account.setDurationDays(spec.getDurationDays());
            account.setAccountSilenceDays(spec.getAccountSilenceDays());
            account.setHistoryImportBatchId(batch.getId());
            account.setVersion(0L);
            account.setCreatedAt(now);
            account.setUpdatedAt(now);
            AccountSourceInvariant.validate(account);
            return account;
        }).toList();
        if (accountMapper.insertBatch(accounts) != accounts.size()) {
            throw new BusinessException(ErrorCode.HISTORY_IMPORT_STATE_INCONSISTENT,
                    "历史账号批量入账数量不一致");
        }
        if (batchMapper.complete(batch.getId(), now) != 1) {
            throw new BusinessException(ErrorCode.HISTORY_IMPORT_STATE_INCONSISTENT,
                    "历史账号导入批次完成状态更新失败");
        }
        batch.setStatus("COMPLETED");
        batch.setCompletedAt(now);
        batch.setUpdatedAt(now);
        return batch;
    }

    private void validateBatchIdentities(List<HistoryAccountIdentity> accounts) {
        Set<Long> ids = accounts.stream().map(HistoryAccountIdentity::id).collect(Collectors.toSet());
        Set<String> names = accounts.stream().map(HistoryAccountIdentity::name).collect(Collectors.toSet());
        if (ids.size() != accounts.size()) {
            throw new BusinessException(ErrorCode.HISTORY_IMPORT_IDENTITY_CONFLICT,
                    "历史账号批内存在重复 CORS id");
        }
        if (names.size() != accounts.size()) {
            throw new BusinessException(ErrorCode.HISTORY_IMPORT_IDENTITY_CONFLICT,
                    "历史账号批内存在重复账号名称");
        }
    }
}
