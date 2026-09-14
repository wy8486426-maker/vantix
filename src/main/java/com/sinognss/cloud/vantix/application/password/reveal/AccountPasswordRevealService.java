package com.sinognss.cloud.vantix.application.password.reveal;

import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.domain.password.AccountPasswordAction;
import com.sinognss.cloud.vantix.integration.cors.CorsOutcome;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountPasswordGateway;
import com.sinognss.cloud.vantix.integration.cors.account.CorsPasswordRevealRequest;
import com.sinognss.cloud.vantix.integration.cors.account.CorsPasswordRevealResult;
import com.sinognss.cloud.vantix.integration.cors.account.PasswordRevealSecret;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class AccountPasswordRevealService {
    private static final int MAX_PASSWORD_LENGTH = 4096;

    private final AccountPasswordRevealAuditService auditService;
    private final CorsAccountPasswordGateway gateway;

    public AccountPasswordRevealService(AccountPasswordRevealAuditService auditService,
                                        CorsAccountPasswordGateway gateway) {
        this.auditService = auditService;
        this.gateway = gateway;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PasswordRevealResponse reveal(Long serviceAccountId, String requestId) {
        AccountPasswordAction action;
        try {
            action = auditService.reserve(serviceAccountId, requestId);
        } catch (DuplicateKeyException duplicate) {
            throw alreadyUsed();
        }

        CorsPasswordRevealResult result;
        try {
            result = gateway.revealPassword(new CorsPasswordRevealRequest(
                    action.getRequestId(), action.getCorsAccountId()));
        } catch (RuntimeException ignored) {
            auditService.fail(action.getId(), action.getVersion());
            throw revealFailed();
        }

        PasswordRevealSecret secret = validSecret(result, action) ? result.getSecret() : null;
        if (secret == null) {
            auditService.fail(action.getId(), action.getVersion());
            throw revealFailed();
        }

        // A secret is only eligible for the HTTP response after the audit commit succeeds.
        auditService.complete(action.getId(), action.getVersion());
        return new PasswordRevealResponse(action.getServiceAccountId(), action.getAccount(), secret.getPassword());
    }

    private static boolean validSecret(CorsPasswordRevealResult result, AccountPasswordAction action) {
        if (result == null || result.getOutcome() != CorsOutcome.SUCCESS
                || !action.getRequestId().equals(result.getRequestId())
                || !action.getCorsAccountId().equals(result.getAccountId())
                || result.getSecret() == null) {
            return false;
        }
        String password = result.getSecret().getPassword();
        return password != null && !password.isEmpty() && password.length() <= MAX_PASSWORD_LENGTH;
    }

    private static BusinessException alreadyUsed() {
        return new BusinessException(ErrorCode.PASSWORD_REVEAL_REQUEST_ALREADY_USED,
                "该查看请求已使用，请生成新的 requestId");
    }

    private static BusinessException revealFailed() {
        return new BusinessException(ErrorCode.PASSWORD_REVEAL_FAILED, "密码查看失败，请稍后重试");
    }
}
