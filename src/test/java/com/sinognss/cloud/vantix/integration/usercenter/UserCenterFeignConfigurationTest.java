package com.sinognss.cloud.vantix.integration.usercenter;

import com.sinognss.cloud.base.dto.UserCacheDTO;
import com.sinognss.cloud.base.filter.UserHolder;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserCenterFeignConfigurationTest {
    private final RequestInterceptor interceptor =
            new UserCenterFeignConfiguration().userCenterUserContextInterceptor();

    @AfterEach
    void clearThreadContext() {
        RequestContextHolder.resetRequestAttributes();
        UserHolder.removeUser();
    }

    @Test
    void doesNotForwardHeadersWithoutServletRequestAttributes() {
        RequestTemplate template = new RequestTemplate();

        assertDoesNotThrow(() -> interceptor.apply(template));

        assertTrue(template.headers().isEmpty());
    }

    @Test
    void forwardsAllUserContextHeadersForHttpRequest() {
        bindRequestContext();
        UserHolder.setUser(user(88L, "tester", 10L, 2, "10,11"));

        Map<String, Collection<String>> headers = applyAndGetHeaders();

        assertHeader(headers, "user_id", "88");
        assertHeader(headers, "user_name", "tester");
        assertHeader(headers, "company_id", "10");
        assertHeader(headers, "data_type", "2");
        assertHeader(headers, "company_ids", "10,11");
    }

    @Test
    void doesNotWriteNullUserIdForCompanyOrGlobalContext() {
        bindRequestContext();
        UserHolder.setUser(user(null, "global", 10L, 4, "10,11"));

        RequestTemplate template = new RequestTemplate();

        assertDoesNotThrow(() -> interceptor.apply(template));

        assertFalse(template.headers().containsKey("user_id"));
        assertFalse(template.headers().values().stream()
                .flatMap(Collection::stream)
                .anyMatch("null"::equals));
        assertHeader(template.headers(), "user_name", "global");
        assertHeader(template.headers(), "company_id", "10");
        assertHeader(template.headers(), "data_type", "4");
        assertHeader(template.headers(), "company_ids", "10,11");
    }

    @Test
    void omitsNullOptionalHeadersAndKeepsOtherHeaders() {
        bindRequestContext();
        UserHolder.setUser(user(88L, "tester", null, null, null));

        Map<String, Collection<String>> headers = applyAndGetHeaders();

        assertHeader(headers, "user_id", "88");
        assertHeader(headers, "user_name", "tester");
        assertFalse(headers.containsKey("company_id"));
        assertFalse(headers.containsKey("data_type"));
        assertFalse(headers.containsKey("company_ids"));
    }

    @Test
    void feignServiceUsesOnlyTheUserCenterConfiguration() {
        FeignClient metadata = UserCenterFeignService.class.getAnnotation(FeignClient.class);

        assertEquals(UserCenterFeignConfiguration.class, metadata.configuration()[0]);
    }

    private Map<String, Collection<String>> applyAndGetHeaders() {
        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);
        return template.headers();
    }

    private void bindRequestContext() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    private UserCacheDTO user(Long userId, String nickname, Long companyId,
                              Integer dataType, String companyIds) {
        UserCacheDTO user = new UserCacheDTO();
        user.setUserId(userId);
        user.setUserNickname(nickname);
        user.setCompanyId(companyId);
        user.setDataType(dataType);
        user.setCompanyIds(companyIds);
        return user;
    }

    private void assertHeader(Map<String, Collection<String>> headers,
                              String name, String value) {
        assertEquals(List.of(value), headers.get(name));
    }
}
