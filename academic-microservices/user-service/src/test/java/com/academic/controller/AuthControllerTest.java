package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthControllerTest {
    
    @Mock
    private UserService userService;
    
    @InjectMocks
    private AuthController authController;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // ==================== 这里原有 3 条 `/register/admin` 的角色闸用例 ====================
    //
    // 2026-10-01 随端点一起删掉了。它们钉住的是 AuthController 里那道"按已验签 token 的
    // 角色再判一次"的闸（匿名 / 学生 token / 管理员 token 三种）。那个端点建的是
    // **系统管理员(role=4)**，2026-09-30 实测确认过洞是真的：改前 `/auth/register/**`
    // 是 permitAll，不带任何 token 直连 `:8082/api/auth/register/admin` 能走到业务逻辑。
    //
    // 端点连同这道闸一起删了（零调用方 + 与 `/users/add` 重复）⇒ 攻击面随之消失，
    // 用例没有对象可测。管理员建号现在只剩 `/users/add` 一条路径，
    // 它的权限闸在 `UserControllerTest` 里钉着。

    @Test
    void testLogin_Success() {
        // 准备测试数据
        Map<String, Object> request = new HashMap<>();
        request.put("username", "2023020616");
        request.put("password", "123456");
        
        // 模拟行为
        when(userService.login(request)).thenReturn(new Object());
        
        // 执行测试
        ApiResponse<Object> result = authController.login(request);
        
        // 验证结果
        assertNotNull(result);
        
        // 验证调用
        verify(userService, times(1)).login(request);
    }
    
    // ==================== 这里原有 `testRegister_Success`（2026-10-01 删） ====================
    //
    // 它测的是裸的 `POST /register`（调 `authController.register(request)`）。那个端点
    // 2026-10-01 按"零调用方 = 删"删掉了 —— 它与 `POST /register/student` 是同一个调用
    // （两处实现体一字不差，都是 `userService.register(request, ROLE_STUDENT)`）。
    //
    // 它覆盖的"通用入口按最低权限处理"这件事**没有失去覆盖**：下面这条
    // `testRegisterStudent_ignoresRoleInBody` 走的就是 `registerStudent`，
    // 断言的是同一件事（传下去的是端点写死的 1，不是请求体里的 role），
    // 而且比原来更严（额外断言 `never()).register(..., 4)`）。所以是收敛，不是丢覆盖。

    /**
     * 请求体里带 {@code role:4} 也**提不了权** —— 学生注册端点固定传 1。
     * 这条是 2026-09-30"匿名者 POST /register/student 带 role:4 造出系统管理员"
     * 那个漏洞在 controller 侧的回归钉子（当时实测建出了 users.id=80、role=4）。
     */
    @Test
    void testRegisterStudent_ignoresRoleInBody() {
        Map<String, Object> maliciousBody = new HashMap<>();
        maliciousBody.put("username", "2023020701");
        maliciousBody.put("password", "123456");
        maliciousBody.put("role", 4);

        when(userService.register(maliciousBody, 1)).thenReturn("Register successful");

        authController.registerStudent(maliciousBody);

        // 关键断言：传下去的是端点写死的 1，不是请求体里的 4
        verify(userService, times(1)).register(maliciousBody, 1);
        verify(userService, never()).register(maliciousBody, 4);
    }
    
    @Test
    void testGetUserInfo_Success() {
        // 准备测试数据
        Long userId = 1L;
        Object userInfo = new Object();
        
        // 模拟行为
        when(userService.getUserInfo(userId)).thenReturn(userInfo);
        
        // 执行测试
        ApiResponse<Object> result = authController.getUserInfo(userId);
        
        // 验证结果
        assertNotNull(result);
        
        // 验证调用
        verify(userService, times(1)).getUserInfo(userId);
    }
}