package com.academic.config;

import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 把网关注入的身份头透传给 Feign 调用。
 *
 * <p>message-service 要求所有非 /ws 请求都已认证，而 Feign 默认不转发请求头；
 * 不补这一层，教师回复会稳定 401/403。
 */
@Configuration
public class FeignConfig {

    @Bean
    public RequestInterceptor gatewayIdentityInterceptor() {
        return template -> {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return;
            }
            HttpServletRequest request = attributes.getRequest();
            String userId = request.getHeader("X-User-Id");
            String username = request.getHeader("X-Username");
            String role = request.getHeader("X-Role");
            if (userId != null) {
                template.header("X-User-Id", userId);
            }
            if (username != null) {
                template.header("X-Username", username);
            }
            if (role != null) {
                template.header("X-Role", role);
            }
        };
    }
}
