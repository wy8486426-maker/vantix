package com.sinognss.cloud.vantix.controller;

import com.sinognss.cloud.vantix.application.servicecode.generation.GenerateServiceCodeCommand;
import com.sinognss.cloud.vantix.application.servicecode.generation.IntegrationActor;
import com.sinognss.cloud.vantix.application.servicecode.generation.ServiceCodeGenerateService;
import com.sinognss.cloud.vantix.common.api.CommonResultAdapter;
import com.sinognss.cloud.vantix.domain.servicecode.GenerationSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/internal/v1/service-code-generations")
public class InternalServiceCodeGenerationController {
    private final ServiceCodeGenerateService generateService;

    public InternalServiceCodeGenerationController(ServiceCodeGenerateService generateService) {
        this.generateService = generateService;
    }

    /**
     * The production gateway must restrict this internal route to the B2B service.
     * No new service-authentication scheme is defined in this application.
     */
    @PostMapping
    public Object generate(@Valid @RequestBody GenerateRequest request) {
        return CommonResultAdapter.success(generateService.generate(new GenerateServiceCodeCommand(
                GenerationSource.B2B, request.requestId(), request.orderNo(), request.orderTime(),
                request.companyId(), request.specCode(), request.quantity(), null),
                IntegrationActor.B2B.operatorIdentity()));
    }

    public record GenerateRequest(@NotBlank @Size(max = 160) String requestId,
                                  @NotBlank @Size(max = 128) String orderNo,
                                  @NotNull @Positive Long companyId,
                                  @NotBlank @Size(max = 700) String specCode,
                                  @NotNull @Positive Integer quantity,
                                  LocalDateTime orderTime) {
    }
}