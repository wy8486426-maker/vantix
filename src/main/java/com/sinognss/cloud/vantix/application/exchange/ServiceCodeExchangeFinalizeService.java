package com.sinognss.cloud.vantix.application.exchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sinognss.cloud.vantix.application.cors.CorsOperationStateService;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.domain.exchange.ExchangeBatch;
import com.sinognss.cloud.vantix.domain.exchange.ExchangeDetail;
import com.sinognss.cloud.vantix.domain.exchange.ExchangeStatus;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCode;
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
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ServiceCodeExchangeFinalizeService {
    private static final int INSERT_CHUNK_SIZE = 500;
    private static final ZoneId CORS_ZONE = ZoneId.of("Asia/Shanghai");

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
        ExchangeBatch batch = batchMapper.selectByRequestIdForUpdate(operation.getRequestId());
        if (batch == null || !batch.getId().equals(operation.getBizId())) {
            throw new BusinessException(ErrorCode.EXCHANGE_STATE_INCONSISTENT,
                    "兑换批次与 CORS 操作关系不一致");
        }
        if (batch.getStatus() == ExchangeStatus.COMPLETED) return true;
        if (batch.getStatus() != ExchangeStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.EXCHANGE_STATE_INCONSISTENT,
                    "兑换批次当前不能 finalize");
        }

        List<CorsCreatedAccount> corsAccounts = validateResponse(batch, result);
        List<ExchangeDetail> details = detailMapper.selectByBatchId(batch.getId());
        validateDetails(batch, details);
        LocalDateTime now = LocalDateTime.now(clock);
        List<ServiceAccount> accounts = new ArrayList<>(batch.getQuantity());
        List<ExchangeDetailMapper.CompletedAccountRow> completedRows = new ArrayList<>(batch.getQuantity());
        for (int i = 0; i < batch.getQuantity(); i++) {
            ExchangeDetail detail = details.get(i);
            CorsCreatedAccount cors = corsAccounts.get(i);
            ExchangeCodeSnapshot snapshot = deserialize(detail.getServiceCodeSnapshot());
            if (!detail.getServiceCodeId().equals(snapshot.serviceCodeId())
                    || !batch.getOwnerCompanyId().equals(snapshot.ownerCompanyId())
                    || (snapshot.specCode() != null && !batch.getSpecCode().equals(snapshot.specCode()))
                    || !batch.getDurationDays().equals(snapshot.durationDays())
                    || !java.util.Objects.equals(batch.getAssignedUserId(), snapshot.assignedUserId())) {
                throw new BusinessException(ErrorCode.EXCHANGE_STATE_INCONSISTENT,
                        "兑换明细快照与批次不匹配");
            }
            accounts.add(toServiceAccount(batch, detail, snapshot, cors, now));
            completedRows.add(new ExchangeDetailMapper.CompletedAccountRow(
                    detail.getDetailIndex(), cors.accountId(), cors.account()));
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

    private List<CorsCreatedAccount> validateResponse(ExchangeBatch batch, CorsBatchResult result) {
        if (result == null || result.outcome() != CorsOutcome.SUCCESS
                || !batch.getRequestId().equals(result.requestId())
                || result.accounts() == null || result.accounts().size() != batch.getQuantity()) {
            throw new BusinessException(ErrorCode.EXCHANGE_STATE_INCONSISTENT,
                    "CORS 成功响应的 requestId 或账号数量无效");
        }
        CorsCreatedAccount[] byIndex = new CorsCreatedAccount[batch.getQuantity()];
        Set<String> accountIds = new HashSet<>();
        Set<String> accountNames = new HashSet<>();
        for (CorsCreatedAccount account : result.accounts()) {
            if (account == null || account.index() < 1 || account.index() > batch.getQuantity()
                    || byIndex[account.index() - 1] != null
                    || blank(account.accountId()) || !accountIds.add(account.accountId())
                    || blank(account.account()) || !accountNames.add(account.account())
                    || blank(account.accountStatus()) || blank(account.activationStatus())
                    || account.createdAt() == null || account.updatedAt() == null) {
                throw new BusinessException(ErrorCode.EXCHANGE_STATE_INCONSISTENT,
                        "CORS 成功响应的账号明细无效");
            }
            byIndex[account.index() - 1] = account;
        }
        for (CorsCreatedAccount account : byIndex) {
            if (account == null) {
                throw new BusinessException(ErrorCode.EXCHANGE_STATE_INCONSISTENT,
                        "CORS 成功响应的账号 index 不连续");
            }
        }
        return List.of(byIndex);
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
                                            ExchangeCodeSnapshot snapshot, CorsCreatedAccount cors,
                                            LocalDateTime now) {
        if (snapshot.serviceType() == null || snapshot.durationDays() == null
                || snapshot.durationDays() <= 0 || snapshot.codeSilenceDays() == null
                || snapshot.codeSilenceDays() < 0 || snapshot.assignedUserId() != null
                && snapshot.assignedUserId() <= 0) {
            throw new BusinessException(ErrorCode.EXCHANGE_STATE_INCONSISTENT,
                    "兑换服务码快照内容无效");
        }
        LocalDateTime activatedAt = local(cors.activatedAt());
        String activationStatus = cors.activationStatus();
        ServiceAccount account = new ServiceAccount();
        account.setCorsAccountId(cors.accountId());
        account.setAccount(cors.account());
        account.setOwnerCompanyId(snapshot.ownerCompanyId());
        account.setAssignedUserId(batch.getAssignedUserId());
        account.setSourceServiceCodeId(detail.getServiceCodeId());
        account.setExchangeBatchId(batch.getId());
        account.setExchangeDetailId(detail.getId());
        account.setSpecCode(snapshot.specCode() == null ? batch.getSpecCode() : snapshot.specCode());
        account.setServiceType(snapshot.serviceType());
        account.setDurationDays(snapshot.durationDays());
        account.setAccountSilenceDays(batch.getAccountSilenceDays());
        account.setExchangeAt(now);
        account.setCorsStatus(cors.accountStatus());
        account.setCorsActivationStatus(activationStatus);
        account.setActivatedAt(activatedAt);
        account.setExpireAt(local(cors.expireAt()));
        account.setCorsCreatedAt(local(cors.createdAt()));
        account.setCorsUpdatedAt(local(cors.updatedAt()));
        account.setLastSyncAt(now);
        account.setVersion(0L);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        return account;
    }

    private static LocalDateTime local(OffsetDateTime value) {
        return value == null ? null : value.atZoneSameInstant(CORS_ZONE).toLocalDateTime();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
