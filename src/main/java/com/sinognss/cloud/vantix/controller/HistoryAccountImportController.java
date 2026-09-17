package com.sinognss.cloud.vantix.controller;

import com.sinognss.cloud.vantix.application.history.HistoryAccountIdentity;
import com.sinognss.cloud.vantix.application.history.HistoryAccountImportCommand;
import com.sinognss.cloud.vantix.application.history.HistoryAccountImportService;
import com.sinognss.cloud.vantix.common.api.CommonResultAdapter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/service-accounts/history-imports")
public class HistoryAccountImportController {
    private final HistoryAccountImportService service;

    public HistoryAccountImportController(HistoryAccountImportService service) {
        this.service = service;
    }

    @PostMapping
    public Object create(@Valid @RequestBody CreateRequest request) {
        List<HistoryAccountIdentity> accounts = request.accounts().stream()
                .map(item -> new HistoryAccountIdentity(item.id(), item.name())).toList();
        return CommonResultAdapter.success(service.importAccounts(new HistoryAccountImportCommand(
                request.requestId(), request.companyId(), request.specCode(), accounts)));
    }

    public record CreateRequest(@Size(max = 128) String requestId,
                                @NotNull @Positive Long companyId,
                                @Size(max = 32) String specCode,
                                @NotEmpty @Size(max = 5000) List<@Valid AccountRequest> accounts) {
    }

    public record AccountRequest(@NotNull @Positive Long id,
                                 @Size(max = 128) String name) {
    }
}
