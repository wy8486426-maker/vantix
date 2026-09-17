package com.sinognss.cloud.vantix.controller;

import com.sinognss.cloud.vantix.application.password.AccountPasswordOperationService;
import com.sinognss.cloud.vantix.application.password.reset.AccountPasswordResetQueryService;
import com.sinognss.cloud.vantix.common.api.CommonResultAdapter;
import com.sinognss.cloud.vantix.integration.cors.account.CorsPasswordGateway;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(prefix = "vantix.cors.password", name = "enabled", havingValue = "true")
@ConditionalOnBean(CorsPasswordGateway.class)
public class AccountPasswordResetController {
    private final AccountPasswordOperationService passwordService;
    private final AccountPasswordResetQueryService queryService;

    public AccountPasswordResetController(AccountPasswordOperationService passwordService,
                                          AccountPasswordResetQueryService queryService) {
        this.passwordService = passwordService;
        this.queryService = queryService;
    }

    @PostMapping("/api/service-accounts/password/reset")
    public Object reset(@RequestParam Long serviceAccountId, @Valid @RequestBody ResetRequest request) {
        return CommonResultAdapter.success(passwordService.reset(serviceAccountId, request.requestId()));
    }

    @PostMapping("/api/service-accounts/password/custom")
    public Object custom(@RequestParam Long serviceAccountId,
                         @Valid @RequestBody CustomRequest request) {
        passwordService.custom(serviceAccountId, request.password());
        return CommonResultAdapter.success(null);
    }

    @GetMapping("/api/account-password-resets/result")
    public Object get(@RequestParam String requestId) {
        return CommonResultAdapter.success(queryService.get(requestId));
    }

    /** Existing Vantix request shape remains; requestId is local-only and is not sent to CORS. */
    public record ResetRequest(@NotNull @NotBlank @Size(max = 128) String requestId) {
    }

    public record CustomRequest(@NotNull @NotBlank @Size(max = 4096) String password) {
        @Override
        public String toString() {
            return "CustomRequest[password=REDACTED]";
        }
    }
}
