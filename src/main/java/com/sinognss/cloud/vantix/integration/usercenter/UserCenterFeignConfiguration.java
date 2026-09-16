package com.sinognss.cloud.vantix.integration.usercenter;

import com.sinognss.cloud.base.filter.UserHolder;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * User Center's client-specific user-context forwarding configuration.
 *
 * <p>This class intentionally is not a {@code @Configuration} component. It is
 * loaded only by {@link UserCenterFeignService}, so its interceptor does not
 * become global configuration for other Feign clients.</p>
 */
public class UserCenterFeignConfiguration {

    @Bean
    public RequestInterceptor userCenterUserContextInterceptor() {
        return template -> {
            if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes)) {
                return;
            }

            headerIfPresent(template, "user_id", UserHolder.getUserId());
            headerIfPresent(template, "user_name", UserHolder.getUserNickname());
            headerIfPresent(template, "company_id", UserHolder.getCompanyId());
            headerIfPresent(template, "data_type", UserHolder.getDataType());
            headerIfPresent(template, "company_ids", UserHolder.getCompanyIds());
        };
    }

    private static void headerIfPresent(RequestTemplate template, String name, Object value) {
        if (value != null) {
            template.header(name, String.valueOf(value));
        }
    }
}
