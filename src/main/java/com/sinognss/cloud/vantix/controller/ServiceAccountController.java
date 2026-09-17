package com.sinognss.cloud.vantix.controller;

import com.sinognss.cloud.vantix.application.account.ServiceAccountPageQuery;
import com.sinognss.cloud.vantix.application.account.ServiceAccountQueryService;
import com.sinognss.cloud.vantix.application.account.ServiceAccountStatisticsQuery;
import com.sinognss.cloud.vantix.common.api.CommonResultAdapter;
import com.sinognss.cloud.vantix.domain.account.AccountSource;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/service-accounts")
public class ServiceAccountController {
    private final ServiceAccountQueryService service;

    public ServiceAccountController(ServiceAccountQueryService service) {
        this.service = service;
    }

    @GetMapping
    public Object page(@RequestParam(defaultValue = "1") long current,
                       @RequestParam(defaultValue = "20") long size,
                       @RequestParam(required = false) @Size(max = 100) String keyword,
                       @RequestParam(required = false) @Size(max = 32) String status,
                       @RequestParam(required = false) @Size(max = 32) String specCode,
                       @RequestParam(required = false) @Positive Integer durationDays,
                       @RequestParam(required = false) @Positive Long ownerCompanyId,
                       @RequestParam(required = false) @Positive Long assignedUserId,
                       @RequestParam(required = false) AccountSource accountSource) {
        return CommonResultAdapter.success(service.page(new ServiceAccountPageQuery(current, size, keyword, status,
                specCode, durationDays, ownerCompanyId, assignedUserId, accountSource)));
    }

    @GetMapping("/statistics")
    public Object statistics(@RequestParam(required = false) @Size(max = 100) String keyword,
                             @RequestParam(required = false) @Size(max = 32) String specCode,
                             @RequestParam(required = false) @Positive Integer durationDays,
                             @RequestParam(required = false) @Positive Long ownerCompanyId,
                             @RequestParam(required = false) @Positive Long assignedUserId) {
        return CommonResultAdapter.success(service.statistics(new ServiceAccountStatisticsQuery(keyword, specCode,
                durationDays, ownerCompanyId, assignedUserId)));
    }

    @GetMapping("/detail")
    public Object detail(@RequestParam @Positive Long id) {
        return CommonResultAdapter.success(service.get(id));
    }
}
