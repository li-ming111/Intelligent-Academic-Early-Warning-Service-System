package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.service.CaptchaService;
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

    /** 登录验证码（W1.12，2026-10-02）。控制器构造函数要两个依赖，这里必须一起给。 */
    @Mock
    private CaptchaService captchaService;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    /** 造一个"验证码已经校验通过"的请求体。 */
    private static Map<String, Object> validLoginBody() {
        Map<String, Object> request = new HashMap<>();
        request.put("username", "2023020616");
        request.put("password", "123456");
        request.put("captchaId", "11111111-2222-3333-4444-555555555555");
        request.put("captcha", "A2C4");
        return request;
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

    // ==================== 登录验证码（W1.12，2026-10-02） ====================
    //
    // 改之前验证码是**纯前端**的：Login.vue 用 Math.random() 生成 4 位串、提交前自己比，
    // 小程序同款；`AuthController.login` 只收 username/password ⇒ **直接 POST 一次登录
    // 接口就绕过去了**。下面四条把"服务端必须验码、验不过不许进 userService"钉住。
    //
    // 判据特意断言 `userService` **一次都没被调用** —— 只断言返回 code != 200 的话，
    // 把验证码检查挪到 `userService.login` 之后（或干脆删掉）这条仍会绿。

    @Test
    void testLogin_Success() {
        // 准备测试数据
        Map<String, Object> request = validLoginBody();

        // 模拟行为
        jakarta.servlet.http.HttpServletRequest httpRequest = mock(jakarta.servlet.http.HttpServletRequest.class);
        when(captchaService.verifyAndConsume(any(), any())).thenReturn(true);
        when(userService.login(eq(request), any())).thenReturn(new Object());

        // 执行测试
        ApiResponse<Object> result = authController.login(request, httpRequest);

        // 验证结果
        assertNotNull(result);
        assertEquals(200, result.getCode());

        // 验证调用
        verify(captchaService, times(1)).verifyAndConsume("11111111-2222-3333-4444-555555555555", "A2C4");
        verify(userService, times(1)).login(eq(request), any());
    }

    /**
     * 请求体里**完全没有**验证码（脚本直接打接口的形状）⇒ 必须被挡在 userService 之前。
     */
    @Test
    void testLogin_RejectedWhenCaptchaAbsent() {
        Map<String, Object> request = new HashMap<>();
        request.put("username", "2023020616");
        request.put("password", "123456");

        // 真的 CaptchaService 对空 id/空码返回 false，这里如实桩成 false
        when(captchaService.verifyAndConsume(null, null)).thenReturn(false);

        ApiResponse<Object> result = authController.login(request, mock(jakarta.servlet.http.HttpServletRequest.class));

        assertNotEquals(200, result.getCode());
        // 关键：口令校验根本没发生
        verify(userService, never()).login(any(), any());
    }

    /**
     * 码对不上（签发过但填错）⇒ 拒绝，且不许进 userService。
     */
    @Test
    void testLogin_RejectedWhenCaptchaWrong() {
        Map<String, Object> request = validLoginBody();
        when(captchaService.verifyAndConsume(any(), any())).thenReturn(false);

        ApiResponse<Object> result = authController.login(request, mock(jakarta.servlet.http.HttpServletRequest.class));

        assertNotEquals(200, result.getCode());
        assertNotNull(result.getMessage());
        assertTrue(result.getMessage().contains("验证码"), "错误信息应指向验证码，实际: " + result.getMessage());
        verify(userService, never()).login(any(), any());
    }

    /**
     * 前端可能把 captchaId 传成数字、或带上首尾空格。
     * 控制器必须把它们**转成字符串并 trim** 后再交给 CaptchaService ——
     * 直接 `(String) get(...)` 会 ClassCastException，表现成"登录 500"。
     */
    @Test
    void testLogin_TrimsAndStringifiesCaptchaFields() {
        Map<String, Object> request = validLoginBody();
        request.put("captchaId", "  11111111-2222-3333-4444-555555555555  ");
        request.put("captcha", " a2c4 ");

        when(captchaService.verifyAndConsume(any(), any())).thenReturn(true);
        when(userService.login(any(), any())).thenReturn(new Object());

        authController.login(request, mock(jakarta.servlet.http.HttpServletRequest.class));

        verify(captchaService, times(1)).verifyAndConsume("11111111-2222-3333-4444-555555555555", "a2c4");
    }

    /** `/auth/captcha` 必须真的返回 id 与文本两个键（前端两个都要用）。 */
    @Test
    void testCaptcha_ReturnsIdAndText() {
        Map<String, Object> issued = new HashMap<>();
        issued.put("captchaId", "id-1");
        issued.put("captchaText", "A2C4");
        when(captchaService.issue()).thenReturn(issued);

        ApiResponse<Map<String, Object>> result = authController.captcha();

        assertEquals(200, result.getCode());
        assertEquals("id-1", result.getData().get("captchaId"));
        assertEquals("A2C4", result.getData().get("captchaText"));
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