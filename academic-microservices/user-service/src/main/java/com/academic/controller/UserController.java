package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.common.entity.User;
import com.academic.common.entity.UserSettings;
import com.academic.common.util.UpdateBodyGuard;
import com.academic.dto.UserDTO;
import com.academic.entity.LoginLog;
import com.academic.mapper.LoginLogMapper;
import com.academic.mapper.UserSettingsMapper;
import com.academic.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户控制器
 */
@Slf4j
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    /** 直接注入 Mapper 而不走 UserService：UserService 有两个实现
     *  （UserServiceImpl @Primary + UserServiceFallback），往接口上加方法
     *  必须同时改 Fallback 才能编译；而这里只是对单表的读写，
     *  与 CounselorController 直接注入 warningMapper 的既有写法一致。 */
    private final UserSettingsMapper userSettingsMapper;
    /** 同上：登录历史的读取只碰 {@code login_logs} 一张表。 */
    private final LoginLogMapper loginLogMapper;

    public UserController(UserService userService, UserSettingsMapper userSettingsMapper,
                          LoginLogMapper loginLogMapper) {
        this.userService = userService;
        this.userSettingsMapper = userSettingsMapper;
        this.loginLogMapper = loginLogMapper;
    }

    // ==================== 调用者身份 ====================
    //
    // 2026-09-30：本控制器原先**一个权限判断都没有**。网关对 `/api/users/**` 那句
    // "非管理员不可访问" 只写在注释里（`AuthenticationFilter` 的枚举注释），代码上
    // role 1/2/3 走完前缀检查就落到函数末尾 `return true` —— 于是**任何已登录用户**
    // 都能 `POST /users/add`（body 自带 role ⇒ 建出管理员）、`PUT /users/update/{别人的id}`、
    // `GET /users/by-username/{n}`（回原始实体含 BCrypt 哈希）。
    //
    // ⚠️ 网关那道闸**不是网络边界**：user-service 的 8082 在本机可直连，直连时
    // 网关的 `X-Role`/`X-User-Id` 请求头可以随便伪造。所以这里**只认
    // `JwtAuthenticationFilter` 从已验签 JWT 声明写进 SecurityContext 的东西**：
    // 用户名（principal name）与 `ROLE_<role>` 权限。

    /** 当前请求者是否管理员。 */
    private boolean currentUserIsAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_4".equals(a.getAuthority()));
    }

    /**
     * 当前请求者的 {@code users.id}；未认证或用户名查无此人返回 null。
     *
     * <p>**不读 `X-User-Id` 请求头** —— 直连本服务时它可以随便伪造。
     */
    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return null;
        }
        User me = userService.getByUsername(auth.getName());
        return me != null ? me.getId() : null;
    }

    /** 管理员，或 {@code targetUserId} 就是本人。 */
    private boolean isAdminOrSelf(Long targetUserId) {
        if (currentUserIsAdmin()) {
            return true;
        }
        Long me = currentUserId();
        boolean ok = me != null && me.equals(targetUserId);
        if (!ok) {
            log.warn("拒绝非本人/非管理员的账号操作: caller={}, target={}", me, targetUserId);
        }
        return ok;
    }

    /**
     * 获取用户列表
     */
    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> getUsers(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Integer role) {
        if (!currentUserIsAdmin()) {
            return ApiResponse.forbidden("需要管理员权限");
        }
        try {
            log.info("Get users request: page={}, size={}, role={}", page, size, role);
            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            if (role != null) {
                queryWrapper.eq("role", role);
            }
            queryWrapper.orderByDesc("created_at");
            
            Page<User> userPage = new Page<>(page, size);
            userPage = userService.page(userPage, queryWrapper);
            
            List<UserDTO> userDTOs = userPage.getRecords().stream()
                    .map(user -> {
                        UserDTO dto = new UserDTO();
                        dto.setId(user.getId());
                        dto.setUsername(user.getUsername());
                        dto.setName(user.getName());
                        dto.setEmail(user.getEmail());
                        dto.setPhone(user.getPhone());
                        dto.setRole(user.getRole());
                        dto.setStatus(user.getStatus());
                        dto.setCreatedAt(user.getCreatedAt());
                        dto.setUpdatedAt(user.getUpdatedAt());
                        return dto;
                    })
                    .collect(Collectors.toList());
            
            Map<String, Object> responseData = Map.of(
                    "data", userDTOs,
                    "total", userPage.getTotal(),
                    "page", page,
                    "size", size,
                    "pages", userPage.getPages()
            );
            return ApiResponse.success(responseData);
        } catch (Exception e) {
            log.error("Get users failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取用户详情
     */
    @GetMapping("/info/{userId}")
    public ApiResponse<UserDTO> getUserInfo(@PathVariable Long userId) {
        if (!isAdminOrSelf(userId)) {
            return ApiResponse.forbidden("只能查看本人的资料");
        }
        try {
            log.info("Get user info request: {}", userId);
            User user = userService.getById(userId);
            if (user == null) {
                return ApiResponse.error(404, "用户不存在");
            }
            
            UserDTO dto = new UserDTO();
            dto.setId(user.getId());
            dto.setUsername(user.getUsername());
            dto.setName(user.getName());
            dto.setEmail(user.getEmail());
            dto.setPhone(user.getPhone());
            dto.setRole(user.getRole());
            dto.setStatus(user.getStatus());
            dto.setCreatedAt(user.getCreatedAt());
            dto.setUpdatedAt(user.getUpdatedAt());
            
            return ApiResponse.success(dto);
        } catch (Exception e) {
            log.error("Get user info failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 创建用户
     */
    @PostMapping("/add")
    public ApiResponse<Long> addUser(@RequestBody User user) {
        if (!currentUserIsAdmin()) {
            return ApiResponse.forbidden("需要管理员权限");
        }
        try {
            log.info("Add user request: {}", user.getUsername());
            boolean saved = userService.save(user);
            if (saved) {
                return ApiResponse.success(user.getId());
            } else {
                return ApiResponse.error("创建用户失败");
            }
        } catch (DuplicateKeyException e) {
            // 用户名唯一索引冲突是**用户输入问题**，不是系统故障。
            // 不翻译的话前端会收到 MyBatis 的整段异常文本（含 SQL 语句和建表索引名），
            // 弹到页面上就是一大坨 SQL —— 用户只看得出"失败了"，看不出为什么。
            log.warn("Add user duplicate: {}", user.getUsername());
            return ApiResponse.error("用户名已存在");
        } catch (Exception e) {
            log.error("Add user failed: {}", e.getMessage());
            if (String.valueOf(e.getMessage()).contains("Duplicate entry")) {
                return ApiResponse.error("用户名已存在");
            }
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 更新用户
     */
    @PutMapping("/update/{userId}")
    public ApiResponse<String> updateUser(@PathVariable Long userId, @RequestBody User user) {
        if (!isAdminOrSelf(userId)) {
            return ApiResponse.forbidden("只能修改本人的资料");
        }
        try {
            log.info("Update user request: {}", userId);
            user.setId(userId);
            if (!currentUserIsAdmin()) {
                // 非管理员改**自己的**资料时，以下字段一律不许动。置 null 即被 MyBatis-Plus 的
                // NOT_NULL 策略跳过、不会进 SET 子句（`UserServiceImpl.updateById` 对 null 密码
                // 也不做编码），所以这是"改不动"而不是"改成 null"：
                //   role / status —— 否则 `PUT /users/update/{自己的id}` 带 {"role":4}
                //                    就能**自助提权**，而网关拦不住（角色在请求体里，网关不看 body）；
                //   password      —— 改口令请走 /auth/change-password（那里要求旧密码）。
                //                    这条路径不验旧密码，放行等于把"改密码要先给旧密码"整层绕掉。
                user.setRole(null).setStatus(null).setPassword(null);
            }
            // W8：请求体全 null（发 `{}` / 字段名写错）⇒ MyBatis-Plus 生成无 SET 子句的 UPDATE
            // ⇒ SQL 语法错。这是客户端的错，回 400。
            // ⚠️ 必须放在上面那段"非管理员禁改字段置 null"**之后**：非管理员只传
            //    {"role":4} 时，role 会被上面置成 null，于是实体最终依然是全 null ——
            //    按请求体判会放行，按即将落库的实体判才是对的。
            if (!UpdateBodyGuard.hasUpdatableField(user)) {
                return ApiResponse.badRequest("请求体里没有任何可更新的字段");
            }
            boolean updated = userService.updateById(user);
            if (updated) {
                return ApiResponse.success("用户已更新");
            } else {
                // W8：0 行 = 该用户不存在（原来回 code 500）。
                return ApiResponse.notFound("用户不存在");
            }
        } catch (Exception e) {
            log.error("Update user failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/delete/{userId}")
    public ApiResponse<String> deleteUser(@PathVariable Long userId) {
        if (!currentUserIsAdmin()) {
            return ApiResponse.forbidden("需要管理员权限");
        }
        try {
            log.info("Delete user request: {}", userId);
            boolean deleted = userService.removeById(userId);
            if (deleted) {
                return ApiResponse.success("用户已删除");
            } else {
                return ApiResponse.error("删除用户失败");
            }
        } catch (Exception e) {
            log.error("Delete user failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 根据用户名获取用户信息
     */
    @GetMapping("/by-username/{username}")
    public ApiResponse<User> getUserByUsername(@PathVariable String username) {
        if (!currentUserIsAdmin()) {
            return ApiResponse.forbidden("需要管理员权限");
        }
        try {
            log.info("Get user by username request: {}", username);
            User user = userService.getByUsername(username);
            if (user == null) {
                return ApiResponse.error(404, "用户不存在");
            }
            // 不回传口令哈希 —— 这里回的是**整个 User 实体**，password 字段就是库里那串
            // BCrypt 哈希。与 admin-service 的 `AdminController.getUserById` 同一条规矩：
            // 一个读接口把凭据材料发给前端，不管前端用不用都是不对的。
            // 校验口令请走登录接口，不需要读哈希。
            user.setPassword(null);
            return ApiResponse.success(user);
        } catch (Exception e) {
            log.error("Get user by username failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取用户总数
     */
    @GetMapping("/count")
    public ApiResponse<Long> getUserCount(@RequestParam(required = false) Integer role) {
        if (!currentUserIsAdmin()) {
            return ApiResponse.forbidden("需要管理员权限");
        }
        try {
            log.info("Get user count request: role={}", role);
            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            if (role != null) {
                queryWrapper.eq("role", role);
            }
            long count = userService.count(queryWrapper);
            return ApiResponse.success(count);
        } catch (Exception e) {
            log.error("Get user count failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    // ==================== 账号级自助设置（通知偏好 + 办公信息） ====================

    /**
     * 读取自助设置。
     *
     * <p><b>没有记录时返回默认值、绝不返回 404</b>：这是"用户从没改过设置"的正常状态，
     * 不是错误。返回 404 会让设置页把三个下拉/开关渲染成空，看起来像数据丢了。
     * 默认值在这里生成，**不落库** —— 首次 PUT 才建行，这样"无记录"这条分支
     * 在演示环境里是真实被走到的。
     */
    @GetMapping("/{userId}/settings")
    public ApiResponse<UserSettings> getSettings(@PathVariable Long userId) {
        if (!isAdminOrSelf(userId)) {
            return ApiResponse.forbidden("只能查看本人的设置");
        }
        try {
            log.info("Get user settings: userId={}", userId);
            UserSettings settings = userSettingsMapper.selectOne(
                    new QueryWrapper<UserSettings>().eq("user_id", userId));
            if (settings == null) {
                settings = defaultSettings(userId);
            }
            return ApiResponse.success(settings);
        } catch (Exception e) {
            log.error("Get user settings failed: userId={}, err={}", userId, e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 保存自助设置。
     *
     * <p><b>支持部分更新</b>：请求体里没出现的字段保持不动。设置页把"通知偏好"和
     * "办公信息"分成两个卡片、两次提交，若按整体覆盖语义，保存办公信息会把通知偏好
     * 全部重置。实现上依赖 MyBatis-Plus {@code updateById} 的
     * {@code FieldStrategy.NOT_NULL}（null 字段不进 SET 子句）—— 所以实体里的布尔字段
     * 必须是包装类型 {@code Boolean}，原始 {@code boolean} 分不出"没传"和"传了 false"。
     *
     * <p>三个 {@code office_*} 文本字段**有意保留空串**、不归一成 NULL：前端把输入框清空
     * 时会提交 {@code ""}，若在这里转成 NULL，NOT_NULL 策略会跳过它，"清空办公地点"就
     * 永远清不掉。代价是库里"未填写"有 NULL（从未保存过）和 ''（保存过又清空）两种写法，
     * 两者在页面上显示完全一样。
     */
    @PutMapping("/{userId}/settings")
    public ApiResponse<UserSettings> updateSettings(@PathVariable Long userId,
                                                   @RequestBody UserSettings body) {
        if (!isAdminOrSelf(userId)) {
            return ApiResponse.forbidden("只能修改本人的设置");
        }
        try {
            log.info("Update user settings: userId={}", userId);
            if (userService.getById(userId) == null) {
                return ApiResponse.error(404, "用户不存在");
            }
            body.setId(null).setUserId(userId);

            UserSettings existing = userSettingsMapper.selectOne(
                    new QueryWrapper<UserSettings>().eq("user_id", userId));
            if (existing == null) {
                // 新行：null 字段被策略跳过，由建表时的 DEFAULT 兜底。
                userSettingsMapper.insert(body);
            } else {
                body.setId(existing.getId());
                userSettingsMapper.updateById(body);
            }
            return ApiResponse.success(userSettingsMapper.selectById(
                    existing == null ? body.getId() : existing.getId()));
        } catch (Exception e) {
            log.error("Update user settings failed: userId={}, err={}", userId, e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 本人的登录历史（学生端「设置 → 安全日志 → 登录历史」）。
     *
     * <p>2026-10-02 新增。此前 {@code login_logs} 全仓没有写入方、也没有面向学生的读取口：
     * 页面把 {@code logs} 声明成空数组后**从不赋值**，旁边还写死一句「刚刚 · Windows」。
     * 现在写入方在 {@link com.academic.service.LoginLogService}（登录成功/失败各记一条），
     * 读取口就是这里。
     *
     * <p>归属校验用 {@code isAdminOrSelf}：管理员可查任意用户（管理端「用户详情 → 登录日志」
     * 也走这个口），普通用户只能查自己。**不读 {@code X-User-Id} 请求头** —— 直连本服务时
     * 它可以随便伪造，只认 {@code JwtAuthenticationFilter} 写进 SecurityContext 的身份。
     *
     * <p>取最近 20 条即可：这个页面的用途是"我最近从哪儿登录过"，不是审计报表
     * （全量审计在管理端的 {@code /admin/login-logs/{userId}}）。
     */
    @GetMapping("/{userId}/login-logs")
    public ApiResponse<List<Map<String, Object>>> getLoginLogs(@PathVariable Long userId) {
        if (!isAdminOrSelf(userId)) {
            return ApiResponse.forbidden("只能查看本人的登录历史");
        }
        try {
            log.info("Get login logs: userId={}", userId);
            List<LoginLog> logs = loginLogMapper.selectList(
                    new QueryWrapper<LoginLog>().eq("user_id", userId)
                            .orderByDesc("login_time").last("LIMIT 20"));
            List<Map<String, Object>> result = new ArrayList<>();
            for (LoginLog entry : logs) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", entry.getId());
                item.put("loginTime", entry.getLoginTime());
                item.put("ipAddress", entry.getIpAddress());
                item.put("device", entry.getDevice());
                item.put("location", entry.getLocation());
                item.put("status", entry.getStatus());
                result.add(item);
            }
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Get login logs failed: userId={}, err={}", userId, e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /** 与 user_settings 建表 DEFAULT 一致，改表时两处要一起改。 */
    private static UserSettings defaultSettings(Long userId) {
        return new UserSettings()
                .setUserId(userId)
                .setWarningNotification(true)
                .setSystemMessage(true)
                .setClassNotification(true)
                .setStudentFeedback(true)
                .setEmailReminder(false);
    }
}
