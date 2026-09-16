package com.sinognss.cloud.vantix.controller;

import com.sinognss.cloud.vantix.application.dashboard.DashboardQueryService;
import com.sinognss.cloud.vantix.common.api.CommonResultAdapter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardQueryService service;

    public DashboardController(DashboardQueryService service) {
        this.service = service;
    }

    @GetMapping
    public Object get() {
        return CommonResultAdapter.success(service.get());
    }
}
