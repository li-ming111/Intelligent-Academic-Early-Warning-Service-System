package com.academic.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 网关注入的身份头**覆盖**客户端自己带的同名头吗？（W26，2026-10-05）
 *
 * <p>为什么必须实测：W26 给 {@code POST /warnings/scan} 补了角色闸（只放行 3/4/5），
 * 而这个闸读的是 {@code X-Role}。它成立的前提是"网关写进去的那个 {@code X-Role} 说了算"。
 * 若 {@code request.mutate().header(...)} 是**追加**而不是**替换**，那么任何学生只要在请求里
 * 自己带一个 {@code X-Role: admin}，下游就会同时收到两个值 —— 具体读到哪个取决于下游，
 * 闸就可能被整个绕过。**这正是本仓栽过的形状**（W17：守卫只认下划线键名，而真实 UI 发的是驼峰，
 * 于是一行代码没写错的守卫整场被跳过；判据太窄 ⇒ 探针一直是绿的）。
 *
 * <p>所以这里不去"读 Spring 源码推断语义"，而是**把 filter 真跑一遍**，
 * 用签名正确的学生 JWT + 伪造的 {@code X-Role}/{@code X-User-Id} 打进去，看链上收到的到底是什么。
 *
 * <p>三组判据：
 * <ol>
 *   <li>伪造的管理员头 → 下游收到的必须是 JWT 里的 {@code 1}/{@code 27}（伪造值一个字都不许留）；</li>
 *   <li>大小写变体 + 重复头（{@code x-role} / 两个 {@code X-Role}）→ 同样只留 JWT 那个；</li>
 *   <li>反向对照：**权限判定用的是 JWT 的 role，不是这个头** —— 学生 JWT 打 {@code /api/admin/**}，
 *       即使伪造 {@code X-Role: admin} 也必须被挡在网关（否则闸就真成了摆设）。</li>
 * </ol>
 */
class AuthenticationFilterIdentityHeaderTest {

    private static final String DEV_SECRET =
            "academic-jwt-secret-key-for-academic-warning-system-2026";

    private AuthenticationFilter filter() {
        AuthenticationFilter f = new AuthenticationFilter(new RolePermissionCache());
        // 字段靠 @Value 注入，纯单测里不会跑容器，直接填默认值（与线上默认一致）
        ReflectionTestUtils.setField(f, "jwtSecret", DEV_SECRET);
        return f;
    }

    /** 用网关认的那个密钥签一个真 token —— 伪造头不算伪造 token。 */
    private static String token(long userId, String username, int role) {
        SecretKey key = Keys.hmacShaKeyFor(DEV_SECRET.getBytes(StandardCharsets.UTF_8));
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("role", role);
        Date now = new Date();
        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 3_600_000L))
                .signWith(key)
                .compact();
    }

    /** 跑一遍 filter，把"到达链上的请求"抓出来。返回 null 表示被网关拒了（没到链上）。 */
    private static ServerWebExchange run(AuthenticationFilter filter, MockServerHttpRequest request) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<ServerWebExchange> seen = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        GatewayFilterChain chain = ex -> {
            seen.set(ex);
            calls.incrementAndGet();
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();
        return seen.get();
    }

    private static String header(ServerWebExchange exchange, String name) {
        if (exchange == null) return null;
        return exchange.getRequest().getHeaders().getFirst(name);
    }

    @Test
    @DisplayName("伪造 X-Role/X-User-Id → 下游只能看到 JWT 里的角色，伪造值一个字都不许留")
    void testForgedIdentityHeadersAreOverwritten() {
        MockServerHttpRequest request = MockServerHttpRequest
                .method(HttpMethod.POST, "/api/warnings/scan")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(27L, "2023020616", 1))
                .header("X-User-Id", "999")
                .header("X-Role", "admin")
                .build();

        ServerWebExchange downstream = run(filter(), request);

        assertNotNull(downstream, "学生打 /api/warnings/scan 在网关这一层是放行的（闸在下游）");
        assertEquals("27", header(downstream, "X-User-Id"),
                "下游若看到 999，就是客户端伪造成功 —— 下游所有归属闸都会认错人");
        assertEquals("1", header(downstream, "X-Role"),
                "下游若看到 admin，W26 给 /warnings/scan 补的角色闸就被整个绕过");
        // 网关自己写的那两个值就是全部，不能既留伪造的又加真的
        List<String> roleValues = downstream.getRequest().getHeaders().get("X-Role");
        assertEquals(1, roleValues.size(), "X-Role 必须只有一个值，实际: " + roleValues);
    }

    @Test
    @DisplayName("大小写变体 / 重复头 → 同样只剩 JWT 那一个值")
    void testCaseVariantsAndDuplicatesCannotSneakThrough() {
        MockServerHttpRequest request = MockServerHttpRequest
                .method(HttpMethod.POST, "/api/warnings/scan")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(27L, "2023020616", 1))
                .header("x-role", "admin")          // 小写变体
                .header("X-Role", "4")              // 又一个
                .build();

        ServerWebExchange downstream = run(filter(), request);

        assertNotNull(downstream);
        List<String> roleValues = downstream.getRequest().getHeaders().get("X-Role");
        assertEquals(List.of("1"), roleValues,
                "客户端送进来的 X-Role 必须被整个替换掉，实际: " + roleValues);
    }

    @Test
    @DisplayName("反向对照：准入判的是 JWT 的 role —— 学生伪造 X-Role: admin 也打不进 /api/admin/**")
    void testForgedHeaderDoesNotBuyPermission() {
        MockServerHttpRequest request = MockServerHttpRequest
                .method(HttpMethod.GET, "/api/admin/statistics")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(27L, "2023020616", 1))
                .header("X-Role", "admin")
                .header("X-User-Id", "1")
                .build();

        ServerWebExchange downstream = run(filter(), request);

        assertNull(downstream,
                "伪造 X-Role 头若能让学生的请求进 /api/admin/**，那这个头就是提权通道");
    }
}
