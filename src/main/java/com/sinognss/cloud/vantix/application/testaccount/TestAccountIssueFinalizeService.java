package com.sinognss.cloud.vantix.application.testaccount;

import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.domain.account.AccountSource;
import com.sinognss.cloud.vantix.domain.account.AccountSourceInvariant;
import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.domain.account.TestAccountIssueBatch;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.infrastructure.mapper.CorsOperationMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.TestAccountIssueBatchMapper;
import com.sinognss.cloud.vantix.integration.cors.CorsBatchResult;
import com.sinognss.cloud.vantix.integration.cors.CorsCreatedAccount;
import com.sinognss.cloud.vantix.integration.cors.CorsOutcome;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class TestAccountIssueFinalizeService {
    private final TestAccountIssueBatchMapper batchMapper;
    private final ServiceAccountMapper accountMapper;
    private final CorsOperationMapper operationMapper;
    private final Clock clock;

    public TestAccountIssueFinalizeService(TestAccountIssueBatchMapper batchMapper,
                                           ServiceAccountMapper accountMapper,
                                           CorsOperationMapper operationMapper,
                                           Clock clock) {
        this.batchMapper = batchMapper;
        this.accountMapper = accountMapper;
        this.operationMapper = operationMapper;
        this.clock = clock;
    }

    @Transactional
    public boolean finalizeSuccess(Long operationId, Long claimedVersion, CorsBatchResult result) {
        CorsOperation claimed = operationMapper.selectById(operationId);
        if (claimed == null || !TestAccountIssueConstants.CLAIMED.equals(claimed.getStatus())
                || !claimedVersion.equals(claimed.getVersion())) return false;
        TestAccountIssueBatch batch = batchMapper.selectByIdForUpdate(claimed.getBizId());
        if (batch == null || !claimed.getBizId().equals(batch.getId())) {
            throw new BusinessException(ErrorCode.TEST_ACCOUNT_ISSUE_STATE_INCONSISTENT,
                    "测试账号批次与 CORS 操作关系不一致");
        }
        CorsOperation current = operationMapper.selectByIdForUpdate(operationId);
        if (current == null || !TestAccountIssueConstants.CLAIMED.equals(current.getStatus())
                || !claimedVersion.equals(current.getVersion())) return false;
        if (TestAccountIssueConstants.COMPLETED.equals(batch.getStatus())) return true;
        if (!TestAccountIssueConstants.PROCESSING.equals(batch.getStatus())) {
            throw new BusinessException(ErrorCode.TEST_ACCOUNT_ISSUE_STATE_INCONSISTENT,
                    "测试账号批次当前不能 finalize");
        }

        List<CorsCreatedAccount> corsAccounts = validateResponse(current, batch, result);
        LocalDateTime now = LocalDateTime.now(clock);
        List<ServiceAccount> accounts = new ArrayList<>(batch.getQuantity());
        for (CorsCreatedAccount corsAccount : corsAccounts) {
            ServiceAccount account = new ServiceAccount();
            account.setAccountSource(AccountSource.TEST);
            account.setCorsAccountId(String.valueOf(corsAccount.id()));
            account.setAccount(corsAccount.name());
            account.setOwnerCompanyId(batch.getOwnerCompanyId());
            account.setAssignedUserId(null);
            account.setSpecCode(batch.getSpecCode());
            account.setServiceType(batch.getServiceType());
            account.setDurationDays(batch.getDurationDays());
            account.setAccountSilenceDays(batch.getAccountSilenceDays());
            account.setTestIssueBatchId(batch.getId());
            account.setVersion(0L);
            account.setCreatedAt(now);
            account.setUpdatedAt(now);
            AccountSourceInvariant.validate(account);
            accounts.add(account);
        }
        if (accountMapper.insertBatch(accounts) != accounts.size()) {
            throw new BusinessException(ErrorCode.TEST_ACCOUNT_ISSUE_STATE_INCONSISTENT,
                    "测试账号批量入账数量不一致");
        }
        if (batchMapper.complete(batch.getId(), now) != 1) {
            throw new BusinessException(ErrorCode.TEST_ACCOUNT_ISSUE_STATE_INCONSISTENT,
                    "测试账号批次完成状态更新失败");
        }
        if (operationMapper.markSucceeded(current.getId(), current.getVersion(), now) != 1) {
            throw new BusinessException(ErrorCode.TEST_ACCOUNT_ISSUE_STATE_INCONSISTENT,
                    "测试账号 CORS 操作完成状态更新失败");
        }
        return true;
    }

    private List<CorsCreatedAccount> validateResponse(CorsOperation operation,
                                                      TestAccountIssueBatch batch,
                                                      CorsBatchResult result) {
        if (result == null || result.outcome() != CorsOutcome.SUCCESS
                || !operation.getRequestId().equals(result.requestId()) || result.data() == null
                || !result.data().hasValidAccounts(batch.getQuantity())) {
            throw new BusinessException(ErrorCode.TEST_ACCOUNT_ISSUE_STATE_INCONSISTENT,
                    "CORS 测试账号成功响应的 requestId 或 accounts 无效");
        }
        return result.data().accounts();
    }
}
