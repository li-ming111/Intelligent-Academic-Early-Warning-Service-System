package com.academic.config;

import feign.Logger;
import feign.RequestInterceptor;
import feign.Retryer;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Feign 客户端配置 - 身份透传 + 重试
 *
 * ⚠️ 关键：下游服务的鉴权**不看 JWT**。
 * 鉴权在 gateway 的 AuthenticationFilter 里做完，由它把身份写进
 * X-User-Id / X-Username / X-Role 三个请求头；下游服务的 SecurityConfig 只写
 * `.anyRequest().authenticated()`，也就是说**有这三个头就认，没有就 403**
 * （academic-common 的 GatewayAuthFilter 就从这三个头构建 SecurityContext）。
 * JWT 只是给 gateway 用的，下游不解析。
 *
 * 而 Feign 是服务间直连，**绕过了 gateway** —— 这三个头不会自己出现，
 * 必须由这里从当前请求上原样抄一份带过去。此前只抄 Authorization，
 * 下游拿不到身份：有本地兜底逻辑的接口看不出问题，纯 Feign 的接口一律 403，
 * 表现为 HTTP 200 里返回 code:500（如 /admin/warnings/{id}、/admin/messages/list）。
 *
 * 抄这三个头是安全的：gateway 用的是 ServerHttpRequestBuilder.header()，
 * 语义是**覆盖**而非追加，所以客户端自己伪造的 X-User-Id/X-Role 到不了这里。
 */
@Configuration
public class FeignConfig {

    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

    @Bean
    Retryer feignRetryer() {
        return new Retryer.Default(1000, 2000, 3);
    }

    @Bean
    RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            requestTemplate.header("X-Requested-By", "admin-service");
            requestTemplate.header("Content-Type", "application/json");

            // 身份透传：从当前 HTTP 请求抄一份 Authorization + 网关那三个头给下游。
            // 少了后三个，下游一律 403（详见类注释）。
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                copyHeader(request, requestTemplate, "Authorization");
                copyHeader(request, requestTemplate, "X-User-Id");
                copyHeader(request, requestTemplate, "X-Username");
                copyHeader(request, requestTemplate, "X-Role");
            }
        };
    }

    /** 请求头有就抄，没有就不动 —— 不要塞空串，空串会让下游把头当成"存在但不是数字"。 */
    private void copyHeader(HttpServletRequest request,
                            feign.RequestTemplate requestTemplate,
                            String name) {
        String value = request.getHeader(name);
        if (value != null && !value.isEmpty()) {
            requestTemplate.header(name, value);
        }
    }
}
