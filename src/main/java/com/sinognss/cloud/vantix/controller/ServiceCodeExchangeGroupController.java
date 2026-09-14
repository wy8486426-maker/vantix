package com.sinognss.cloud.vantix.controller;

import com.sinognss.cloud.vantix.application.exchange.ExchangeGroupQueryService;
import com.sinognss.cloud.vantix.common.api.CommonResultAdapter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/service-code-exchange-groups")
public class ServiceCodeExchangeGroupController {
    private final ExchangeGroupQueryService groupQueryService;

    public ServiceCodeExchangeGroupController(ExchangeGroupQueryService groupQueryService) {
        this.groupQueryService = groupQueryService;
    }

    @GetMapping
    public Object groups(@RequestParam(required = false) Long companyId) {
        return CommonResultAdapter.success(groupQueryService.list(companyId));
    }
}
