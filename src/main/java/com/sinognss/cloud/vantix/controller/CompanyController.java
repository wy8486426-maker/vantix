package com.sinognss.cloud.vantix.controller;

import com.sinognss.cloud.vantix.application.company.CompanyService;
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

import java.util.List;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {
    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
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

    @PutMapping("/{companyId}/parent")
    public Object updateParent(@PathVariable Long companyId, @Valid @RequestBody UpdateParentRequest request) {
        return CommonResultAdapter.success(companyService.updateParent(
                new UpdateCompanyParentCommand(companyId, request.parentCompanyId(), request.reason())));
    }

    public record UpdateParentRequest(Long parentCompanyId, @Size(max = 512) String reason) {
    }
}
