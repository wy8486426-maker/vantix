package com.sinognss.cloud.vantix.controller;

import com.sinognss.cloud.vantix.application.servicecode.DisplayStatus;
import com.sinognss.cloud.vantix.application.servicecode.ServiceCodePageQuery;
import com.sinognss.cloud.vantix.application.servicecode.ServiceCodeService;
import com.sinognss.cloud.vantix.application.servicecode.ServiceCodeStatisticsQuery;
import com.sinognss.cloud.vantix.application.servicecode.ServiceCodeTransferService;
import com.sinognss.cloud.vantix.application.servicecode.TransferServiceCodeCommand;
import com.sinognss.cloud.vantix.common.api.CommonResultAdapter;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.config.ServiceCodeTransferProperties;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/service-codes")
public class ServiceCodeController {
    private final ServiceCodeService serviceCodeService;
    private final ServiceCodeTransferService transferService;
    private final ServiceCodeTransferProperties transferProperties;

    public ServiceCodeController(ServiceCodeService serviceCodeService,
                                 ServiceCodeTransferService transferService,
                                 ServiceCodeTransferProperties transferProperties) {
        this.serviceCodeService = serviceCodeService;
        this.transferService = transferService;
        this.transferProperties = transferProperties;
    }

    @GetMapping
    public Object page(@RequestParam(defaultValue = "1") long current,
                       @RequestParam(defaultValue = "20") long size,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(required = false) ServiceCodeStatus status,
                       @RequestParam(required = false) DisplayStatus displayStatus,
                       @RequestParam(required = false) String specCode,
                       @RequestParam(required = false) Integer durationDays,
                       @RequestParam(required = false) String sourceOrderNo,
                       @RequestParam(required = false) Long ownerCompanyId) {
        return CommonResultAdapter.success(serviceCodeService.page(new ServiceCodePageQuery(
                current, size, keyword, status, displayStatus, specCode, durationDays,
                sourceOrderNo, ownerCompanyId)));
    }

    @GetMapping("/statistics")
    public Object statistics(@RequestParam(required = false) String keyword,
                             @RequestParam(required = false) String specCode,
                             @RequestParam(required = false) Integer durationDays,
                             @RequestParam(required = false) String sourceOrderNo,
                             @RequestParam(required = false) Long ownerCompanyId) {
        return CommonResultAdapter.success(serviceCodeService.statistics(new ServiceCodeStatisticsQuery(
                keyword, specCode, durationDays, sourceOrderNo, ownerCompanyId)));
    }

    @GetMapping("/spec-statistics")
    public Object specStatistics(@RequestParam(required = false) Long ownerCompanyId) {
        return CommonResultAdapter.success(serviceCodeService.specStatistics(ownerCompanyId));
    }

    @GetMapping("/detail")
    public Object detail(@RequestParam Long id) {
        return CommonResultAdapter.success(serviceCodeService.get(id));
    }

    @PostMapping("/transfers")
    public Object transfer(@Valid @RequestBody TransferRequest request) {
        if (request.serviceCodeIds().size() > transferProperties.getMaxBatchSize()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "单次转赠服务码数量不能超过 " + transferProperties.getMaxBatchSize());
        }
        return CommonResultAdapter.success(transferService.transfer(new TransferServiceCodeCommand(
                request.toCompanyId(), request.serviceCodeIds(), request.reason())));
    }

    @GetMapping("/transfers/history")
    public Object history(@RequestParam Long id) {
        return CommonResultAdapter.success(transferService.history(id));
    }

    public record TransferRequest(@NotNull @Positive Long toCompanyId,
                                  @NotEmpty List<@NotNull @Positive Long> serviceCodeIds,
                                  @Size(max = 512) String reason) {
    }
}
