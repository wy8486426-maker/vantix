package com.sinognss.cloud.vantix.controller;

import com.sinognss.cloud.vantix.application.renewal.AccountRenewalLogPageQuery;
import com.sinognss.cloud.vantix.application.renewal.AccountRenewalLogQueryService;
import com.sinognss.cloud.vantix.application.renewal.AccountRenewalQueryService;
import com.sinognss.cloud.vantix.common.api.CommonResultAdapter;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@Validated
@RequestMapping("/api/account-renewals")
public class AccountRenewalLogController {
    private final AccountRenewalLogQueryService logQueryService;
    private final AccountRenewalQueryService queryService;

    public AccountRenewalLogController(AccountRenewalLogQueryService logQueryService,
                                       AccountRenewalQueryService queryService) {
        this.logQueryService = logQueryService;
        this.queryService = queryService;
    }

    @GetMapping
    public Object page(@RequestParam(defaultValue = "1") long current,
                       @RequestParam(defaultValue = "20") long size,
                       @RequestParam(required = false) @Size(max = 100) String keyword,
                       @RequestParam(required = false) @Size(max = 32) String status,
                       @RequestParam(required = false) @Positive Long ownerCompanyId,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                       LocalDateTime createdFrom,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                       LocalDateTime createdTo) {
        return CommonResultAdapter.success(logQueryService.page(new AccountRenewalLogPageQuery(current, size, keyword, status,
                ownerCompanyId, createdFrom, createdTo)));
    }

    @GetMapping("/{requestId}")
    public Object detail(@PathVariable String requestId) {
        return CommonResultAdapter.success(queryService.get(requestId));
    }
}
