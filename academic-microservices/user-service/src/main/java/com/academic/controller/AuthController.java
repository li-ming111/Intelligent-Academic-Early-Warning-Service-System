package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.service.CaptchaService;
import com.academic.service.UserService;
import com.academic.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证控制器
 * <p>
 * 负责处理用户登录、注册和获取用户信息等认证相关的请求
 * </p>
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    // ==================== 注册角色 ====================
    //
    // 角色**由端点写死**，绝不从请求体读。
    // 修复前四个 register 端点都调同一个 userService.register(request)，而那个方法
    // 从 body 里取 role —— 于是匿名者 POST /api/auth/register/student 带 role:4
    // 就能造出系统管理员（2026-09-30 实测：建出 users.id=80、role=4）。
    // 现在每个端点把"自己能创建的角色"作为参数传下去，请求体里的 role 一律作废。
    private static final int ROLE_STUDENT = 1;
    private static final int ROLE_TEACHER = 2;
    private static final int ROLE_COUNSELOR = 3;

    private final UserService userService;

    /** 登录验证码的签发与校验（W1.12，2026-10-02）。 */
    private final CaptchaService captchaService;

    /** 直连场景（无网关注入的 X-User-Id 头）下，从 Bearer token 里解出请求者 userId。 */
    private final JwtUtil jwtUtil;

    /**
     * 构造函数
     * @param userService 用户服务
     */
    public AuthController(UserService userService, CaptchaService captchaService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.captchaService = captchaService;
        this.jwtUtil = jwtUtil;
    }

    /**
     * 签发一个登录验证码（W1.12）。
     *
     * <p>改之前验证码是**纯前端**的（`Login.vue` 用 `Math.random()` 生成、提交前自己比对），
     * 直接 POST `/auth/login` 就绕过去了。现在码由服务端签发并校验。
     *
     * <p>⚠️ 这个端点必须**匿名可达** —— 它在登录之前就要被调用。两处白名单都要加：
     * 网关 `AuthenticationFilter.WHITE_LIST` + 本服务 `SecurityConfig.permitAll`。
     * 只加一处的话，网关放行了但下游没放行 ⇒ 撞 `.anyRequest().authenticated()` ⇒ 403
     * （这个坑 2026-09-30 在 `/api/auth/majors` 上实测过）。
     */
    @GetMapping("/captcha")
    public ApiResponse<Map<String, Object>> captcha() {
        return ApiResponse.success(captchaService.issue());
    }

    /**
     * 用户登录
     * <p>
     * 处理用户登录请求，验证用户名和密码，返回登录令牌和用户信息
     * </p>
     * @param request 登录请求，包含用户名和密码
     * @return 登录响应，包含令牌和用户信息
     */
            @PostMapping("/login")
    public ApiResponse<Object> login(@RequestBody Map<String, Object> request,
                                     HttpServletRequest httpRequest) {
        try {
            // 验证码先于账号口令校验（W1.12）。
            // 放在**控制器**这一层而不是 userService 里，是因为验证码是 HTTP 层的
            // 反自动化手段，而 `userService.login(request)` 那个单参重载是给单元测试用的
            // 非 HTTP 路径（全仓非测试调用方只有下面这一处）。
            // 先验码再验口令，也让"爆破口令"必须每一步都重新签发验证码。
            if (!captchaService.verifyAndConsume(str(request.get("captchaId")), str(request.get("captcha")))) {
                log.warn("Login rejected: bad or expired captcha");
                return ApiResponse.error("验证码错误或已过期，请点击验证码刷新后重试");
            }
            // ⚠️ 只打用户名，**不要**打整个 request：那个 map 里带着用户口令，
            // 现在还多了一个验证码明文（也就是这一层的答案）。原来这里打的是整个 map，
            // 等于把口令写进日志文件。
            log.info("User login request: username={}", request.get("username"));
            // 直接返回登录响应，不解析JSON。
            // 带 httpRequest 是为了把这次登录记进 `login_logs`（客户端 IP / UA 只有这里拿得到）——
            // 此前那张表**全仓无写入方**，学生端「登录历史」与管理员端「登录日志」永远空。
            Object response = userService.login(request, httpRequest);
            log.info("Login successful");
            return ApiResponse.success(response);
        } catch (Exception e) {
            log.error("Login failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    // ==================== 这里原来有裸的 `POST /register`（2026-10-01 删） ====================
    //
    // 它和紧跟其后的 `POST /register/student` 是**同一个调用** —— 两处的实现体一字不差，
    // 都是 `userService.register(request, ROLE_STUDENT)`，注释也都写明"通用入口按最低权限
    // 处理（注册学生）"。也就是说它**不是**提权面（请求体里的 `role` 本来就不生效），
    // 只是一个和后缀端点完全重复的入口。
    //
    // 删它的依据是"零调用方"：前端 `api/index.js` 只发 `/auth/register/{student,teacher,counselor}`，
    // 小程序 `services/api.js` 同三条，裸路径全仓无一处（逐文件 grep 过）。
    //
    // ⚠️ 连带改动：网关 `AuthenticationFilter` 里那段 `path.startsWith("/api/auth/register")`
    //    的闸**原本只拦得住裸路径**（它自己的注释写明：白名单放过三条自助路径后，
    //    "能走到这一行的只剩裸的 /api/auth/register"）。端点没了，那段闸就成了死代码，
    //    已一并删除 —— 所以这两处必须一起改，只删一处会留下一个永远不成立的判断。

    /**
     * 学生注册
     * <p>
     * 处理学生注册请求，验证并保存学生信息
     * </p>
     * @param request 注册请求，包含学生信息
     * @return 注册结果
     */
    @PostMapping("/register/student")
    public ApiResponse<String> registerStudent(@RequestBody Object request) {
        try {
            log.info("Student register request");
            String result = userService.register(request, ROLE_STUDENT);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Student register failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 教师注册
     * <p>
     * 处理教师注册请求，验证并保存教师信息
     * </p>
     * @param request 注册请求，包含教师信息
     * @return 注册结果
     */
    @PostMapping("/register/teacher")
    public ApiResponse<String> registerTeacher(@RequestBody Object request) {
        try {
            log.info("Teacher register request");
            String result = userService.register(request, ROLE_TEACHER);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Teacher register failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 辅导员注册
     * <p>
     * 处理辅导员注册请求，验证并保存辅导员信息
     * </p>
     * @param request 注册请求，包含辅导员信息
     * @return 注册结果
     */
    @PostMapping("/register/counselor")
    public ApiResponse<String> registerCounselor(@RequestBody Object request) {
        try {
            log.info("Counselor register request");
            String result = userService.register(request, ROLE_COUNSELOR);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Counselor register failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    // ==================== 管理员注册：已删（2026-10-01） ====================
    //
    // `POST /auth/register/admin` 连同它的角色闸 `currentUserIsAdmin()` 一起删掉了。理由：
    //   - 唯一的调用方是前端 `AdminRegister.vue`，而那个页面**没有任何入口**
    //     （路由在、只能手敲 URL），已一并删除 ⇒ 本端点变成零调用方；
    //   - 它和 `/users/add`（AdminUsers.vue 在用的活路径）、admin-service 的
    //     `/admin/register` 构成"同一件事三种做法"，三处都删到只剩一条；
    //   - 删掉端点 = "匿名直连 8082 提权"这个洞**连同攻击面一起消失**，
    //     比留着端点再加闸更彻底。
    //
    // 于是**管理员建号全仓只剩一条活路径**: AdminUsers.vue → `POST /api/users/add`
    // → user-service `UserController.addUser`（那里有 `currentUserIsAdmin()` 闸）。
    // ⚠️ 2026-10-09（W32）订正：上面这句**不成立** —— 还有第二条活路径
    // 小程序 `admin/users.vue` → `POST /api/admin/users`（admin-service
    // `AdminController.createUser`，闸是网关 PREFIX_ADMIN + 控制器内角色判断）。
    //
    // ⚠️ 历史留档：这里原来有个真实漏洞 —— 2026-09-30 实测，改前 `/auth/register/**`
    //    是 permitAll，**不带任何 token 直连 :8082/api/auth/register/admin 就能走到
    //    业务逻辑**，而该端点建的是 role=4 系统管理员。当时是"收紧 SecurityConfig
    //    白名单 + 端点内按已验签 token 的角色再判一次"两层修的；现在端点本身没了。
    //    同理每个 register 端点的角色**由端点写死**、绝不从请求体读（见上面的常量注释）。


    // ⚠️ 2026-10-10（W34）这里原有 `GET /auth/info/{userId}` —— **已删，不要再加回来**。
    //
    // 它是一条**无归属校验的跨用户读**：网关 `hasPermission` 对 `/api/auth/**` 没有任何前缀
    // 检查（`/api/auth` 不匹配任何受限前缀，内置角色 1/2/3 一路落到末尾 `return true`），
    // user-service `SecurityConfig` 是 `anyRequest().authenticated()`，而这个方法体**不判归属**
    // ⇒ **任何已登录用户（含学生）都能读任意 userId 的 name/username/role/status，并据此枚举 id**
    // （2026-10-08 实测：学生 27 打 `/auth/info/18` 得 HTTP 200 + name=刘教授）。
    //
    // 它与 `/users/info/{userId}` 是**孪生**，但那条**两层都判归属**（网关 `USER_SELF_PATTERN`
    // 要求 id==本人、控制器 `isAdminOrSelf`）且回**更多**字段 ⇒ 本端点是它**无闸且更弱的重复**。
    // 且**全仓零调用方**（PC + 小程序封装与页面字面路径均 0 —— 各端 `getUserInfo` 打的是
    // `/users/info/{userId}`）⇒ 按本仓「零调用方 = 删」处理，一并删掉：
    //   · `UserService.getUserInfo` / `UserServiceImpl` / `UserServiceFallback` 的实现
    //   · `UserServiceImplTest.testGetUserInfo` / `testGetUserInfo_notFound`
    //   · `sentinel-rules.json` 里那条 `"/auth/info/{userId}"` 限流规则（为一条
    //     @GetMapping 配的规则**从未生效** —— Sentinel 资源名默认按 @SentinelResource 或
    //     URL 注册，纯 @GetMapping 不会自动成为资源）
    //   · `AuthControllerTest.testGetUserInfo_Success`
    // 被拒形状：修后已认证打 `/auth/info/*` 是 **404（已认证但无映射）**，
    // 匿名仍是 **403 空体**（Spring Security 拦在映射之前）—— 两者不是同一种，别只断言"非 200"。

    /**
     * 用户登出
     * <p>
     * 处理用户登出请求
     * </p>
     * @param userId 用户ID
     * @param role 用户角色
     * @return 登出结果
     */
    @PostMapping("/logout")
    public ApiResponse<Object> logout(@RequestParam Long userId, @RequestParam String role) {
        try {
            log.info("User logout request: userId={}, role={}", userId, role);
            Object response = userService.logout(userId, role);
            return ApiResponse.success(response);
        } catch (Exception e) {
            log.error("Logout failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 刷新令牌
     * <p>
     * 使用刷新令牌获取新的访问令牌
     * </p>
     * @param request 包含刷新令牌的请求
     * @return 新的访问令牌和刷新令牌
     */
    @PostMapping("/refresh-token")
    public ApiResponse<Object> refreshToken(@RequestBody Map<String, Object> request) {
        try {
            log.info("Refresh token request");
            String refreshToken = request.get("refreshToken") != null ? request.get("refreshToken").toString() : null;
            Object response = userService.refreshToken(refreshToken);
            return ApiResponse.success(response);
        } catch (Exception e) {
            log.error("Refresh token failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 修改密码（**仅限本人**）
     *
     * <p>权威身份取网关注入的 {@code X-User-Id}，请求体里的 {@code userId} 只作为
     * 无网关场景（本地脚本直连本服务）的兜底；两者同时存在且不一致时直接拒绝。
     *
     * <p>原来这里只信请求体里的 {@code userId}，而网关对 {@code /api/auth/**} 是
     * "已认证即放行"（该路径既不匹配任何受限前缀，也拿不到 RBAC 权限编码），
     * 于是**任意登录用户都能改别人的密码**：{@code UserServiceImpl.changePassword}
     * 只校验"目标账号的旧密码"，而本演示库全部账号口令统一为 123456。
     */
    @PostMapping("/change-password")
    public ApiResponse<String> changePassword(@RequestBody Map<String, Object> request,
                                              HttpServletRequest httpRequest) {
        try {
            Long bodyUserId = request.get("userId") != null
                    ? Long.parseLong(request.get("userId").toString()) : null;
            String oldPassword = (String) request.get("oldPassword");
            String newPassword = (String) request.get("newPassword");
            if (oldPassword == null || newPassword == null) {
                return ApiResponse.error("参数不完整");
            }

            Long callerId = resolveCallerId(httpRequest);
            if (callerId == null) {
                // 拿不到"你是谁"就拒绝。**不回落到请求体的 `userId`** ——
                // 那是调用方自己填的，用它就等于没有闸。
                return ApiResponse.unauthorized("无法确定请求者身份");
            }
            if (bodyUserId != null && !bodyUserId.equals(callerId)) {
                log.warn("拒绝越权改密: 调用者={}, 请求体目标={}", callerId, bodyUserId);
                return ApiResponse.forbidden("只能修改本人的密码");
            }
            Long userId = callerId;

            userService.changePassword(userId, oldPassword, newPassword);
            return ApiResponse.success("密码修改成功");
        } catch (Exception e) {
            log.error("Change password failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 请求体里取字符串（前端可能传 null / 数字 / 带空格）。
     *
     * <p>验证码那两格尤其不能只做 `(String) get(...)` —— 前端把 `captchaId` 传成数字
     * 或带上首尾空格都会 {@code ClassCastException}，表现成"登录 500"。
     */
    private static String str(Object v) {
        return v == null ? null : String.valueOf(v).trim();
    }

    /**
     * 取请求者 userId —— <b>身份只认两处，都不来自请求体</b>。
     *
     * <ol>
     *   <li>{@code X-User-Id} 头：网关验签后注入的，是正常入口；</li>
     *   <li>没有该头时，从 {@code Authorization: Bearer <token>} 自己解出
     *       {@link JwtUtil#getUserIdFromToken} —— 直连场景。token 由本服务的密钥签发，
     *       改了它验签就过（{@code JwtAuthenticationFilter} 也是这么做的）。</li>
     * </ol>
     *
     * <p><b>2026-10-10（W37）</b>：此前这里没有第 ② 步 —— 缺头时直接 <b>回落到请求体的
     * {@code userId}</b>（注释还把它写成"给无网关场景的本地脚本用"）。后果：绕过网关直连
     * {@code :8082} 时，改密接口不判归属，而演示库口令统一 123456 ⇒
     * <b>任意已认证用户可改任意账号的口令</b>。经网关有闸，所以这是"网关是唯一防线"的又一例
     * （本仓红线：网关不是网络边界）。现在**身份永远取自 token/头，请求体里的 userId
     * 只当"被检查对象"**。
     */
    private Long resolveCallerId(HttpServletRequest request) {
        Long fromHeader = getRequestUserId(request);
        if (fromHeader != null) {
            return fromHeader;
        }
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                Long fromToken = jwtUtil.getUserIdFromToken(authHeader.substring(7));
                if (fromToken != null) {
                    log.debug("change-password 无 X-User-Id 头，改用 token 内 userId={}", fromToken);
                    return fromToken;
                }
            } catch (Exception e) {
                log.warn("change-password 解析 token 取 userId 失败: {}", e.getMessage());
            }
        }
        return null;
    }

    /** 从网关注入的头取请求者 userId，与消息服务里的同名方法保持同一解析口径。 */
    private Long getRequestUserId(HttpServletRequest request) {
        String header = request.getHeader("X-User-Id");
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(header.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
