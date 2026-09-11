package com.sinognss.cloud.vantix.controller;

import com.sinognss.cloud.vantix.application.config.AccountConfigService;
import com.sinognss.cloud.vantix.application.config.ServiceDurationConfigCommand;
import com.sinognss.cloud.vantix.application.config.ServiceDurationConfigService;
import com.sinognss.cloud.vantix.application.config.SystemConfigService;
import com.sinognss.cloud.vantix.common.api.CommonResultAdapter;
import com.sinognss.cloud.vantix.domain.config.DurationUnit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config")
public class ConfigController {
    private final ServiceDurationConfigService durationService;
    private final AccountConfigService accountConfigService;
    private final SystemConfigService systemConfigService;

    public ConfigController(ServiceDurationConfigService durationService,
                             AccountConfigService accountConfigService,
                             SystemConfigService systemConfigService) {
        this.durationService = durationService;
        this.accountConfigService = accountConfigService;
        this.systemConfigService = systemConfigService;
    }

    @GetMapping("/service-durations")
    public Object durationList() {
        return CommonResultAdapter.success(durationService.list());
    }

    @PostMapping("/service-durations")
    public Object durationCreate(@Valid @RequestBody DurationRequest request) {
        return CommonResultAdapter.success(durationService.create(request.toCommand()));
    }

    @PutMapping("/service-durations/{id}")
    public Object durationUpdate(@PathVariable Long id, @Valid @RequestBody DurationRequest request) {
        return CommonResultAdapter.success(durationService.update(id, request.toCommand()));
    }

    @GetMapping("/account")
    public Object accountConfig() {
        return CommonResultAdapter.success(accountConfigService.get());
    }

    @PutMapping("/account")
    public Object accountConfigUpdate(@Valid @RequestBody AccountConfigRequest request) {
        return CommonResultAdapter.success(accountConfigService.update(request.accountSilenceMonths()));
    }

    @GetMapping("/system-company")
    public Object systemCompany() {
        return CommonResultAdapter.success(systemConfigService.getSystemCompanyId());
    }

    @PutMapping("/system-company")
    public Object updateSystemCompany(@Valid @RequestBody SystemCompanyRequest request) {
        return CommonResultAdapter.success(systemConfigService.update(
                SystemConfigService.SYSTEM_COMPANY_ID_KEY, String.valueOf(request.systemCompanyId())));
    }

    public record DurationRequest(@NotBlank @Size(max = 64) String serviceType,
                                  @NotNull @Positive Integer durationValue,
                                  @NotNull DurationUnit durationUnit,
                                  @NotNull @Min(0) Integer codeSilenceMonths,
                                  @NotNull Boolean enabled,
                                  @Size(max = 512) String remark) {
        ServiceDurationConfigCommand toCommand() {
            return new ServiceDurationConfigCommand(serviceType, durationValue, durationUnit,
                    codeSilenceMonths, enabled, remark);
        }
    }

    public record AccountConfigRequest(@NotNull @Min(0) Integer accountSilenceMonths) {
    }

    public record SystemCompanyRequest(@NotNull @Positive Long systemCompanyId) {
    }
}
