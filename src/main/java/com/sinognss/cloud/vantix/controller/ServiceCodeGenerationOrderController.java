package com.sinognss.cloud.vantix.controller;

import com.sinognss.cloud.vantix.application.servicecode.generation.ServiceCodeGenerationQueryService;
import com.sinognss.cloud.vantix.common.api.CommonResultAdapter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/service-code-generations/orders")
public class ServiceCodeGenerationOrderController {
    private final ServiceCodeGenerationQueryService queryService;

    public ServiceCodeGenerationOrderController(ServiceCodeGenerationQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/{orderNo}")
    public Object byOrderNo(@PathVariable @NotBlank @Size(max = 128) String orderNo,
                            @RequestParam(required = false) @Positive Long companyId) {
        return CommonResultAdapter.success(queryService.ordersByOrderNo(orderNo, companyId));
    }
}