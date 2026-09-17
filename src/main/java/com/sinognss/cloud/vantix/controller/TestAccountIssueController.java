package com.sinognss.cloud.vantix.controller;

import com.sinognss.cloud.vantix.application.testaccount.TestAccountIssueCommand;
import com.sinognss.cloud.vantix.application.testaccount.TestAccountIssueQueryService;
import com.sinognss.cloud.vantix.application.testaccount.TestAccountIssueService;
import com.sinognss.cloud.vantix.common.api.CommonResultAdapter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/service-accounts/test-issues")
public class TestAccountIssueController {
    private final TestAccountIssueService service;
    private final TestAccountIssueQueryService queryService;

    public TestAccountIssueController(TestAccountIssueService service,
                                      TestAccountIssueQueryService queryService) {
        this.service = service;
        this.queryService = queryService;
    }

    @PostMapping
    public Object create(@Valid @RequestBody CreateRequest request) {
        return CommonResultAdapter.success(service.issue(new TestAccountIssueCommand(
                request.requestId(), request.companyId(), request.quantity(), request.specCode(),
                request.accountPrefix())));
    }

    @GetMapping("/query")
    public Object get(@RequestParam String requestId) {
        return CommonResultAdapter.success(queryService.get(requestId));
    }

    public record CreateRequest(@Size(max = 128) String requestId,
                                @NotNull @Positive Long companyId,
                                @NotNull @Positive Integer quantity,
                                @Size(max = 32) String specCode,
                                @Size(max = 4) String accountPrefix) {
    }
}
