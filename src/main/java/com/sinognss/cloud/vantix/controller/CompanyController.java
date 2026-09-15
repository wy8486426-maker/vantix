package com.sinognss.cloud.vantix.controller;

import com.sinognss.cloud.vantix.application.company.CompanyService;
import com.sinognss.cloud.vantix.application.company.CompanyFrontendQueryService;
import com.sinognss.cloud.vantix.application.company.CompanyLevel;
import com.sinognss.cloud.vantix.application.company.CompanyPageQuery;
import com.sinognss.cloud.vantix.application.company.UpdateCompanyParentCommand;
import com.sinognss.cloud.vantix.common.api.CommonResultAdapter;
import com.sinognss.cloud.vantix.domain.company.CompanyStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/companies")
public class CompanyController {
    private final CompanyService companyService;
    private final CompanyFrontendQueryService frontendQueryService;

    public CompanyController(CompanyService companyService, CompanyFrontendQueryService frontendQueryService) {
        this.companyService = companyService;
        this.frontendQueryService = frontendQueryService;
    }

    @GetMapping
    public Object list(@RequestParam(required = false) @Size(max = 128) String name,
                       @RequestParam(required = false) CompanyStatus status) {
        return CommonResultAdapter.success(companyService.list(name, status));
    }

    @GetMapping("/{companyId}")
    public Object detail(@PathVariable Long companyId) {
        return CommonResultAdapter.success(companyService.get(companyId));
    }

    @GetMapping("/{companyId}/children")
    public Object children(@PathVariable Long companyId) {
        return CommonResultAdapter.success(companyService.directChildren(companyId));
    }

    @GetMapping("/page")
    public Object page(@RequestParam(defaultValue = "1") long current,
                       @RequestParam(defaultValue = "20") long size,
                       @RequestParam(required = false) @Size(max = 100) String keyword,
                       @RequestParam(required = false) CompanyStatus status,
                       @RequestParam(required = false) Long parentCompanyId,
                       @RequestParam(required = false) CompanyLevel level) {
        return CommonResultAdapter.success(frontendQueryService.page(new CompanyPageQuery(current, size, keyword,
                status, parentCompanyId, level)));
    }

    @GetMapping("/partners")
    public Object partners(@RequestParam(required = false) @jakarta.validation.constraints.Positive Long companyId) {
        return CommonResultAdapter.success(frontendQueryService.partners(companyId));
    }

    @PutMapping("/{companyId}/parent")
    public Object updateParent(@PathVariable Long companyId, @Valid @RequestBody UpdateParentRequest request) {
        return CommonResultAdapter.success(companyService.updateParent(
                new UpdateCompanyParentCommand(companyId, request.parentCompanyId(), request.reason())));
    }

    public record UpdateParentRequest(Long parentCompanyId, @Size(max = 512) String reason) {
    }
}
