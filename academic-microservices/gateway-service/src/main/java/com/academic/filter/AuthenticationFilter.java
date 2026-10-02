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
    //
    // ⚠️ 白名单只解决"网关这一层不查 token"，**不等于下游会放行**：白名单命中的请求
    //    在网关是直接路过的，不会注入 X-User-Id/X-Role，下游服务的 GatewayAuthFilter
    //    没头就不建 Authentication，于是撞上各自的 .anyRequest().authenticated() → 403。
    //    所以往这里加地址时，**必须同时**在下游服务的 SecurityConfig 里 permitAll。
    //    2026-09-30 实测（college-service 的 PublicDirectoryAccessTest）：
    //    匿名 GET /api/auth/majors → 下游 403，学院/专业下拉全空、教师/辅导员注册页提交不了。
    private static final List<String> WHITE_LIST = Arrays.asList(
            "/api/auth/login",
            // 登录验证码（W1.12）。**必须在登录之前就能拿到**，所以是匿名可达的。
            // 加这条时同步在 user-service 的 SecurityConfig 里 permitAll 了
            // `/auth/captcha` —— 只加这里的话网关放行、下游 403，页面上就是"验证码加载不出来"。
            "/api/auth/captcha",
            "/api/auth/register/student",
            "/api/auth/register/teacher",
            "/api/auth/register/counselor",
            "/api/auth/colleges",
            "/api/auth/majors",
            // 班级目录：注册页（PC/小程序）选班级用。此前只在白名单里挂了名，
            // 网关有路由、下游没有实现，一直是 404 —— 2026-09-30 才在
            // college-service 加了 ClassController 并把 /classes/all 设为匿名可达。
            "/api/auth/classes",
            // ⚠️ 这里原本还有一条 `/api/auth/courses` —— 2026-10-02（W1.15）删掉了。
            //    它和 `/api/auth/classes` 是**方向相反**的两种幽灵：classes 是"白名单有名、
            //    下游没实现"，补齐了；courses 则是**从来没有人需要过它** ——
            //    `AuthController`（在 user-service 里）没有这个映射、PC 与小程序两端的
            //    `getAllCourses` 封装**全仓零调用方**（教师端课程页走的是
            //    `/teachers/courses`，另一条路）。白名单里挂着一条永远 404 的地址，
            //    唯一的作用就是让后来人以为"课程目录能匿名取"。删掉封装的同时把这条也删了，
            //    连同 auth-service `SecurityConfig` 里那句 `permitAll` —— 两处是同一次改动的两半。
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
    /** 账号前缀。非管理员只放"本人的资料/设置"，其余一律管理员（见 hasPermission 里的那一块）。 */
    private static final String PREFIX_USERS = "/api/users";

    /**
     * 账号与权限管理面（auth-service 的三组控制器）。2026-10-01 第二十四轮新增闸门。
     *
     * <p>这三条前缀此前**不被任何前缀检查命中**，于是 role 1/2/3 一路落到
     * {@link #hasPermission} 末尾的 {@code return true}（RBAC 那段是死的，见其注释）。
     * 用学生账号（{@code 2023020616}）的真实 JWT 走网关实测：
     * <pre>
     *   GET  /api/roles/count                                    → 200（返回 4 条角色）
     *   PUT  /api/roles/update/99999                             → 200（写方法也通，只因 id 不存在才没落库）
     *   GET  /api/admin/statistics（同一时刻的对照）              → 403
     * </pre>
     * ⇒ 一个学生登录后本可以 {@code POST /api/roles/add} 建角色、{@code DELETE /api/roles/delete/{id}} 删角色，
     * 与 2026-09-30 修掉的 {@code /api/users/**} 是同一族缺陷。
     *
     * <p>加闸不会影响任何现有功能：这三条前缀在**全仓零调用方**（前端所有角色/权限调用打的都是
     * {@code /api/admin/permissions/*}，落在 admin-service，不经过这里），
     * 网关自己的 {@code RolePermissionCache} 取 RBAC 是**直连 auth-service**、也不过网关。
     */
    private static final String PREFIX_ROLES = "/api/roles";
    private static final String PREFIX_PERMISSIONS = "/api/permissions";
    private static final String PREFIX_ROLE_PERMISSIONS = "/api/role-permissions";

    // ==================== 学院管理员的自助账号路径 ====================
    //
    // 背景：学院管理员（role=5）原先被一律挡在 `/api/college-admin` + `/api/admin`
    // 之内，连自己的账号资料、通知设置、改密码都拿不到（三支全是 403 权限不足），
    // 结果是**学院管理员在本系统里改不了自己的密码**。
    //
    // 这里只放开"自助"那一小撮，**不是**把 `/api/users/**` 整个前缀放开 ——
    // 那个前缀下面还挂着管理员专用接口：
    //   /api/users/list、/api/users/add、/api/users/delete/{id}、/api/users/count
    //   /api/users/by-username/{name}  ← 直接返回含密码哈希的原始 User 实体
    //   /api/users/update/{id}         ← ★ 请求体是完整 User 实体，改别人的 role/status 就是提权
    // 同理 `/api/auth/**` 也不能整段放开：`/api/auth/register/student` 能建账号，
    // `/api/auth/info/{id}` 也会绕过下面的归属校验。
    // （原话点名的是 `/api/auth/register/admin` —— 它能造系统管理员账号，那个端点 2026-10-01 已删；
    //  裸的 `/api/auth/register` 同日也按"零调用方"删了，所以现在这一段伞下**只剩三条自助注册路径**，
    //  而它们的约束在 user-service 自己的 SecurityConfig 里逐条列着。）

    /** 改自己的密码。 */
    private static final String AUTH_CHANGE_PASSWORD = "/api/auth/change-password";
    /** 登出 / 刷新 token —— 都属于自助，与角色无关。 */
    private static final Pattern SELF_AUTH_PATTERN =
            Pattern.compile("^/api/auth/(logout|refresh-token)$");

    /**
     * 非管理员的**自助**账号路径：本人的资料 / 本人的设置，且 id 必须等于 token 里的 userId。
     *
     * <p>2026-09-30 由 `^/api/users/(?:info/)?(\d+)(?:/settings)?$` **扩到含 `update/`**：
     * 三个角色的设置页都在用 `PUT /users/update/{自己的id}` 改姓名/邮箱/电话
     * （`studentAPI|teacherAPI|counselorAPI.updateProfile`），从前这条对非管理员一律 403
     * —— 也就是"学生改不了自己的邮箱"。扩的是**本人可改的范围**，不是放给别人的范围：
     * `(\d+)` 仍然必须等于 token 里的 userId，别人的 id 照样 403。
     *
     * <p>⚠️ 改自己的 role/status 这条**网关拦不住**（角色在请求体里，网关不看 body），
     * 由 `user-service` 的 `UserController.updateUser` 剥掉 —— 见那边的注释。
     */
    private static final Pattern USER_SELF_PATTERN =
            Pattern.compile("^/api/users/(?:(?:info|update)/)?(\\d+)(?:/settings)?$");

    @Value("${spring.cloud.gateway.jwt.secret:academic-jwt-secret-key-for-academic-warning-system-2026}")
    private String jwtSecret;

    /**
     * 与四处源码字面量逐字相同。**网关是本工程里唯一不依赖 `academic-common` 的模块**
     * （它是 WebFlux 网关，只带最小依赖），所以那句本可以共享的告警在
     * `academic-common/config/JwtSecretGuard.java` 里，这里只能各写一份 —— 这是模块边界的代价，
     * 不是重复代码没收拾。
     */
    private static final String DEV_DEFAULT_SECRET =
            "academic-jwt-secret-key-for-academic-warning-system-2026";

    @PostConstruct
    public void init() {
        log.info("AuthenticationFilter 初始化完成，密钥长度: {}", jwtSecret.length());
        // 网关是**校验方**：它用哪个密钥，等于全系统认哪个密钥。默认值仍可用（演示不受影响），
        // 但必须让"还在用开发默认值"这件事在启动日志里看得见。
        if (DEV_DEFAULT_SECRET.equals(jwtSecret)) {
            log.warn("");
            log.warn("========================================================================");
            log.warn("  ⚠️  [gateway AuthenticationFilter] JWT 密钥正在使用**开发默认值**（源码里的字面量）。");
            log.warn("      后果：任何拿到本仓库的人都能伪造任意角色（含管理员）的 token。");
            log.warn("      本地演示可忽略；但**不要**把这个默认值带到任何对外可访问的环境。");
            log.warn("      换掉它：设置环境变量 JWT_SECRET（auth/user/gateway 三处会一起生效）。");
            log.warn("========================================================================");
            log.warn("");
        } else {
            log.info("[gateway AuthenticationFilter] JWT 密钥已由外部配置提供（非开发默认值）。");
        }
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
            Matcher m = USER_SELF_PATTERN.matcher(path);
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
        // ⚠️ `/api/college-admin/**` 此前**漏在下面所有前缀检查之外**，一路落到函数末尾的
        // `return true` —— 学生 / 教师 / 辅导员的 token 都能打到学院管理员控制器。
        // 那个控制器里既有全院花名册，也有「删预警」这种真删行的写接口，所以这不是理论问题。
        // 注意 `PREFIX_ADMIN = "/api/admin"` 与 `/api/college-admin` 不构成前缀关系，
        // 上面那句挡不住它 —— 差的就是这一句。role=4 与 role=5 在上面两段已经 return。
        if (path.startsWith(PREFIX_COLLEGE_ADMIN)) {
            return false;
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

        // ==================== /api/users/** 的角色门禁 ====================
        // 从前这一整段对 role 1/2/3 是"不匹配任何受限前缀 → 落到函数末尾 return true"，
        // 即**任何已登录用户**（含学生）都能（2026-09-30 读码确认，网关这层完全不拦）：
        //   GET    /api/users/list              全部账号的邮箱/手机号
        //   POST   /api/users/add               ★ 请求体是完整 User 实体、自带 role
        //                                         ⇒ **学生直接建出一个系统管理员**
        //   PUT    /api/users/update/{id}       ★ 改任意账号（含把自己的 role 改成 4 提权）
        //   DELETE /api/users/delete/{id}       删任意账号
        //   GET    /api/users/by-username/{n}   原始 User 实体（含 BCrypt 密码哈希）
        //   GET    /api/users/count             账号总数
        // 该对非管理员开放的只有"本人的资料 / 本人的设置"，且 id 必须等于 token 里的 userId。
        // ⚠️ 与 `/api/auth/**` 同理（那个前缀过去也挂过一条"管理员专用"的闸，2026-10-01 随端点一起删了）：
        //    user-service 没有 GatewayAuthFilter、而且是本机可直连的，**网关不是网络边界**
        //    —— 那边 UserController 里做了同样的判定。
        if (path.equals(PREFIX_USERS) || path.startsWith(PREFIX_USERS + "/")) {
            Matcher m = USER_SELF_PATTERN.matcher(path);
            if (m.matches() && userId != null) {
                try {
                    return Long.parseLong(m.group(1)) == userId.longValue();
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
            log.warn("拒绝非管理员访问 /api/users 管理端点: role={}, path={}", role, path);
            return false;
        }

        // ==================== /api/roles|permissions|role-permissions 的角色门禁 ====================
        // 见上面 PREFIX_ROLES 的注释：这三条前缀此前没有任何检查命中，学生 token 实测能读到
        // 角色表、也能走通写方法。角色 4（管理员）在上面 `role == ROLE_ADMIN` 处已 return true；
        // 角色 5（学院管理员）管的是本院事务，不该管全局角色 ⇒ 一并拒绝。
        if (path.startsWith(PREFIX_ROLES)
                || path.startsWith(PREFIX_PERMISSIONS)
                || path.startsWith(PREFIX_ROLE_PERMISSIONS)) {
            log.warn("拒绝非管理员访问账号/权限管理端点: role={}, path={}", role, path);
            return false;
        }

        // ==================== 这里原来有 `/api/auth/register/**` 的角色闸（2026-10-01 删） ====================
        //
        // 历史：`/api/auth/register/admin` 曾经是个真洞 —— 任何已登录角色（含学生）
        // POST 它并带 `role:4` 就能造出系统管理员。2026-09-30 用两层修（网关要求 role=4 +
        // 控制器按已验签 token 再判一次）；2026-10-01 发现它零调用方、且与 `/users/add` 重复，
        // 就**删掉端点本身** ⇒ 攻击面消失，比留着再加闸更彻底。管理员建号现在只剩
        // AdminUsers.vue → `POST /api/users/add` 一条活路径（上面 PREFIX_USERS 那段管着）。
        //
        // 剩下的那段 `path.startsWith("/api/auth/register")` 闸，**原本只拦得住裸的
        // `/api/auth/register`**（白名单放了三条自助路径，它们匹配后直接 return，走不到这里；
        // 这一点原注释自己就写明了）。而裸端点在 2026-10-01 也按"零调用方 = 删"删掉了
        // （与 `/register/student` 是同一个调用、一字不差）⇒ **这段闸再没有任何请求能命中**，
        // 属死代码，一并删除。两处是**同一次改动的两半**，只删一处会留下一个永远不成立的判断。
        //
        // ⚠️ 保留这条注释是因为下面这句仍然成立、且值得记住：
        //    **网关不是网络边界。** user-service 没有 GatewayAuthFilter、而且本机可直连，
        //    它的 `/auth` 段对任何持 token 的人都通（见本文件末尾的 return true）。
        //    所以 `/api/auth/register/{student,teacher,counselor}` 三条自助路径的约束
        //    **只在 user-service 自己的 SecurityConfig 里**（那边刻意逐个列出、不用通配），
        //    网关白名单只是"放进来"而已，不是"管住"。

        // RBAC 细粒度校验（有 RBAC 数据时才生效）
        //
        // ⚠️ 2026-10-01 第十六轮查证：`hasRbacData()` **恒为 false**
        //    （RolePermissionCache 的拉取必 403，且失败后无重试、无刷新），
        //    所以**下面这个块从来没有执行过** —— 网关实际生效的只有上面的路径前缀层。
        //
        // ⚠️ 别为了"让 RBAC 生效"去放开 /role-permissions/** 的匿名访问：
        //    角色码（库 ROLE_STUDENT vs 代码 "student"）与权限码
        //    （库 PERM_STUDENT_VIEW vs 代码 "student:read"）两处都不匹配，
        //    RBAC 一旦真的加载进来，会反过来 403 掉 role 1/2/3 的**全部业务接口**。
        //    详见 RolePermissionCache 类注释与手册 §6.16。
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
