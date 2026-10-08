package com.academic.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ai-service 的 Feign 是否把**调用者的身份头**带给下游（W26）。
 *
 * <p>钉的缺陷：`FeignConfig` 原来只转发 `Authorization`，而本仓下游服务建认证上下文
 * 靠的是 {@link com.academic.common.config.GatewayAuthFilter} 读的
 * `X-User-Id` / `X-Role` —— 它**从不解析 JWT**。所以 ai-service 发的每一次 Feign
 * 都落到下游的 `.anyRequest().authenticated()` 上，回 **403 空体**。
 * 后果不是报错，而是更坏的一种：`AIController.analyzeScores` 的内层 catch 把 403 吞了，
 * 于是给一个库里有 29 行成绩的学生稳定回「暂无成绩数据，请先录入成绩」。
 *
 * <p>实测证据（备用实例的 Feign FULL 日志，2026-10-05）：
 * 修后出站请求带上 `X-User-Id: 27` / `X-Role: 1` / `X-Username: probe27`。
 *
 * <p>判据两组：**有身份头时必须原样带上**（否则又 403）；**没有时必须不凭空造**
 * （凭空造等于提权）。
 */
class FeignIdentityHeaderTest {

    private final RequestInterceptor interceptor = new FeignConfig().requestInterceptor();

    @AfterEach
    void clearContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static String header(RequestTemplate template, String name) {
        Collection<String> values = template.headers().get(name);
        return values == null || values.isEmpty() ? null : values.iterator().next();
    }

    @Test
    @DisplayName("有身份头 → X-User-Id / X-Role / X-Username 必须原样带给下游")
    void testIdentityHeadersAreForwarded() {
        MockHttpServletRequest incoming = new MockHttpServletRequest();
        incoming.addHeader("X-User-Id", "27");
        incoming.addHeader("X-Role", "1");
        incoming.addHeader("X-Username", "2023020616");
        incoming.addHeader("Authorization", "Bearer token-abc");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(incoming));

        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertEquals("27", header(template, "X-User-Id"), "缺 X-User-Id ⇒ 下游 403");
        assertEquals("1", header(template, "X-Role"), "缺 X-Role ⇒ 下游 403");
        assertEquals("2023020616", header(template, "X-Username"));
        assertEquals("Bearer token-abc", header(template, "Authorization"));
        assertEquals("ai-service", header(template, "X-Requested-By"));
    }

    @Test
    @DisplayName("没有身份头 → 一个都不许凭空造（凭空造 = 提权）")
    void testNoIdentityHeadersMeansNoneSent() {
        MockHttpServletRequest incoming = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(incoming));

        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertNull(header(template, "X-User-Id"), "不能编一个用户 id 出来");
        assertNull(header(template, "X-Role"), "不能编一个角色出来");
        assertNull(header(template, "X-Username"));
    }

    @Test
    @DisplayName("连请求上下文都没有（如定时任务里发 Feign）→ 不抛异常，也不造头")
    void testNoRequestContextIsSafe() {
        RequestContextHolder.resetRequestAttributes();

        RequestTemplate template = new RequestTemplate();
        assertDoesNotThrow(() -> interceptor.apply(template));
        assertNull(header(template, "X-User-Id"));
        assertNull(header(template, "X-Role"));
    }
}
