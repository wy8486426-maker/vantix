package com.sinognss.cloud.vantix.application.testaccount;

import com.sinognss.cloud.vantix.application.account.AccountIdentityView;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.common.user.UserScope;
import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.domain.account.TestAccountIssueBatch;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.TestAccountIssueBatchMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TestAccountIssueQueryService {
    private final TestAccountIssueBatchMapper batchMapper;
    private final ServiceAccountMapper accountMapper;
    private final UserHolderBridge userHolder;

    public TestAccountIssueQueryService(TestAccountIssueBatchMapper batchMapper,
                                        ServiceAccountMapper accountMapper,
                                        UserHolderBridge userHolder) {
        this.batchMapper = batchMapper;
        this.accountMapper = accountMapper;
        this.userHolder = userHolder;
    }

    public TestAccountIssueView get(String inputRequestId) {
        assertGlobal();
        String requestId = normalizeRequestId(inputRequestId);
        TestAccountIssueBatch batch = batchMapper.selectByRequestId(requestId);
        if (batch == null) throw new BusinessException(ErrorCode.NOT_FOUND, "测试账号下发请求不存在: " + requestId);
        List<AccountIdentityView> accounts = batch.getStatus().equals(TestAccountIssueConstants.COMPLETED)
                ? accountMapper.selectByTestIssueBatchId(batch.getId()).stream()
                .map(TestAccountIssueQueryService::toIdentity).toList() : List.of();
        return new TestAccountIssueView(batch.getRequestId(), batch.getIssueBatchNo(), batch.getOwnerCompanyId(),
                batch.getSpecCode(), batch.getDurationDays(), batch.getQuantity(), batch.getStatus(),
                batch.getCreatedAt(), batch.getCompletedAt(), accounts);
    }

    private void assertGlobal() {
        UserScope scope = userHolder.getUserScope();
        if (scope == null || !scope.isSupported()) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_USER_SCOPE, "当前用户数据范围不受支持");
        }
        if (!scope.isGlobal()) {
            throw new BusinessException(ErrorCode.GLOBAL_SCOPE_REQUIRED, "测试账号查询仅允许 GLOBAL 数据范围");
        }
    }

    private static String normalizeRequestId(String value) {
        if (value == null) throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "requestId 非法");
        String normalized = value.trim();
        if (normalized.isBlank() || normalized.length() > 128
                || normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "requestId 非法");
        }
        return normalized;
    }

    private static AccountIdentityView toIdentity(ServiceAccount account) {
        try {
            return new AccountIdentityView(Long.valueOf(account.getCorsAccountId()), account.getAccount());
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.TEST_ACCOUNT_ISSUE_STATE_INCONSISTENT,
                    "测试账号本地 CORS identity 无效");
        }
    }
}
