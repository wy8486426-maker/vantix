package com.sinognss.cloud.vantix.application.password.reveal;

import com.sinognss.cloud.vantix.application.password.AccountPasswordAccessPolicy;
import com.sinognss.cloud.vantix.application.password.AccountPasswordRequestIds;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.OperatorIdentity;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.domain.password.AccountPasswordAction;
import com.sinognss.cloud.vantix.domain.password.AccountPasswordActionConstants;
import com.sinognss.cloud.vantix.infrastructure.mapper.AccountPasswordActionMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** Short database transactions for the reveal audit trail. */
public class AccountPasswordRevealAuditService {
    private final AccountPasswordActionMapper actionMapper;
    private final ServiceAccountMapper accountMapper;
    private final UserHolderBridge userHolder;
    private final Clock clock;

    public AccountPasswordRevealAuditService(AccountPasswordActionMapper actionMapper,
                                             ServiceAccountMapper accountMapper,
                                             UserHolderBridge userHolder,
                                             Clock clock) {
        this.actionMapper = actionMapper;
        this.accountMapper = accountMapper;
        this.userHolder = userHolder;
        this.clock = clock;
    }

    @Transactional
    public AccountPasswordAction reserve(Long serviceAccountId, String inputRequestId) {
        if (serviceAccountId == null || serviceAccountId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "serviceAccountId 非法");
        }
        String requestId = AccountPasswordRequestIds.normalize(inputRequestId);
        ServiceAccount account = accountMapper.selectByIdForUpdate(serviceAccountId);
        if (account == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "账号不存在");
        }
        AccountPasswordAccessPolicy.assertAccess(userHolder.getUserScope(),
                account.getOwnerCompanyId(), account.getAssignedUserId());
        requireAccountIdentity(account);
        if (actionMapper.selectByRequestId(requestId) != null) {
            throw new BusinessException(ErrorCode.PASSWORD_REVEAL_REQUEST_ALREADY_USED,
                    "该查看请求已使用，请生成新的 requestId");
        }

        OperatorIdentity operator = userHolder.getOperator();
        LocalDateTime now = now();
        AccountPasswordAction action = new AccountPasswordAction();
        action.setRequestId(requestId);
        action.setActionType(AccountPasswordActionConstants.REVEAL);
        action.setServiceAccountId(account.getId());
        action.setOwnerCompanyId(account.getOwnerCompanyId());
        action.setAssignedUserId(account.getAssignedUserId());
        action.setCorsAccountId(account.getCorsAccountId());
        action.setAccount(account.getAccount());
        action.setStatus(AccountPasswordActionConstants.PROCESSING);
        action.setOperatorUserId(operator.userId());
        action.setOperatorUserName(operator.userName());
        action.setVersion(0L);
        action.setCreatedAt(now);
        action.setUpdatedAt(now);
        if (actionMapper.insert(action) != 1 || action.getId() == null) {
            throw new IllegalStateException("Password reveal audit could not be reserved");
        }
        return action;
    }

    @Transactional
    public void complete(Long actionId, Long expectedVersion) {
        LocalDateTime now = now();
        requireTransition(actionMapper.transitionFromProcessing(actionId, expectedVersion,
                AccountPasswordActionConstants.SUCCEEDED, null, null, now, now, null));
    }

    @Transactional
    public void fail(Long actionId, Long expectedVersion) {
        LocalDateTime now = now();
        requireTransition(actionMapper.transitionFromProcessing(actionId, expectedVersion,
                AccountPasswordActionConstants.FAILED, "PASSWORD_REVEAL_FAILED",
                "Password reveal did not complete", now, now, null));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
    }

    private static void requireAccountIdentity(ServiceAccount account) {
        if (!StringUtils.hasText(account.getCorsAccountId()) || !StringUtils.hasText(account.getAccount())) {
            throw new BusinessException(ErrorCode.PASSWORD_REVEAL_FAILED, "账号信息不完整，无法查看密码");
        }
    }

    private static void requireTransition(int rows) {
        if (rows != 1) {
            throw new IllegalStateException("Password reveal audit state changed before completion");
        }
    }
}
