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
 * Feign 客户端配置
 * 包括 Token 透传、日志级别、重试策略
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

    /**
     * 头透传拦截器：把**调用者本人**的 Authorization 与网关注入的身份头带给下游。
     *
     * <p>2026-10-05（W26）补：原来只转发 {@code Authorization}，而本仓下游服务的
     * {@link com.academic.common.config.GatewayAuthFilter} 是**只认 {@code X-User-Id}/{@code X-Role}**
     * 的 —— 它从不解析 JWT。于是 {@code ai-service} 发出去的每一次 Feign 都落到
     * student-service 的 {@code .anyRequest().authenticated()} 上 ⇒ **403 空体**。
     *
     * <p>实测现场（{@code testsprite/local-check/_w26_ai_scores_probe.mjs}）：
     * 学生 27 有 29 行成绩，而 {@code GET /ai/analyze-scores/27} 稳定回
     * {@code {"totalCourses":0,"suggestion":"暂无成绩数据，请先录入成绩"}} ——
     * 内侧 catch 把那个 403 吞了，于是"取不到"被渲染成"没有"。
     *
     * <p>⚠️ 转发的是**调用方自己的身份**（网关已经写过一次），不是提权：学生换个 id 也照样
     * 被 student-service 的 {@code denyUnlessSelf} 拦下。这与"下游自己判权"的既有分工一致。
     */
    @Bean
    RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            requestTemplate.header("X-Requested-By", "ai-service");
            requestTemplate.header("Content-Type", "application/json");

            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return;
            }
            HttpServletRequest request = attributes.getRequest();

            // 传递 JWT Token 到下游服务
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null) {
                requestTemplate.header("Authorization", authHeader);
            }
            // ⭐ 下游真正用来建认证上下文的是这三个头（GatewayAuthFilter）
            copyHeader(request, requestTemplate, "X-User-Id");
            copyHeader(request, requestTemplate, "X-Role");
            copyHeader(request, requestTemplate, "X-Username");
        };
    }

    private static void copyHeader(HttpServletRequest request, feign.RequestTemplate template, String name) {
        String value = request.getHeader(name);
        if (value != null) {
            template.header(name, value);
        }
    }
}
