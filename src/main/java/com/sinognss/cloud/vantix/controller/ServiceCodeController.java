package com.sinognss.cloud.vantix.controller;

import com.sinognss.cloud.vantix.application.servicecode.DisplayStatus;
import com.sinognss.cloud.vantix.application.servicecode.ServiceCodePageQuery;
import com.sinognss.cloud.vantix.application.servicecode.ServiceCodeService;
import com.sinognss.cloud.vantix.application.servicecode.ServiceCodeStatisticsQuery;
import com.sinognss.cloud.vantix.application.servicecode.ServiceCodeTransferService;
import com.sinognss.cloud.vantix.application.servicecode.TransferServiceCodeCommand;
import com.sinognss.cloud.vantix.common.api.CommonResultAdapter;
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

    public ServiceCodeController(ServiceCodeService serviceCodeService,
                                 ServiceCodeTransferService transferService) {
        this.serviceCodeService = serviceCodeService;
        this.transferService = transferService;
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

    @GetMapping("/detail")
    public Object detail(@RequestParam Long id) {
        return CommonResultAdapter.success(serviceCodeService.get(id));
    }

    @PostMapping("/transfers")
    public Object transfer(@Valid @RequestBody TransferRequest request) {
        return CommonResultAdapter.success(transferService.transfer(new TransferServiceCodeCommand(
                request.fromCompanyId(), request.toCompanyId(), request.serviceCodeIds(), request.reason())));
    }

    @GetMapping("/transfers/history")
    public Object history(@RequestParam Long id) {
        return CommonResultAdapter.success(transferService.history(id));
    }

    public record TransferRequest(@NotNull @Positive Long fromCompanyId,
                                  @NotNull @Positive Long toCompanyId,
                                  @NotEmpty @Size(max = 500) List<@NotNull @Positive Long> serviceCodeIds,
                                  @Size(max = 512) String reason) {
    }
}
