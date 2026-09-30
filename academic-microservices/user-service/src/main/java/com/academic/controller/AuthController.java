package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.service.UserService;
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
    private final UserService userService;

    /**
     * 构造函数
     * @param userService 用户服务
     */
    public AuthController(UserService userService) {
        this.userService = userService;
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
    public ApiResponse<Object> login(@RequestBody Map<String, Object> request) {
        try {
            log.info("User login request: {}", request);
            // 直接返回登录响应，不解析JSON
            Object response = userService.login(request);
            log.info("Login successful");
            return ApiResponse.success(response);
        } catch (Exception e) {
            log.error("Login failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 用户注册
     * <p>
     * 处理用户注册请求，验证并保存用户信息
     * </p>
     * @param request 注册请求，包含用户信息
     * @return 注册结果
     */
    @PostMapping("/register")
    public ApiResponse<String> register(@RequestBody Object request) {
        try {
            log.info("User register request");
            String result = userService.register(request);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Register failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

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
            String result = userService.register(request);
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
            String result = userService.register(request);
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
            String result = userService.register(request);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Counselor register failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 管理员注册
     * <p>
     * 处理管理员注册请求，验证并保存管理员信息
     * </p>
     * @param request 注册请求，包含管理员信息
     * @return 注册结果
     */
    @PostMapping("/register/admin")
    public ApiResponse<String> registerAdmin(@RequestBody Object request) {
        try {
            log.info("Admin register request");
            String result = userService.register(request);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Admin register failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取用户信息
     * <p>
     * 根据用户ID获取用户详细信息
     * </p>
     * @param userId 用户ID
     * @return 用户信息
     */
    @GetMapping("/info/{userId}")
    public ApiResponse<Object> getUserInfo(@PathVariable Long userId) {
        try {
            log.info("Get user info: {}", userId);
            Object info = userService.getUserInfo(userId);
            return ApiResponse.success(info);
        } catch (Exception e) {
            log.error("Get user info failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

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

            Long headerUserId = getRequestUserId(httpRequest);
            Long userId;
            if (headerUserId != null) {
                if (bodyUserId != null && !bodyUserId.equals(headerUserId)) {
                    log.warn("拒绝越权改密: 调用者={}, 请求体目标={}", headerUserId, bodyUserId);
                    return ApiResponse.forbidden("只能修改本人的密码");
                }
                userId = headerUserId;
            } else {
                log.warn("change-password 缺少 X-User-Id（未经网关），回落到请求体 userId={}", bodyUserId);
                userId = bodyUserId;
            }
            if (userId == null) {
                return ApiResponse.error("参数不完整");
            }

            userService.changePassword(userId, oldPassword, newPassword);
            return ApiResponse.success("密码修改成功");
        } catch (Exception e) {
            log.error("Change password failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
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
