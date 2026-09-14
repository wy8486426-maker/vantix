package com.sinognss.cloud.vantix.application.password.reset;

import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.domain.password.AccountPasswordAction;
import com.sinognss.cloud.vantix.domain.password.AccountPasswordActionConstants;
import com.sinognss.cloud.vantix.infrastructure.mapper.AccountPasswordActionMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.CorsOperationMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceAccountMapper;
import com.sinognss.cloud.vantix.integration.cors.CorsOutcome;
import com.sinognss.cloud.vantix.integration.cors.account.CorsPasswordResetResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class AccountPasswordResetFinalizeService {
    private final CorsOperationMapper operationMapper;
    private final AccountPasswordActionMapper actionMapper;
    private final ServiceAccountMapper serviceAccountMapper;
    private final Clock clock;

    public AccountPasswordResetFinalizeService(CorsOperationMapper operationMapper,
                                              AccountPasswordActionMapper actionMapper,
                                              ServiceAccountMapper serviceAccountMapper,
                                              Clock clock) {
        this.operationMapper = operationMapper;
        this.actionMapper = actionMapper;
        this.serviceAccountMapper = serviceAccountMapper;
        this.clock = clock;
    }

    @Transactional
    public void finalizeSuccess(CorsOperation claimedOperation,
                                AccountPasswordAction claimedAction,
                                CorsPasswordResetResult result) {
        if (claimedOperation == null || claimedOperation.getId() == null
                || claimedOperation.getVersion() == null || claimedAction == null
                || claimedAction.getId() == null || claimedAction.getVersion() == null) {
            throw new IllegalStateException("Claimed password reset identity is unavailable");
        }
        CorsOperation operation = operationMapper.selectByIdForUpdate(claimedOperation.getId());
        if (!sameClaim(operation, claimedOperation)) {
            throw new IllegalStateException("Claimed password reset operation changed before finalize");
        }
        AccountPasswordAction action = actionMapper.selectByIdForUpdate(operation.getBizId());
        if (!hasActionIdentity(operation, action)
                || !claimedAction.getId().equals(action.getId())
                || !claimedAction.getVersion().equals(action.getVersion())) {
            throw new IllegalStateException("Password reset audit action changed before finalize");
        }
        ServiceAccount account = serviceAccountMapper.selectByIdForUpdate(action.getServiceAccountId());
        if (account == null || account.getId() == null
                || !account.getId().equals(action.getServiceAccountId())
                || !same(action.getCorsAccountId(), account.getCorsAccountId())
                || !same(action.getAccount(), account.getAccount())) {
            throw new IllegalStateException("Password reset account identity changed before finalize");
        }
        if (result == null || result.outcome() != CorsOutcome.SUCCESS
                || !same(operation.getRequestId(), result.requestId())
                || !same(action.getRequestId(), result.requestId())
                || !same(account.getCorsAccountId(), result.accountId())) {
            throw new IllegalStateException("Password reset success correlation is invalid");
        }

        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
        requireOne(actionMapper.transitionFromProcessing(action.getId(), action.getVersion(),
                AccountPasswordActionConstants.SUCCEEDED, null, null, now, now),
                "Password reset audit action could not be completed");
        requireOne(operationMapper.markSucceeded(operation.getId(), operation.getVersion(), now),
                "Password reset operation could not be completed");
    }

    private static boolean sameClaim(CorsOperation current, CorsOperation claimed) {
        return AccountPasswordResetConstants.isOperation(current, claimed.getBizId(), claimed.getServiceAccountId())
                && AccountPasswordResetConstants.CLAIMED.equals(current.getStatus())
                && current.getId().equals(claimed.getId())
                && current.getVersion() != null && current.getVersion().equals(claimed.getVersion())
                && same(current.getRequestId(), claimed.getRequestId());
    }

    private static boolean hasActionIdentity(CorsOperation operation, AccountPasswordAction action) {
        return action != null && action.getId() != null && operation.getBizId().equals(action.getId())
                && AccountPasswordResetConstants.isAction(action, operation.getServiceAccountId(),
                operation.getRequestId())
                && action.getVersion() != null;
    }

    private static boolean same(String left, String right) {
        return left != null && left.equals(right);
    }

    private static void requireOne(int changed, String message) {
        if (changed != 1) {
            throw new IllegalStateException(message);
        }
    }
}
