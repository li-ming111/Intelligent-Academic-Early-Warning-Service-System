package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.common.entity.User;
import com.academic.common.entity.UserSettings;
import com.academic.dto.UserDTO;
import com.academic.mapper.UserSettingsMapper;
import com.academic.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.*;

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

    public UserController(UserService userService, UserSettingsMapper userSettingsMapper) {
        this.userService = userService;
        this.userSettingsMapper = userSettingsMapper;
    }

    /**
     * 获取用户列表
     */
    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> getUsers(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Integer role) {
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
        try {
            log.info("Update user request: {}", userId);
            user.setId(userId);
            boolean updated = userService.updateById(user);
            if (updated) {
                return ApiResponse.success("用户已更新");
            } else {
                return ApiResponse.error("更新用户失败");
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
        try {
            log.info("Get user by username request: {}", username);
            User user = userService.getByUsername(username);
            if (user == null) {
                return ApiResponse.error(404, "用户不存在");
            }
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
