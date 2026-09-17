package com.sinognss.cloud.vantix.common.user;

import com.sinognss.cloud.base.dto.UserCacheDTO;
import com.sinognss.cloud.base.filter.UserHolder;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserContextInterceptorTest {
    private final UserContextInterceptor interceptor = new UserContextInterceptor();
    private final HttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void clearUser() {
        UserHolder.removeUser();
    }

    @Test
    void validGatewayHeadersPopulateUserHolder() throws Exception {
        MockHttpServletRequest request = requestWithRequiredHeaders();
        request.addHeader("data_type", "2");
        request.addHeader("company_ids", "10,11");

        interceptor.preHandle(request, response, new Object());

        UserCacheDTO user = UserHolder.getUser();
        assertEquals(88L, user.getUserId());
        assertEquals("测试用户", user.getUserNickname());
        assertEquals(10L, user.getCompanyId());
        assertEquals(2, user.getDataType());
        assertEquals("10,11", user.getCompanyIds());
    }

    @Test
    void missingRequiredHeaderClearsPreviousRequestContext() throws Exception {
        UserHolder.setUser(user());

        MockHttpServletRequest request = new MockHttpServletRequest();
        interceptor.preHandle(request, response, new Object());

        assertNull(UserHolder.getUser());
    }

    @Test
    void malformedHeaderFailsClosed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("user_id", "88");
        request.addHeader("user_name", "tester");
        request.addHeader("company_id", "not-a-number");

        interceptor.preHandle(request, response, new Object());

        assertNull(UserHolder.getUser());
    }

    @Test
    void completionAlwaysClearsContext() throws Exception {
        interceptor.preHandle(requestWithRequiredHeaders(), response, new Object());

        interceptor.afterCompletion(new MockHttpServletRequest(), response, new Object(), null);

        assertNull(UserHolder.getUser());
    }

    private MockHttpServletRequest requestWithRequiredHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("user_id", "88");
        request.addHeader("user_name", "%E6%B5%8B%E8%AF%95%E7%94%A8%E6%88%B7");
        request.addHeader("company_id", "10");
        return request;
    }

    private UserCacheDTO user() {
        UserCacheDTO user = new UserCacheDTO();
        user.setUserId(88L);
        user.setUserNickname("tester");
        user.setCompanyId(10L);
        user.setDataType(2);
        return user;
    }
}
