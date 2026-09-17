package com.sinognss.cloud.vantix.common.user;

import com.sinognss.cloud.base.dto.UserCacheDTO;
import com.sinognss.cloud.base.filter.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Builds the base-library user context for Boot 3 HTTP requests.
 *
 * <p>The deployment gateway authenticates the request and forwards the
 * user-center context in the legacy header names. The base library's
 * interceptor cannot be used here because it is compiled against
 * {@code javax.servlet}; this adapter is the Boot 3-compatible equivalent.</p>
 */
@Component
public class UserContextInterceptor implements HandlerInterceptor {
    private static final String USER_ID = "user_id";
    private static final String USER_NAME = "user_name";
    private static final String COMPANY_ID = "company_id";
    private static final String DATA_TYPE = "data_type";
    private static final String COMPANY_IDS = "company_ids";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        UserHolder.removeUser();
        UserHolder.setUser(resolveUser(request));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception exception) {
        UserHolder.removeUser();
    }

    private UserCacheDTO resolveUser(HttpServletRequest request) {
        String userId = header(request, USER_ID);
        String userName = header(request, USER_NAME);
        String companyId = header(request, COMPANY_ID);
        if (userId == null || userName == null || companyId == null) {
            return null;
        }

        try {
            Integer dataType = parseDataType(header(request, DATA_TYPE));
            if (dataType == null) {
                return null;
            }

            UserCacheDTO user = new UserCacheDTO();
            user.setUserId(Long.valueOf(userId));
            user.setUserNickname(URLDecoder.decode(userName, StandardCharsets.UTF_8));
            user.setCompanyId(Long.valueOf(companyId));
            user.setDataType(dataType);
            user.setCompanyIds(request.getHeader(COMPANY_IDS));
            return user;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private Integer parseDataType(String value) {
        return value == null ? 1 : Integer.valueOf(value);
    }

    private String header(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
