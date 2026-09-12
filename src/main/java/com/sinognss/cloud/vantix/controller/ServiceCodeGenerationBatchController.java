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
@RequestMapping("/api/service-code-generation-batches")
public class ServiceCodeGenerationBatchController {
    private final ServiceCodeGenerationQueryService queryService;

    public ServiceCodeGenerationBatchController(ServiceCodeGenerationQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/{batchNo}")
    public Object get(@PathVariable @NotBlank @Size(max = 64) String batchNo) {
        return CommonResultAdapter.success(queryService.get(batchNo));
    }

    @GetMapping
    public Object byOrderNo(@RequestParam @NotBlank @Size(max = 128) String orderNo,
                            @RequestParam(required = false) @Positive Long companyId) {
        return CommonResultAdapter.success(queryService.byOrderNo(orderNo, companyId));
    }
}