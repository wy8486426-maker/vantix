package com.sinognss.cloud.vantix.controller;

import com.sinognss.cloud.vantix.application.renewal.AccountRenewalQueryService;
import com.sinognss.cloud.vantix.application.renewal.AccountRenewalReserveService;
import com.sinognss.cloud.vantix.application.renewal.CreateAccountRenewalCommand;
import com.sinognss.cloud.vantix.common.api.CommonResultAdapter;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountRenewalGateway;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountStatusGateway;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account-renewals")
@ConditionalOnProperty(prefix = "vantix.cors.renewal", name = "enabled", havingValue = "true")
@ConditionalOnBean({CorsAccountStatusGateway.class, CorsAccountRenewalGateway.class})
public class AccountRenewalController {
    private final AccountRenewalReserveService reserveService;
    private final AccountRenewalQueryService queryService;

    public AccountRenewalController(AccountRenewalReserveService reserveService,
                                    AccountRenewalQueryService queryService) {
        this.reserveService = reserveService;
        this.queryService = queryService;
    }

    @PostMapping
    public Object create(@Valid @RequestBody CreateRequest request) {
        return CommonResultAdapter.success(reserveService.reserve(new CreateAccountRenewalCommand(
                request.requestId(), request.serviceAccountId(), request.serviceCodeId())));
    }

    @GetMapping("/{requestId}")
    public Object get(@PathVariable String requestId) {
        return CommonResultAdapter.success(queryService.get(requestId));
    }

    public record CreateRequest(@NotBlank @Size(max = 128) String requestId,
                                @NotNull @Positive Long serviceAccountId,
                                @NotNull @Positive Long serviceCodeId) {
    }
}
