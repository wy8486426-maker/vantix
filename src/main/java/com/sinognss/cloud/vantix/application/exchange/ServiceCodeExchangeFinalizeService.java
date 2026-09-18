package com.sinognss.cloud.vantix.application.exchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sinognss.cloud.vantix.application.cors.CorsOperationStateService;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.domain.account.AccountSource;
import com.sinognss.cloud.vantix.domain.account.AccountSourceInvariant;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.domain.exchange.ExchangeBatch;
import com.sinognss.cloud.vantix.domain.exchange.ExchangeDetail;
import com.sinognss.cloud.vantix.domain.exchange.ExchangeStatus;
import com.sinognss.cloud.vantix.integration.cors.CorsBatchResult;
import com.sinognss.cloud.vantix.integration.cors.CorsCreatedAccount;
import com.sinognss.cloud.vantix.integration.cors.CorsOutcome;
import com.sinognss.cloud.vantix.infrastructure.mapper.CorsOperationMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ExchangeBatchMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ExchangeDetailMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ServiceCodeExchangeFinalizeService {
    private static final int INSERT_CHUNK_SIZE = 500;
    private final ExchangeBatchMapper batchMapper;
    private final ExchangeDetailMapper detailMapper;
    private final ServiceAccountMapper accountMapper;
    private final ServiceCodeMapper serviceCodeMapper;
    private final CorsOperationMapper operationMapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ServiceCodeExchangeFinalizeService(ExchangeBatchMapper batchMapper,
                                              ExchangeDetailMapper detailMapper,
                                              ServiceAccountMapper accountMapper,
                                              ServiceCodeMapper serviceCodeMapper,
                                              CorsOperationMapper operationMapper,
                                              ObjectMapper objectMapper,
                                              Clock clock) {
        this.batchMapper = batchMapper;
        this.detailMapper = detailMapper;
        this.accountMapper = accountMapper;
        this.serviceCodeMapper = serviceCodeMapper;
        this.operationMapper = operationMapper;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public boolean finalizeSuccess(Long operationId, Long claimedVersion, CorsBatchResult result) {
        CorsOperation operation = operationMapper.selectById(operationId);
        if (operation == null || !"CLAIMED".equals(operation.getStatus())
                || !claimedVersion.equals(operation.getVersion())) {
            return false;
        }
        ExchangeBatch batch = batchMapper.selectByIdForUpdate(operation.getBizId());
        if (batch == null || !batch.getId().equals(operation.getBizId())) {
            throw new BusinessException(ErrorCode.EXCHANGE_STATE_INCONSISTENT,
                    "兑换批次与 CORS 操作关系不一致");
        }
        if (batch.getStatus() == ExchangeStatus.COMPLETED) return true;
        if (batch.getStatus() != ExchangeStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.EXCHANGE_STATE_INCONSISTENT,
                    "兑换批次当前不能 finalize");
        }

        List<CorsCreatedAccount> corsAccounts = validateResponse(operation, batch, result);
        List<ExchangeDetail> details = detailMapper.selectByBatchId(batch.getId());
        validateDetails(batch, details);
        LocalDateTime now = LocalDateTime.now(clock);
        List<ServiceAccount> accounts = new ArrayList<>(batch.getQuantity());
        List<ExchangeDetailMapper.CompletedAccountRow> completedRows = new ArrayList<>(batch.getQuantity());
        for (int i = 0; i < batch.getQuantity(); i++) {
            ExchangeDetail detail = details.get(i);
            CorsCreatedAccount corsAccount = corsAccounts.get(i);
            ExchangeCodeSnapshot snapshot = deserialize(detail.getServiceCodeSnapshot());
            if (!detail.getServiceCodeId().equals(snapshot.serviceCodeId())
                    || !batch.getOwnerCompanyId().equals(snapshot.ownerCompanyId())
                    || (snapshot.specCode() != null && !batch.getSpecCode().equals(snapshot.specCode()))
                    || (!ServiceCodeExchangeReserveService.EXACT_GENERATION_SOURCE.equals(batch.getGenerationSource())
                    && !batch.getDurationDays().equals(snapshot.durationDays()))
                    || !java.util.Objects.equals(batch.getAssignedUserId(), snapshot.assignedUserId())) {
                throw new BusinessException(ErrorCode.EXCHANGE_STATE_INCONSISTENT,
                        "兑换明细快照与批次不匹配");
            }
            accounts.add(toServiceAccount(batch, detail, snapshot, corsAccount, now));
            completedRows.add(new ExchangeDetailMapper.CompletedAccountRow(
                    detail.getDetailIndex(), String.valueOf(corsAccount.id()), corsAccount.name()));
        }

        for (int from = 0; from < accounts.size(); from += INSERT_CHUNK_SIZE) {
            List<ServiceAccount> chunk = accounts.subList(from, Math.min(from + INSERT_CHUNK_SIZE,
                    accounts.size()));
            if (accountMapper.insertBatch(chunk) != chunk.size()) {
                throw new BusinessException(ErrorCode.EXCHANGE_STATE_INCONSISTENT,
                        "账号批量入账数量不一致");
            }
        }
        for (int from = 0; from < completedRows.size(); from += INSERT_CHUNK_SIZE) {
            List<ExchangeDetailMapper.CompletedAccountRow> chunk = completedRows.subList(from,
                    Math.min(from + INSERT_CHUNK_SIZE, completedRows.size()));
            if (detailMapper.completeBatchDetails(batch.getId(), chunk, now) != chunk.size()) {
                throw new BusinessException(ErrorCode.EXCHANGE_STATE_INCONSISTENT,
                        "兑换明细 finalize 数量不一致");
            }
        }

        List<Long> ids = details.stream().map(ExchangeDetail::getServiceCodeId).toList();
        if (serviceCodeMapper.consumeForExchange(ids, batch.getRequestId(), now) != batch.getQuantity()) {
            throw new BusinessException(ErrorCode.EXCHANGE_STATE_INCONSISTENT,
                    "兑换服务码消费数量不一致");
        }
        if (batchMapper.complete(batch.getId(), now) != 1) {
            throw new BusinessException(ErrorCode.EXCHANGE_STATE_INCONSISTENT,
                    "兑换批次完成状态更新失败");
        }
        if (operationMapper.markSucceeded(operation.getId(), operation.getVersion(), now) != 1) {
            throw new BusinessException(ErrorCode.EXCHANGE_STATE_INCONSISTENT,
                    "CORS 操作完成状态更新失败");
        }
        return true;
    }

    private List<CorsCreatedAccount> validateResponse(CorsOperation operation, ExchangeBatch batch,
                                                       CorsBatchResult result) {
        if (result == null || result.outcome() != CorsOutcome.SUCCESS
                || !operation.getRequestId().equals(result.requestId())
                || result.data() == null
                || !result.data().hasValidAccounts(batch.getQuantity())) {
            throw new BusinessException(ErrorCode.EXCHANGE_STATE_INCONSISTENT,
                    "CORS 成功响应的 requestId 或 accounts 无效");
        }
        return result.data().accounts();
    }

    private void validateDetails(ExchangeBatch batch, List<ExchangeDetail> details) {
        if (details == null || details.size() != batch.getQuantity()) {
            throw new BusinessException(ErrorCode.EXCHANGE_STATE_INCONSISTENT,
                    "兑换明细数量与批次不一致");
        }
        for (int i = 0; i < details.size(); i++) {
            ExchangeDetail detail = details.get(i);
            if (detail.getDetailIndex() != i + 1 || detail.getStatus() != ExchangeStatus.PROCESSING
                    || detail.getServiceCodeId() == null) {
                throw new BusinessException(ErrorCode.EXCHANGE_STATE_INCONSISTENT,
                        "兑换明细 index 或状态不一致");
            }
        }
    }

    private ExchangeCodeSnapshot deserialize(String value) {
        try {
            return objectMapper.readValue(value, ExchangeCodeSnapshot.class);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.EXCHANGE_STATE_INCONSISTENT,
                    "兑换服务码快照无法读取");
        }
    }

    private ServiceAccount toServiceAccount(ExchangeBatch batch, ExchangeDetail detail,
                                            ExchangeCodeSnapshot snapshot, CorsCreatedAccount corsAccount,
                                            LocalDateTime now) {
        if (snapshot.serviceType() == null || snapshot.durationDays() == null
                || snapshot.durationDays() <= 0 || snapshot.codeSilenceDays() == null
                || snapshot.codeSilenceDays() < 0 || snapshot.assignedUserId() != null
                && snapshot.assignedUserId() <= 0) {
            throw new BusinessException(ErrorCode.EXCHANGE_STATE_INCONSISTENT,
                    "兑换服务码快照内容无效");
        }
        ServiceAccount account = new ServiceAccount();
        account.setAccountSource(AccountSource.EXCHANGE);
        account.setCorsAccountId(String.valueOf(corsAccount.id()));
        account.setAccount(corsAccount.name());
        account.setOwnerCompanyId(snapshot.ownerCompanyId());
        account.setAssignedUserId(batch.getAssignedUserId());
        account.setSourceServiceCodeId(detail.getServiceCodeId());
        account.setExchangeBatchId(batch.getId());
        account.setExchangeDetailId(detail.getId());
        account.setSpecCode(snapshot.specCode() == null ? batch.getSpecCode() : snapshot.specCode());
        account.setDisplayName(batch.getDisplayName());
        account.setServiceType(snapshot.serviceType());
        account.setDurationDays(snapshot.durationDays());
        account.setAccountSilenceDays(batch.getAccountSilenceDays());
        account.setExchangeAt(now);
        account.setLastSyncAt(now);
        account.setVersion(0L);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        AccountSourceInvariant.validate(account);
        return account;
    }

}
