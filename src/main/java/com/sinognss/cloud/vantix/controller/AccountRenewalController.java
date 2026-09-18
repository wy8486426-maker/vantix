package com.sinognss.cloud.vantix.controller;

import com.sinognss.cloud.vantix.application.renewal.AccountRenewalReserveService;
import com.sinognss.cloud.vantix.application.renewal.CreateAccountRenewalCommand;
import com.sinognss.cloud.vantix.common.api.CommonResultAdapter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account-renewals")
@ConditionalOnProperty(prefix = "vantix.cors.renewal", name = "enabled", havingValue = "true")
public class AccountRenewalController {
    private final AccountRenewalReserveService reserveService;

    public AccountRenewalController(AccountRenewalReserveService reserveService) {
        this.reserveService = reserveService;
    }

    @PostMapping("/create")
    public Object create(@Valid @RequestBody CreateRequest request) {
        return CommonResultAdapter.success(reserveService.reserve(new CreateAccountRenewalCommand(
                request.requestId(), request.serviceAccountId(), request.serviceCodeId())));
    }

    public record CreateRequest(@NotBlank @Size(max = 128) String requestId,
                                @NotNull @Positive Long serviceAccountId,
                                @NotNull @Positive Long serviceCodeId) {
    }
}
