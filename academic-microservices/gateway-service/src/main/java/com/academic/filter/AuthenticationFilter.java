package com.academic.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JWT 认证全局过滤器
 * 与 auth-service 共用同一密钥，验证所有进入网关的请求。
 * 角色编码：1=学生 2=教师 3=辅导员 4=管理员
 */
@Component
public class AuthenticationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationFilter.class);

    private final RolePermissionCache rbacCache;

    public AuthenticationFilter(RolePermissionCache rbacCache) {
        this.rbacCache = rbacCache;
    }

    // 角色编码（与 academic-common ApiConstants 保持一致）
    private static final int ROLE_STUDENT = 1;
    private static final int ROLE_TEACHER = 2;
    private static final int ROLE_COUNSELOR = 3;
    private static final int ROLE_ADMIN = 4;
    private static final int ROLE_COLLEGE_ADMIN = 5;

    // 白名单：无需 Token 即可访问
    private static final List<String> WHITE_LIST = Arrays.asList(
            "/api/auth/login",
            "/api/auth/register/student",
            "/api/auth/register/teacher",
            "/api/auth/register/counselor",
            "/api/auth/colleges",
            "/api/auth/majors",
            "/api/auth/courses",
            "/api/colleges/all",
            "/actuator/health",
            "/ws/**"
    );

    // 各角色专属路径前缀
    private static final String PREFIX_ADMIN = "/api/admin";
    private static final String PREFIX_TEACHER = "/api/teachers";
    private static final String PREFIX_STUDENT = "/api/students";
    private static final String PREFIX_COUNSELOR = "/api/counselors";
    private static final String PREFIX_COLLEGE_ADMIN = "/api/college-admin";

    // ==================== 学院管理员的自助账号路径 ====================
    //
    // 背景：学院管理员（role=5）原先被一律挡在 `/api/college-admin` + `/api/admin`
    // 之内，连自己的账号资料、通知设置、改密码都拿不到（三支全是 403 权限不足），
    // 结果是**学院管理员在本系统里改不了自己的密码**。
    //
    // 这里只放开"自助"那一小撮，**不是**把 `/api/users/**` 整个前缀放开 ——
    // 那个前缀下面还挂着管理员专用接口：
    //   /api/users/list、/api/users/add、/api/users/update/{id}、/api/users/delete/{id}
    //   /api/users/by-username/{name}  ← 直接返回含密码哈希的原始 User 实体
    // 同理 `/api/auth/**` 也不能整段放开：`/api/auth/register/admin` 能让学院管理员
    // 自己造一个系统管理员账号，`/api/auth/info/{id}` 也会绕过下面的归属校验。

    /** 改自己的密码。 */
    private static final String AUTH_CHANGE_PASSWORD = "/api/auth/change-password";
    /** 登出 / 刷新 token —— 都属于自助，与角色无关。 */
    private static final Pattern SELF_AUTH_PATTERN =
            Pattern.compile("^/api/auth/(logout|refresh-token)$");
    /** 账号资料与自助设置，**且 id 必须是自己**（见 hasPermission 里的 userId 比对）。 */
    private static final Pattern OWN_ACCOUNT_PATTERN =
            Pattern.compile("^/api/users/(?:info/)?(\\d+)(?:/settings)?$");

    @Value("${spring.cloud.gateway.jwt.secret:academic-jwt-secret-key-for-academic-warning-system-2026}")
    private String jwtSecret;

    @PostConstruct
    public void init() {
        log.info("AuthenticationFilter 初始化完成，密钥长度: {}", jwtSecret.length());
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // 白名单直接放行
        if (isWhiteListed(path)) {
            return chain.filter(exchange);
        }

        // 验证 Token
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("缺少 Authorization 头: {}", path);
            return unauthorized(exchange, "缺少认证信息");
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = validateToken(token);
            if (claims == null) {
                return unauthorized(exchange, "Token 无效或已过期");
            }

            Long userId = claims.get("userId", Long.class);
            String username = claims.get("username", String.class);
            Integer role = claims.get("role", Integer.class);

            if (userId == null || role == null) {
                return unauthorized(exchange, "Token 信息不完整");
            }

            // 角色权限校验（路径前缀 + RBAC）
            if (!hasPermission(request, role, userId)) {
                log.warn("权限不足: userId={}, role={}, path={}", userId, role, path);
                return forbidden(exchange, "权限不足");
            }

            // 将用户信息注入请求头，下游微服务可直接读取
            ServerHttpRequest modifiedRequest = request.mutate()
                    .header("X-User-Id", String.valueOf(userId))
                    .header("X-Username", username != null ? username : "")
                    .header("X-Role", String.valueOf(role))
                    .build();

            log.debug("JWT 验证通过: userId={}, role={}, path={}", userId, role, path);
            return chain.filter(exchange.mutate().request(modifiedRequest).build());

        } catch (Exception e) {
            log.error("Token 验证异常: {}", e.getMessage());
            return unauthorized(exchange, "Token 验证失败");
        }
    }

    /**
     * 解析并验证 JWT Token
     */
    private Claims validateToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (claims.getExpiration().before(new Date())) {
                log.warn("Token 已过期");
                return null;
            }
            return claims;
        } catch (Exception e) {
            log.warn("Token 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 角色-路径权限校验（增强版：路径前缀 + RBAC 双重校验）
     * - 管理员(4)：可访问所有路径
     * - 学院管理员(5)：可访问 college-admin 和 admin 前缀路径
     * - 其他角色：先路径前缀检查，再 RBAC 细粒度检查
     */
    private boolean hasPermission(ServerHttpRequest request, Integer role, Long userId) {
        String path = request.getPath().value();
        String method = request.getMethod() != null ? request.getMethod().name() : "GET";

        // 管理员可访问所有
        if (role == ROLE_ADMIN) {
            return true;
        }

        // 学院管理员：college-admin 与 admin 前缀，外加**仅限本人**的自助账号路径
        if (role == ROLE_COLLEGE_ADMIN) {
            if (path.startsWith(PREFIX_COLLEGE_ADMIN) || path.startsWith(PREFIX_ADMIN)) {
                return true;
            }
            if (AUTH_CHANGE_PASSWORD.equals(path) || SELF_AUTH_PATTERN.matcher(path).matches()) {
                return true;
            }
            // 归属校验：路径里的 id 必须等于 token 里的 userId。
            // 缺了这一步，学院管理员就能读**任意**用户的资料与设置
            // （下游 UserController 的 getSettings/updateSettings 不做归属判断）。
            Matcher m = OWN_ACCOUNT_PATTERN.matcher(path);
            if (m.matches() && userId != null) {
                try {
                    return Long.parseLong(m.group(1)) == userId.longValue();
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
            return false;
        }

        // 路径前缀快速检查
        if (path.startsWith(PREFIX_ADMIN)) {
            return false; // 非管理员不可访问 admin 路径
        }
        if (path.startsWith(PREFIX_TEACHER) && role != ROLE_TEACHER) {
            return false;
        }
        if (path.startsWith(PREFIX_STUDENT) && role != ROLE_STUDENT) {
            return false;
        }
        if (path.startsWith(PREFIX_COUNSELOR) && role != ROLE_COUNSELOR) {
            return false;
        }

        // RBAC 细粒度校验（有 RBAC 数据时才生效）
        if (rbacCache.hasRbacData()) {
            String permissionCode = buildPermissionCode(path, method);
            if (permissionCode != null) {
                boolean rbacAllowed = rbacCache.hasPermission("user", role, permissionCode);
                if (!rbacAllowed) {
                    log.debug("RBAC拒绝: role={}, path={}, perm={}", role, path, permissionCode);
                    return false;
                }
            }
        }

        // 公共接口允许已认证用户访问
        return true;
    }

    /**
     * 根据 URL 路径和 HTTP 方法构建权限编码
     * 例如 GET /api/students → "student:read"
     */
    private String buildPermissionCode(String path, String method) {
        String resource;
        if (path.startsWith(PREFIX_STUDENT)) resource = "student";
        else if (path.startsWith(PREFIX_TEACHER)) resource = "teacher";
        else if (path.startsWith(PREFIX_COUNSELOR)) resource = "counselor";
        else if (path.startsWith(PREFIX_ADMIN)) resource = "admin";
        else if (path.startsWith(PREFIX_COLLEGE_ADMIN)) resource = "college_admin";
        else return null; // 公共路径不需要权限编码

        String action = "GET".equalsIgnoreCase(method) ? "read" : "write";
        return resource + ":" + action;
    }

    private boolean isWhiteListed(String path) {
        return WHITE_LIST.stream().anyMatch(pattern ->
                path.matches(pattern.replace("**", ".*")));
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        String body = String.format("{\"code\":401,\"message\":\"%s\"}", message);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }

    private Mono<Void> forbidden(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        String body = String.format("{\"code\":403,\"message\":\"%s\"}", message);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }

    @Override
    public int getOrder() {
        return -80;
    }
}
