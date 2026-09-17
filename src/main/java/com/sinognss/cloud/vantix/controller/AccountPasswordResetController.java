package com.sinognss.cloud.vantix.controller;

import com.sinognss.cloud.vantix.application.password.reset.AccountPasswordResetQueryService;
import com.sinognss.cloud.vantix.application.password.reset.AccountPasswordResetReserveService;
import com.sinognss.cloud.vantix.common.api.CommonResultAdapter;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountPasswordGateway;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountStatusGateway;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(prefix = "vantix.cors.password", name = "enabled", havingValue = "true")
@ConditionalOnBean({CorsAccountPasswordGateway.class, CorsAccountStatusGateway.class})
public class AccountPasswordResetController {
    private final AccountPasswordResetReserveService reserveService;
    private final AccountPasswordResetQueryService queryService;

    public AccountPasswordResetController(AccountPasswordResetReserveService reserveService,
                                          AccountPasswordResetQueryService queryService) {
        this.reserveService = reserveService;
        this.queryService = queryService;
    }

    @PostMapping("/api/service-accounts/password/reset")
    public Object reset(@RequestParam Long serviceAccountId, @Valid @RequestBody ResetRequest request) {
        return CommonResultAdapter.success(reserveService.reserve(request.requestId(), serviceAccountId));
    }

    @GetMapping("/api/account-password-resets/result")
    public Object get(@RequestParam String requestId) {
        return CommonResultAdapter.success(queryService.get(requestId));
    }

    public record ResetRequest(@NotBlank @Size(max = 128) String requestId) {
    }
}
