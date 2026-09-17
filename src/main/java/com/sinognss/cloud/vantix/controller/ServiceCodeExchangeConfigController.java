package com.sinognss.cloud.vantix.controller;

import com.sinognss.cloud.vantix.application.exchange.ServiceCodeExchangeConfigService;
import com.sinognss.cloud.vantix.common.api.CommonResultAdapter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/service-code-exchange-config")
public class ServiceCodeExchangeConfigController {
    private final ServiceCodeExchangeConfigService service;

    public ServiceCodeExchangeConfigController(ServiceCodeExchangeConfigService service) {
        this.service = service;
    }

    @GetMapping
    public Object get() {
        return CommonResultAdapter.success(service.getCurrent());
    }

    @PostMapping("/configure")
    public Object configure(@Valid @RequestBody ExchangeConfigRequest request) {
        return CommonResultAdapter.success(service.configure(request.accountPrefix()));
    }

    public record ExchangeConfigRequest(@NotBlank String accountPrefix) {
    }
}
