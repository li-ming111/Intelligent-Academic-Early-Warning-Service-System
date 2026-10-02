package com.academic.service;

import com.academic.common.entity.User;
import com.academic.entity.LoginLog;
import com.academic.mapper.LoginLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 登录日志写入方的单元测试。
 *
 * <p>这里刻意只钉两件事：**写进去的字段对不对**（尤其 IP 的取法，请求是经网关转发进来的，
 * 拿 {@code getRemoteAddr()} 只会得到网关的地址），以及**不该写的时候一行都不写**
 * （用户名不存在时没有 id 可填，写 0 会造出悬空引用）。
 */
@ExtendWith(MockitoExtension.class)
class LoginLogServiceTest {

    @Mock
    private LoginLogMapper loginLogMapper;

    @InjectMocks
    private LoginLogService loginLogService;

    private static HttpServletRequest request(String forwardedFor, String realIp, String userAgent, String remoteAddr) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        lenient().when(request.getHeader("X-Forwarded-For")).thenReturn(forwardedFor);
        lenient().when(request.getHeader("X-Real-IP")).thenReturn(realIp);
        lenient().when(request.getHeader("User-Agent")).thenReturn(userAgent);
        lenient().when(request.getRemoteAddr()).thenReturn(remoteAddr);
        return request;
    }

    /** 正常写一条：user_id / username / status / ip / device 都落到实体上。 */
    @Test
    void record_writesUserIdUsernameAndClientIp() {
        User user = new User().setId(27L).setUsername("2023020616");
        HttpServletRequest req = request("203.0.113.7, 10.0.0.1", null, "Mozilla/5.0", "10.0.0.9");

        loginLogService.record(user, "success", req);

        ArgumentCaptor<LoginLog> cap = ArgumentCaptor.forClass(LoginLog.class);
        verify(loginLogMapper).insert(cap.capture());
        LoginLog saved = cap.getValue();
        assertEquals(27L, saved.getUserId());
        assertEquals("2023020616", saved.getUsername());
        assertEquals("success", saved.getStatus());
        assertEquals("203.0.113.7", saved.getIpAddress(), "要取 X-Forwarded-For 的第一段，不是网关地址");
        assertEquals("Mozilla/5.0", saved.getDevice());
        assertNull(saved.getLocation(), "工程里没有 IP 归属地库，编一个城市出来不如空着");
        assertNotNull(saved.getLoginTime());
    }

    /** 没有 X-Forwarded-For 时回落 X-Real-IP，再回落 remoteAddr。 */
    @Test
    void clientIp_fallsBackThroughHeaders() {
        assertEquals("198.51.100.4",
                LoginLogService.clientIp(request(null, "198.51.100.4", null, "10.0.0.9")));
        assertEquals("10.0.0.9",
                LoginLogService.clientIp(request(null, null, null, "10.0.0.9")));
        assertNull(LoginLogService.clientIp(null), "没有请求上下文时不猜 IP");
    }

    /** 用户名不存在（user 为 null）→ 一行都不写。 */
    @Test
    void record_skipsWhenNoUserRow() {
        loginLogService.record(null, "failed", request(null, null, null, "10.0.0.9"));
        verifyNoInteractions(loginLogMapper);
    }

    /** user 有行但主键为 null（理论上不该发生）→ 同样不写。 */
    @Test
    void record_skipsWhenUserIdIsNull() {
        loginLogService.record(new User().setUsername("x"), "failed", null);
        verifyNoInteractions(loginLogMapper);
    }

    /**
     * 落库失败**不能**影响登录本身 —— 这是"写日志和登录分开"的全部意义。
     * 负向对照：把 record 里那个 try/catch 去掉，这条立刻红。
     */
    @Test
    void record_swallowsDatabaseFailure() {
        when(loginLogMapper.insert(any(LoginLog.class))).thenThrow(new RuntimeException("db down"));

        assertDoesNotThrow(() ->
                loginLogService.record(new User().setId(1L).setUsername("admin"), "success", null));
    }

    /** 超长字段要截断到列宽，否则 MySQL 严格模式直接报错、日志丢一条。 */
    @Test
    void record_truncatesOverlongFields() {
        User user = new User().setId(1L).setUsername("u".repeat(300));
        HttpServletRequest req = request("1.1.1.1", null, "A".repeat(500), "10.0.0.9");

        loginLogService.record(user, "success", req);

        ArgumentCaptor<LoginLog> cap = ArgumentCaptor.forClass(LoginLog.class);
        verify(loginLogMapper).insert(cap.capture());
        assertEquals(100, cap.getValue().getUsername().length());
        assertEquals(200, cap.getValue().getDevice().length());
    }
}
