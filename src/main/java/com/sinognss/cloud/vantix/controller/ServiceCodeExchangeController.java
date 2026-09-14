package com.sinognss.cloud.vantix.controller;

import com.sinognss.cloud.vantix.application.exchange.ExchangeQueryService;
import com.sinognss.cloud.vantix.application.exchange.ServiceCodeExchangeCommand;
import com.sinognss.cloud.vantix.application.exchange.ServiceCodeExchangeService;
import com.sinognss.cloud.vantix.common.api.CommonResultAdapter;
import com.sinognss.cloud.vantix.domain.servicecode.GenerationSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/service-code-exchanges")
public class ServiceCodeExchangeController {
    private final ServiceCodeExchangeService exchangeService;
    private final ExchangeQueryService queryService;

    public ServiceCodeExchangeController(ServiceCodeExchangeService exchangeService,
                                         ExchangeQueryService queryService) {
        this.exchangeService = exchangeService;
        this.queryService = queryService;
    }

    @PostMapping
    public Object exchange(@Valid @RequestBody ExchangeRequest request) {
        return CommonResultAdapter.success(exchangeService.exchange(new ServiceCodeExchangeCommand(
                request.requestId(), request.companyId(), request.specCode(), request.generationSource(),
                request.quantity(), request.accountPrefix())));
    }

    @GetMapping("/{requestId}")
    public Object get(@PathVariable String requestId) {
        return CommonResultAdapter.success(queryService.get(requestId));
    }

    public record ExchangeRequest(@NotBlank @Size(max = 128) String requestId,
                                 @NotNull @Positive Long companyId,
                                 @NotBlank @Size(max = 32) String specCode,
                                 @NotNull GenerationSource generationSource,
                                 @NotNull @Positive Integer quantity,
                                 @Size(max = 64) String accountPrefix) {
    }
}
