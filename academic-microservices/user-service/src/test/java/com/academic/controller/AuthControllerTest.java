package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
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

    /** SecurityContextHolder 是 thread-local，用例之间会串，必须清。 */
    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ==================== /register/admin 的角色闸 ====================
    //
    // 该端点建的是**系统管理员(role=4)**。网关对它要求 role=4，但**网关不是网络边界** ——
    // user-service 的 8082 在本机可直连，而 Spring Security 那层只保证"已认证"，
    // 任意角色的 token 都算数。所以 AuthController 里再按**已验签 token 里的角色**判一次。
    // 2026-09-30 实测确认过这个洞是真的：改前 `/auth/register/**` 是 permitAll，
    // 不带任何 token 直连 `:8082/api/auth/register/admin` 能走到业务逻辑。下面三条钉住第二道闸。

    /** 匿名（SecurityContext 为空）→ 403，且**一次都不调** userService.register。 */
    @Test
    void testRegisterAdmin_anonymousRejected() {
        ApiResponse<String> result = authController.registerAdmin(new HashMap<>());

        assertFalse(result.isSuccess());
        assertEquals(403, result.getCode());
        verify(userService, never()).register(any(), any());
    }

    /** 学生 token（ROLE_1）→ 403 —— "直连 8082 拿学生 token 提权"的钉子。 */
    @Test
    void testRegisterAdmin_studentRejected() {
        authenticateAs(1);

        ApiResponse<String> result = authController.registerAdmin(new HashMap<>());

        assertFalse(result.isSuccess());
        assertEquals(403, result.getCode());
        verify(userService, never()).register(any(), any());
    }

    /** 管理员 token（ROLE_4）→ 放行，传下去的角色是端点写死的 4。 */
    @Test
    void testRegisterAdmin_adminAllowed() {
        authenticateAs(4);
        Map<String, Object> body = new HashMap<>();
        body.put("username", "adm002");
        when(userService.register(body, 4)).thenReturn("Register successful");

        ApiResponse<String> result = authController.registerAdmin(body);

        assertTrue(result.isSuccess());
        verify(userService, times(1)).register(body, 4);
    }

    /** 造一个"已验签 token 的 principal"，形状与 JwtAuthenticationFilter 写入的一致。 */
    private void authenticateAs(int role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("probe", null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role))));
    }
    
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
    
    /** 通用 /register 入口按**最低权限**（学生）处理。 */
    @Test
    void testRegister_Success() {
        // 准备测试数据
        Object request = new Object();

        // 模拟行为
        when(userService.register(request, 1)).thenReturn("Register successful");

        // 执行测试
        ApiResponse<String> result = authController.register(request);

        // 验证结果
        assertNotNull(result);

        // 验证调用
        verify(userService, times(1)).register(request, 1);
    }

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