package com.sinognss.cloud.vantix.controller;

import com.sinognss.cloud.vantix.application.servicecode.generation.ServiceCodeGenerationQueryService;
import com.sinognss.cloud.vantix.application.servicecode.generation.GenerationOrderPageQuery;
import com.sinognss.cloud.vantix.application.servicecode.generation.GenerationOrderQueryService;
import com.sinognss.cloud.vantix.common.api.CommonResultAdapter;
import com.sinognss.cloud.vantix.domain.servicecode.GenerationSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@RestController
@Validated
@RequestMapping("/api/service-code-generations/orders")
public class ServiceCodeGenerationOrderController {
    private final ServiceCodeGenerationQueryService queryService;
    private final GenerationOrderQueryService orderQueryService;

    public ServiceCodeGenerationOrderController(ServiceCodeGenerationQueryService queryService,
                                                GenerationOrderQueryService orderQueryService) {
        this.queryService = queryService;
        this.orderQueryService = orderQueryService;
    }

    @GetMapping
    public Object page(@RequestParam(defaultValue = "1") long current,
                       @RequestParam(defaultValue = "20") long size,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(required = false) GenerationSource generationSource,
                       @RequestParam(required = false) @Size(max = 32) String status,
                       @RequestParam(required = false) @Positive Long ownerCompanyId,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                       LocalDateTime createdFrom,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                       LocalDateTime createdTo) {
        return CommonResultAdapter.success(orderQueryService.page(new GenerationOrderPageQuery(current, size,
                keyword, generationSource, status, ownerCompanyId, createdFrom, createdTo)));
    }

    @GetMapping("/statistics")
    public Object statistics(@RequestParam(required = false) String keyword,
                             @RequestParam(required = false) @Size(max = 32) String status,
                             @RequestParam(required = false) @Positive Long ownerCompanyId,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                             LocalDateTime createdFrom,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                             LocalDateTime createdTo) {
        return CommonResultAdapter.success(orderQueryService.statistics(new GenerationOrderPageQuery(1, 1,
                keyword, null, status, ownerCompanyId, createdFrom, createdTo)));
    }

    @GetMapping("/{orderNo}")
    public Object byOrderNo(@PathVariable @NotBlank @Size(max = 128) String orderNo,
                            @RequestParam(required = false) @Positive Long companyId) {
        return CommonResultAdapter.success(queryService.ordersByOrderNo(orderNo, companyId));
    }
}
