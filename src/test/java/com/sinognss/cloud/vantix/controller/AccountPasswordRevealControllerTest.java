package com.sinognss.cloud.vantix.controller;

import com.sinognss.cloud.vantix.application.password.reveal.AccountPasswordRevealService;
import com.sinognss.cloud.vantix.application.password.reveal.PasswordRevealResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountPasswordRevealControllerTest {
    @Test
    void revealResponseDisablesBrowserAndProxyCaching() {
        AccountPasswordRevealService revealService = mock(AccountPasswordRevealService.class);
        when(revealService.reveal(41L, "RV-41"))
                .thenReturn(new PasswordRevealResponse(41L, "account-41", "p@ssword-value"));
        AccountPasswordRevealController controller = new AccountPasswordRevealController(revealService);

        ResponseEntity<?> response = controller.reveal(41L,
                new AccountPasswordRevealController.RevealRequest("RV-41"));

        assertEquals("no-store, max-age=0", response.getHeaders().getFirst("Cache-Control"));
        assertEquals("no-cache", response.getHeaders().getFirst("Pragma"));
        assertEquals("0", response.getHeaders().getFirst("Expires"));
        assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
        assertNotNull(response.getBody());
    }
}
