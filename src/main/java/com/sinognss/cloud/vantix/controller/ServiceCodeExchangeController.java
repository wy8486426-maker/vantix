package com.sinognss.cloud.vantix.controller;

import com.sinognss.cloud.vantix.application.exchange.ExchangeQueryService;
import com.sinognss.cloud.vantix.application.exchange.ExchangeLogPageQuery;
import com.sinognss.cloud.vantix.application.exchange.ExchangeLogQueryService;
import com.sinognss.cloud.vantix.application.exchange.ServiceCodeExchangeCommand;
import com.sinognss.cloud.vantix.application.exchange.ServiceCodeExchangeService;
import com.sinognss.cloud.vantix.common.api.CommonResultAdapter;
import com.sinognss.cloud.vantix.domain.servicecode.GenerationSource;
import com.sinognss.cloud.vantix.domain.exchange.ExchangeStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/service-code-exchanges")
public class ServiceCodeExchangeController {
    private final ServiceCodeExchangeService exchangeService;
    private final ExchangeQueryService queryService;
    private final ExchangeLogQueryService logQueryService;

    public ServiceCodeExchangeController(ServiceCodeExchangeService exchangeService,
                                         ExchangeQueryService queryService,
                                         ExchangeLogQueryService logQueryService) {
        this.exchangeService = exchangeService;
        this.queryService = queryService;
        this.logQueryService = logQueryService;
    }

    @PostMapping("/create")
    public Object exchange(@Valid @RequestBody ExchangeRequest request) {
        return CommonResultAdapter.success(exchangeService.exchange(new ServiceCodeExchangeCommand(
                request.requestId(), request.companyId(), request.specCode(), request.generationSource(),
                request.quantity())));
    }

    @GetMapping("/result")
    public Object get(@RequestParam String requestId) {
        return CommonResultAdapter.success(queryService.get(requestId));
    }

    @GetMapping("/detail")
    public Object detail(@RequestParam String requestId) {
        return CommonResultAdapter.success(logQueryService.detail(requestId));
    }

    @GetMapping
    public Object page(@RequestParam(defaultValue = "1") long current,
                       @RequestParam(defaultValue = "20") long size,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(required = false) ExchangeStatus status,
                       @RequestParam(required = false) String specCode,
                       @RequestParam(required = false) Long ownerCompanyId,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                       LocalDateTime createdFrom,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                       LocalDateTime createdTo) {
        return CommonResultAdapter.success(logQueryService.page(new ExchangeLogPageQuery(current, size, keyword,
                status, specCode, ownerCompanyId, createdFrom, createdTo)));
    }

    public record ExchangeRequest(@NotBlank @Size(max = 128) String requestId,
                                 @NotNull @Positive Long companyId,
                                 @NotBlank @Size(max = 32) String specCode,
                                 @NotNull GenerationSource generationSource,
                                 @NotNull @Positive Integer quantity) {
    }

}
