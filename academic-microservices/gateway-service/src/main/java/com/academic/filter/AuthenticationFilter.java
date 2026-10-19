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
 * 角色编码：1=学生 2=教师 3=辅导员 4=管理员 5=学院管理员
 *
 * <p><b>两层准入</b>（2026-10-02 W3.3 起）：
 * <ul>
 *   <li><b>路径前缀层</b>：内置角色 1–5 的准入。每个角色有自己的 URL 前缀
 *       （{@code /api/students} 是学生本人的家、{@code /api/teachers} 是教师的家…），
 *       另有"本人的资料/设置"这类与角色无关的归属校验。</li>
 *   <li><b>权限表层</b>：只对**自定义角色（{@code users.role ≥ 6}）**生效 ——
 *       管理员在权限管理页新建的角色，能力完全来自 {@code role_permissions} 表
 *       （见 {@link #customRoleAllows} 与 {@link RolePermissionCache}）。</li>
 * </ul>
 * 内置角色**不经过**权限表层，所以这一层的引入对现有页面不可能产生 403。
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
            // WebSocket 实时推送（STOMP）。2026-10-19（W49）修：原来这条是 `"/ws/**"`，
            // 配上当时用 `String.matches` 的写法（全串匹配）**匹配不上客户端实际连的 `/ws`**
            // ⇒ 浏览器在网关这一跳就被 401 挡下，整条实时推送链从未成立（W48 发现）。
            // 现在 `globMatches` 把结尾的 `/**` 解释成"零段或多段"，所以这条同时覆盖
            // `/ws`、`/ws/`、`/ws/info`、`/ws/**`。⚠️ 别把 glob 改回 replace("**",".*")。
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

    // 下面两条**只给自定义角色（role ≥ 6）用** —— 见 hasPermission 里那一块。
    // ⚠️ 2026-10-08（W31）订正：这里原本接着写「内置角色走的是各自的角色前缀
    // （/api/teachers、/api/students…），**不经这两条**」—— **后半句是错的**。
    // "不经这两条常量" ≠ "不被放行"：内置角色 1/2/3 不匹配任何受限前缀，
    // 结果是**穿过整个 hasPermission 被末尾 return true 放行**（这正是闸漏掉的原因）。
    // `/api/courses` 已就地补闸（见 hasPermission 里 W31 那一段）；
    // `/api/warnings` 走的是**控制器层**闸（W26 `IntelligentWarningController.isScanAllowed`），
    // 网关这层刻意仍放行 —— 两处的判据不同，别按一边推另一边。
    private static final String PREFIX_COURSE = "/api/courses";
    // ⚠️ `/api/course-plans` **不是** `/api/courses` 的前缀（差在 `-plans`），必须单列一条 ——
    //    否则它会像 `/api/college-admin` 当年那样穿过所有前缀检查、被末尾 `return true` 放行。
    //    2026-10-17（W48）：这一面此前无网关路由，现在接上了 ⇒ 同步补闸，顺序是**先闸后路由**。
    private static final String PREFIX_COURSE_PLANS = "/api/course-plans";
    private static final String PREFIX_WARNING = "/api/warnings";

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
    // 同理 `/api/auth/**` 也不能整段放开：`/api/auth/register/student` 能建账号。
    // （⚠️ 2026-10-10（W34）订正：这里原还点名 `/api/auth/info/{id}`「也会绕过归属校验」——
    //  它**确实绕过**（无前缀检查 + 下游只要求已认证 + 方法体不判归属），但**已按"零调用方"整条删掉**
    //  （见 `AuthController` 里 W34 那一段留档），本句不再成立。原话点名的是
    //  `/api/auth/register/admin` —— 它能造系统管理员账号，那个端点 2026-10-01 已删；
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
     *
     * <p>2026-10-03（W8.3）再补 `login-logs`：`GET /api/users/{id}/login-logs` 是
     * 2026-10-02 才加的"我最近从哪儿登录过"读取口（`UserController` 里带 `isAdminOrSelf`
     * 归属校验），学生端设置页的「登录历史」卡片就打它。**但这条白名单当时没跟着扩**
     * ⇒ 学生/教师/辅导员一律在网关吃 403（下游 `:8082` 直连同一 token 是 200 有数据），
     * 页面于是永远空着 + 控制台「加载登录历史失败」。与上面 `update/` 是同一次遗漏的两种表现。
     * 放开的是**本人**这一条：`(\d+)` 仍必须等于 token 的 userId，别人的 id 照样 403，
     * 且下游还会再判一次（网关不是网络边界，见下面那段注释）。
     */
    private static final Pattern USER_SELF_PATTERN =
            Pattern.compile("^/api/users/(?:(?:info|update)/)?(\\d+)(?:/settings|/login-logs)?$");

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
     * 角色-路径权限校验。
     *
     * <ul>
     *   <li><b>管理员(4)</b>：可访问所有路径；</li>
     *   <li><b>学院管理员(5)</b>：**只有** college-admin 前缀，外加仅限本人的自助账号路径
     *       （<b>不含</b> admin 前缀，见下面 role 5 那段的说明）；</li>
     *   <li><b>内置角色 1/2/3</b>：路径前缀层（各自的角色前缀 + 自助账号路径）；</li>
     *   <li><b>自定义角色（id ≥ 6，管理员在权限管理页新建的）</b>：**权限表驱动**，
     *       见 {@link #customRoleAllows}。</li>
     * </ul>
     *
     * <p>包级可见（不是 {@code private}）是为了让单测直接打这个判定 ——
     * 走 {@code filter()} 要造 {@code ServerWebExchange}，把判据埋在 mock 里反而看不清。
     */
    boolean hasPermission(ServerHttpRequest request, Integer role, Long userId) {
        String path = request.getPath().value();
        String method = request.getMethod() != null ? request.getMethod().name() : "GET";

        // 管理员可访问所有
        if (role == ROLE_ADMIN) {
            return true;
        }

        // 学院管理员：**只有** college-admin 前缀，外加**仅限本人**的自助账号路径
        //
        // ⚠️ W33（2026-10-09）：原来这里写的是 `path.startsWith(PREFIX_COLLEGE_ADMIN)
        //    || path.startsWith(PREFIX_ADMIN)` —— 学院管理员因此能打**整个** `/api/admin/**`。
        //    那段注释把理由写成"college-admin 与 admin 前缀"，但**学院管理员是本院管理员，
        //    `/api/admin/**` 是全局管理员面**（改任意账号的角色 / 建号 / 删号 / 改学院），
        //    两个前缀放一起是范畴错误。admin-service 侧只有 `anyRequest().authenticated()`
        //    + 不验签的 GatewayAuthFilter ⇒ 这一层放行等于**学院管理员可改全库账号角色**
        //    （`POST /api/admin/permissions/assign-role` 实测可达）。
        //    针对 `/api/permissions/**` 的那道闸救不了它：`PREFIX_PERMISSIONS` 是
        //    `/api/permissions`，而真实路径是 `/api/admin/permissions/...`，前缀对不上。
        //
        //    现在把 PREFIX_ADMIN 从这一支删掉 ⇒ 直落到本段末尾 `return false`。
        //    ⚠️ 不需要给学院管理端补白名单：`/college-admin` 路由组下的全部 9 个页面
        //    （CollegeAdmin*.vue）只打 `/api/college-admin/**`，零处 `/api/admin/`；
        //    `/admin/appeals`、`/admin/audit-logs` 属**管理员**的页面（AdminAppeals.vue /
        //    AdminSettings.vue），Router 在 `router/index.js` 里按 `/admin` 前缀只放 role 4。
        //    role 1/2/3 无需改动：它们走不到这里，下面 `path.startsWith(PREFIX_ADMIN)` 已拒。
        if (role == ROLE_COLLEGE_ADMIN) {
            if (path.startsWith(PREFIX_COLLEGE_ADMIN)) {
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

        // ==================== 自定义角色（users.role ≥ 6）：权限表驱动 ====================
        //
        // 2026-10-02（W3.3）。管理员可以在权限管理页新建角色并勾权限，但从前的代码里
        // 这些角色**根本表达不出来**（RolePermissionCache.roleToCode 是个 switch(1..5)、
        // default "unknown"），所以"新建角色"这个功能实际上是空转的。
        //
        // 现在自定义角色改由**库里 role_permissions 表**决定：URL 前缀 → 功能模块，
        // 再要求该模块的 PERM_{模块}_{VIEW|MANAGE}。权限表没覆盖到的面一律拒绝
        // （fail-closed，语义是"自定义角色的能力完全来自权限表"）。
        //
        // ⚠️ 内置角色 1–5 **不走这里**（上面几段已经把 4/5 拦下、1/2/3 继续往下走），
        //    所以这次改动对现有页面**不可能**产生 403 —— 这是刻意的：
        //    库里的权限是"功能模块"维度（学生/课程/预警/帮扶/系统），
        //    而 URL 前缀是"角色自己的家"维度（`/api/students/**` 是学生**本人**的自助面，
        //    不是"学生信息管理面"）。两个维度硬对齐只能靠编数据，见 RolePermissionCache 类注释。
        if (role != null && role > RolePermissionCache.BUILT_IN_ROLE_MAX) {
            return customRoleAllows(path, method, role, userId);
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

        // ==================== /api/courses/** 的角色门禁（W31，2026-10-08） ====================
        //
        // 与上面 `/api/roles` 那一段是**同一族**：`PREFIX_COURSE` 此前只出现在
        // {@link #customRoleAllows}（role ≥ 6）里，对内置角色 1/2/3 **一个检查都没有**
        // ⇒ 一路落到本方法末尾的 `return true`。而下游 course-service 的
        // `SecurityConfig` 是 `anyRequest().authenticated()`（不认角色），
        // ⇒ **任何已登录用户（含学生）都能调 course-service 的整套写接口**。
        // 2026-10-08 直连 8075 复刻"网关 StripPrefix=1 + 注入头"之后的请求实测：
        //   POST   /courses/add          X-Role 1 → 200，库里真的多一行；
        //   DELETE /courses/delete/{id}  X-Role 1 → 200，那一行真的没了；
        //   PUT    /courses/update/1     X-Role 1 → 200，真改到了 courses.id=1 的字段。
        //
        // 注意 `PREFIX_COURSE` 上面那句注释写的是「下面两条**只给自定义角色（role ≥ 6）用** ——
        // 内置角色走的是各自的角色前缀（/api/teachers、/api/students…），**不经这两条**」。
        // 这句话的**后半句是错的**，而且错得刚好把闸漏掉：
        //   "不经这两条常量" ≠ "不放行" —— 1/2/3 不匹配上面任何一条受限前缀，
        //   结果就是**穿过整个函数、被末尾 return true 放行**。这正是 W30 那个形状
        //   （「一条闸一直没装，是因为注释里写的理由本身是错的」）的第二次出现。
        //
        // 顺带核出闸的方向是**反的**：role 5（学院管理员）在 `role == ROLE_COLLEGE_ADMIN`
        // 那一段末尾 `return false`（line ~331），**本来就被拒**；被放行的恰恰是 1/2/3。
        //
        // 加闸不影响任何现有功能：`/api/courses/**` 在**全仓零调用方** —— 前端课程管理打的是
        // `/admin/courses`（admin-service）、教师/学生课表打的是 `/teachers/courses`
        // （teacher-service），两条都不经过这里（已用正对照 grep 过：
        // `teachers/courses` 在 PC 前端命中 11 处，裸 `/courses/` 命中 0 处）。
        // role ≥ 6 在更上面已按权限表 return，role 4 在函数开头已 return true
        // ⇒ 能走到这一句的只可能是 1/2/3，所以这里**无条件拒绝**就是 fail-closed。
        if (path.startsWith(PREFIX_COURSE)) {
            log.warn("拒绝非管理员访问课程管理端点: role={}, path={}", role, path);
            return false;
        }

        // ==================== /api/course-plans/** 的角色门禁（W48，2026-10-17） ====================
        // 与 `/api/courses/**` 是**同一族**、也是同一次接线的配套：排课面（`CoursePlanController`）
        // 此前无网关路由、无前端入口，但同时**不被任何前缀检查命中** ⇒ 若直接加路由而不补这一条，
        // role 1/2/3 会一路落到末尾 `return true`，把一个"建/改/删排课"的写面开给每个登录用户
        // （正是 W31 `/api/courses/**` 的复刻）。加闸后落到这里的一律是 1/2/3 ⇒ 无条件拒绝 = fail-closed。
        if (path.startsWith(PREFIX_COURSE_PLANS)) {
            log.warn("拒绝非管理员访问排课管理端点: role={}, path={}", role, path);
            return false;
        }

        // ==================== 这里原来有 `/api/auth/register/**` 的角色闸（2026-10-01 删） ====================
        //
        // 历史：`/api/auth/register/admin` 曾经是个真洞 —— 任何已登录角色（含学生）
        // POST 它并带 `role:4` 就能造出系统管理员。2026-09-30 用两层修（网关要求 role=4 +
        // 控制器按已验签 token 再判一次）；2026-10-01 发现它零调用方、且与 `/users/add` 重复，
        // 就**删掉端点本身** ⇒ 攻击面消失，比留着再加闸更彻底。管理员建号现在只剩
        // AdminUsers.vue → `POST /api/users/add` 一条活路径（上面 PREFIX_USERS 那段管着）。
        // ⚠️ 2026-10-09（W32）订正：这句"只剩一条活路径"**不成立**。全仓另有第二条 ——
        // 小程序 `admin/users.vue` → `POST /api/admin/users`（admin-service，下面
        // PREFIX_ADMIN 管着）。两条都是现役，且都要给 role 5 写 `college_admin_profile`。
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

        // ==================== 这里原来有一段「RBAC 细粒度校验」 ====================
        //
        // 2026-10-01 第十六轮查证：那个块**自上线起从未执行过** —— `hasRbacData()` 恒为 false
        // （RolePermissionCache 的拉取必 403，且失败后无重试、无刷新）。
        // 2026-10-02（W3.3）把它删掉了，**不是**因为"没生效所以删死代码"，
        // 而是因为它的判据本身是错的：`buildPermissionCode` 把 URL 前缀翻译成
        // `"student:read"` 这种权限码，而库里的权限码是 `PERM_STUDENT_VIEW` ——
        // 两套词表不是"拼写不同"，是**两个不同的维度**（见 RolePermissionCache 类注释）。
        // 把词表对齐只会得到假数据或 403 雪崩，所以整段拿掉，
        // 改为**只对自定义角色（id ≥ 6）**按权限表判定（上面那一段）。

        // 公共接口允许已认证用户访问
        return true;
    }

    /**
     * 自定义角色（{@code users.role ≥ 6}）的准入判定 —— 完全由 {@code role_permissions} 表决定。
     *
     * <p>顺序：
     * <ol>
     *   <li><b>自助账号路径</b>（改自己的密码 / 登出 / 刷新 token / 本人的资料与设置）
     *       与角色无关，任何已认证角色都可用 —— 与内置角色同一口径，归属校验照旧；</li>
     *   <li>RBAC 数据没加载 ⇒ **拒绝**（fail-closed，并打日志说明原因）；</li>
     *   <li>URL 前缀不在权限表覆盖的模块里 ⇒ **拒绝**（"权限表没这个面"和"你没这个权限"
     *       都是拒绝，但日志里分得开）；</li>
     *   <li>否则查该模块的 {@code PERM_{模块}_{VIEW|MANAGE}}。</li>
     * </ol>
     */
    private boolean customRoleAllows(String path, String method, int role, Long userId) {
        if (AUTH_CHANGE_PASSWORD.equals(path) || SELF_AUTH_PATTERN.matcher(path).matches()) {
            return true;
        }
        Matcher m = USER_SELF_PATTERN.matcher(path);
        if (m.matches() && userId != null) {
            try {
                return Long.parseLong(m.group(1)) == userId.longValue();
            } catch (NumberFormatException ignored) {
                return false;
            }
        }

        if (!rbacCache.hasRbacData()) {
            log.warn("自定义角色 role={} 被拒：RBAC 映射尚未加载（auth-service 未起？）path={}", role, path);
            return false;
        }
        String family = rbacFamilyOf(path);
        if (family == null) {
            log.warn("自定义角色 role={} 被拒：该 URL 前缀不在权限表覆盖的模块里 path={}", role, path);
            return false;
        }
        boolean allowed = rbacCache.allows(role, family, method);
        if (!allowed) {
            log.debug("自定义角色 RBAC 拒绝: role={}（{}）, path={}, 模块={}",
                    role, rbacCache.roleToCode(role), path, family);
        }
        return allowed;
    }

    /**
     * URL 前缀 → 库里 {@code permissions.code} 的**功能模块**名。
     *
     * <p><b>刻意只映射权限表真有的模块</b>，并且刻意**不映射**
     * {@code /api/students|teachers|counselors/**} —— 那三条是各角色**本人**的自助面
     * （我的成绩、我的课程、我的班级），不是"学生/教师/辅导员信息管理面"。
     * 把它们算成 {@code PERM_STUDENT_VIEW} 会得出「学生角色持有『管理学生信息』权限」
     * 这种自相矛盾的数据（详见 RolePermissionCache 类注释）。
     *
     * <p>返回 {@code null} = "权限表管不到这个面" ⇒ 自定义角色拿不到（fail-closed）。
     */
    static String rbacFamilyOf(String path) {
        if (path.startsWith(PREFIX_ADMIN)
                || path.startsWith(PREFIX_ROLES)
                || path.startsWith(PREFIX_PERMISSIONS)
                || path.startsWith(PREFIX_ROLE_PERMISSIONS)) {
            return "SYSTEM";
        }
        if (path.startsWith(PREFIX_COURSE)) {
            return "COURSE";
        }
        // 排课是课程管理的一部分，归同一个功能模块（W48）。
        if (path.startsWith(PREFIX_COURSE_PLANS)) {
            return "COURSE";
        }
        if (path.startsWith(PREFIX_WARNING)) {
            return "WARNING";
        }
        return null;
    }

    static boolean isWhiteListed(String path) {
        return WHITE_LIST.stream().anyMatch(pattern -> globMatches(pattern, path));
    }

    /**
     * 白名单 glob 匹配（W49，2026-10-19 修正）。
     *
     * <p><b>修之前是什么样</b>：{@code path.matches(pattern.replace("**", ".*"))}。
     * {@link String#matches} 是**全串匹配**，于是清单里的 {@code "/ws/**"} 变成正则
     * {@code /ws/.*} —— **匹配不上客户端实际请求的 {@code /ws}**（没有尾段）。
     * 浏览器连 PC 端实时推送时请求的正是 {@code ws://host/ws}，于是在网关鉴权这一跳
     * 就被 401 挡下，**根本到不了下游 message-service**。
     * 实测（W48）：无 token 打 {@code /ws} → 401，打 {@code /ws/}、{@code /ws/info} → 101。
     * 这就是整条 STOMP 推送链从未成立的承重原因。
     *
     * <p>另外 {@code replace} 只是字符串替换：白名单里任何 {@code .} 都会变成正则通配符。
     * 现在清单里没有这种条目，但这是个只会等别人踩的坑，一并按字面量转义。
     *
     * <p>本方法的语义（只覆盖白名单真正用到的两种写法，够用且可测）：
     * <ul>
     *   <li>{@code *} → 单个路径段内任意字符（不含 {@code /}）；</li>
     *   <li>{@code **} → 任意字符；出现在**结尾**的 {@code /**} 允许**零段** ——
     *       即 {@code "/ws/**"} 同时匹配 {@code /ws} 与 {@code /ws/anything}
     *       （这正是修 {@code /ws} 的关键）；</li>
     *   <li>其余字符一律按字面量转义。</li>
     * </ul>
     *
     * <p>⚠️ 别把这条改成"再补一条精确的 {@code "/ws"}"：那样只治好这一个地址，
     * 下一个 `/**` 白名单还会踩同一坑。修的是**尺子**，不是某一格。
     */
    static boolean globMatches(String pattern, String path) {
        StringBuilder re = new StringBuilder("^");
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '/' && pattern.startsWith("/**", i) && i + 3 == pattern.length()) {
                // 结尾的 `/**`：零段或多段
                re.append("(?:/.*)?");
                i += 2;
            } else if (c == '*') {
                if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '*') {
                    re.append(".*");
                    i++;
                } else {
                    re.append("[^/]*");
                }
            } else {
                // 正则元字符一律转义（`/` 本身不需要，但它也不在表里）
                if ("\\.[]{}()+-^$|?".indexOf(c) >= 0) {
                    re.append('\\');
                }
                re.append(c);
            }
        }
        re.append('$');
        return path.matches(re.toString());
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
