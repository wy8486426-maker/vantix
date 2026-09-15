package com.sinognss.cloud.vantix.controller;

import com.sinognss.cloud.vantix.application.config.CreateServiceDurationConfigCommand;
import com.sinognss.cloud.vantix.application.config.ServiceDurationConfigService;
import com.sinognss.cloud.vantix.application.config.SystemConfigService;
import com.sinognss.cloud.vantix.application.config.UpdateServiceDurationConfigCommand;
import com.sinognss.cloud.vantix.common.api.CommonResultAdapter;
import jakarta.validation.Valid;
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
    private final SystemConfigService systemConfigService;

    public ConfigController(ServiceDurationConfigService durationService,
                             SystemConfigService systemConfigService) {
        this.durationService = durationService;
        this.systemConfigService = systemConfigService;
    }

    @GetMapping("/service-durations")
    public Object durationList() {
        return CommonResultAdapter.success(durationService.list());
    }

    @PostMapping("/service-durations")
    public Object durationCreate(@Valid @RequestBody CreateServiceDurationRequest request) {
        return CommonResultAdapter.success(durationService.create(request.toCreateCommand()));
    }

    @PutMapping("/service-durations/{id}")
    public Object durationUpdate(@PathVariable Long id, @Valid @RequestBody UpdateServiceDurationRequest request) {
        return CommonResultAdapter.success(durationService.update(id, request.toCommand()));
    }

    @GetMapping("/service-durations/{id}")
    public Object durationDetail(@PathVariable Long id) {
        return CommonResultAdapter.success(durationService.get(id));
    }

    @GetMapping("/system-company")
    public Object systemCompany() {
        return CommonResultAdapter.success(systemConfigService.getSystemCompanyId());
    }

    @PutMapping("/system-company")
    public Object updateSystemCompany(@Valid @RequestBody SystemCompanyRequest request) {
        return CommonResultAdapter.success(systemConfigService.updateSystemCompany(request.systemCompanyId()));
    }

    public record CreateServiceDurationRequest(@NotBlank @Size(max = 128) String displayName,
                                               @NotBlank @Size(max = 64) String serviceType,
                                               @NotNull @jakarta.validation.constraints.Positive Integer durationDays,
                                               @NotNull @jakarta.validation.constraints.Min(0) Integer codeSilenceDays,
                                               @NotNull @jakarta.validation.constraints.Min(0) Integer accountSilenceDays,
                                               @NotNull Boolean enabled,
                                               @Size(max = 512) String remark) {
        CreateServiceDurationConfigCommand toCreateCommand() {
            return new CreateServiceDurationConfigCommand(displayName, serviceType, durationDays,
                    codeSilenceDays, accountSilenceDays, enabled, remark);
        }
    }

    public record UpdateServiceDurationRequest(@NotBlank @Size(max = 128) String displayName,
                                               @NotNull @jakarta.validation.constraints.Min(0) Integer codeSilenceDays,
                                               @NotNull @jakarta.validation.constraints.Min(0) Integer accountSilenceDays,
                                               @NotNull Boolean enabled,
                                               @Size(max = 512) String remark) {
        UpdateServiceDurationConfigCommand toCommand() {
            return new UpdateServiceDurationConfigCommand(displayName, codeSilenceDays,
                    accountSilenceDays, enabled, remark);
        }
    }

    public record SystemCompanyRequest(@NotNull @Positive Long systemCompanyId) {
    }
}
