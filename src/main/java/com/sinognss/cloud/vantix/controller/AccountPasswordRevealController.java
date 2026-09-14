package com.sinognss.cloud.vantix.controller;

import com.sinognss.cloud.vantix.application.password.reveal.AccountPasswordRevealService;
import com.sinognss.cloud.vantix.application.password.reveal.PasswordRevealResponse;
import com.sinognss.cloud.vantix.common.api.CommonResultAdapter;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountPasswordGateway;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/service-accounts")
@ConditionalOnProperty(prefix = "vantix.cors.password", name = "enabled", havingValue = "true")
@ConditionalOnBean(CorsAccountPasswordGateway.class)
public class AccountPasswordRevealController {
    private final AccountPasswordRevealService revealService;

    public AccountPasswordRevealController(AccountPasswordRevealService revealService) {
        this.revealService = revealService;
    }

    @PostMapping(value = "/{serviceAccountId}/password/reveal", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> reveal(@PathVariable Long serviceAccountId,
                                    @Valid @RequestBody RevealRequest request) {
        PasswordRevealResponse response = revealService.reveal(serviceAccountId, request.requestId());
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl("no-store, max-age=0");
        headers.setPragma("no-cache");
        headers.set(HttpHeaders.EXPIRES, "0");
        headers.set("X-Content-Type-Options", "nosniff");
        return ResponseEntity.ok().headers(headers).body(CommonResultAdapter.success(response));
    }

    public record RevealRequest(@NotBlank @Size(max = 128) String requestId) {
    }
}
