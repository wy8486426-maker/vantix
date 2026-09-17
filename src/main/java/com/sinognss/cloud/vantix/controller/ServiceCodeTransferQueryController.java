package com.sinognss.cloud.vantix.controller;

import com.sinognss.cloud.vantix.application.servicecode.ServiceCodeTransferPageQuery;
import com.sinognss.cloud.vantix.application.servicecode.ServiceCodeTransferQueryService;
import com.sinognss.cloud.vantix.common.api.CommonResultAdapter;
import com.sinognss.cloud.vantix.domain.servicecode.TransferType;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@Validated
@RequestMapping("/api/service-code-transfers")
public class ServiceCodeTransferQueryController {
    private final ServiceCodeTransferQueryService service;

    public ServiceCodeTransferQueryController(ServiceCodeTransferQueryService service) {
        this.service = service;
    }

    @GetMapping
    public Object page(@RequestParam(defaultValue = "1") long current,
                       @RequestParam(defaultValue = "20") long size,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(required = false) TransferType transferType,
                       @RequestParam(required = false) Long fromCompanyId,
                       @RequestParam(required = false) Long toCompanyId,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                       LocalDateTime createdFrom,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                       LocalDateTime createdTo,
                       @RequestParam(required = false) String specCode,
                       @RequestParam(required = false) Integer durationDays) {
        return CommonResultAdapter.success(service.page(new ServiceCodeTransferPageQuery(current, size, keyword,
                transferType, fromCompanyId, toCompanyId, createdFrom, createdTo, specCode, durationDays)));
    }

    @GetMapping("/detail")
    public Object detail(@RequestParam String transferNo) {
        return CommonResultAdapter.success(service.detail(transferNo));
    }
}
