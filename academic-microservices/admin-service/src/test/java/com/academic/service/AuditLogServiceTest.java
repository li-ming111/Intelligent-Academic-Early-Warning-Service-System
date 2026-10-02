package com.academic.service;

import com.academic.common.entity.User;
import com.academic.entity.AuditLog;
import com.academic.mapper.AuditLogMapper;
import com.academic.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 审计日志写入方的单元测试（2026-10-02）。
 *
 * <p>钉两件事：<b>操作人是谁</b>（身份只可能来自网关注入的头，而那个头直连时可以伪造
 * ⇒ 必须回查 {@code users}），以及 <b>{@code audit_logs.user_id} 有外键</b>
 * ⇒ 解析不出来就写 {@code NULL}，绝不编一个 0（本项目在 {@code messages.sender_id}
 * 上踩过：恒设 0 导致每次插入都 {@code ERROR 1452}）。
 */
@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogMapper auditLogMapper;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private AuditLogService auditLogService;

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static void withRequest(String userId, String username) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (userId != null) {
            request.addHeader("X-User-Id", userId);
        }
        if (username != null) {
            request.addHeader("X-Username", username);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private AuditLog capture() {
        ArgumentCaptor<AuditLog> cap = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogMapper).insert(cap.capture());
        return cap.getValue();
    }

    /** 网关注入的 X-User-Id 指向真实用户 → 记下他。 */
    @Test
    void record_attributesToRealActorFromGatewayHeader() {
        withRequest("1", "admin");
        when(userMapper.selectById(1L)).thenReturn(new User().setId(1L).setUsername("admin"));

        auditLogService.record("DELETE", "USER", 16L, "删除账号 adm001");

        AuditLog saved = capture();
        assertEquals(1L, saved.getUserId());
        assertEquals("DELETE", saved.getActionType());
        assertEquals("USER", saved.getEntityType());
        assertEquals(16L, saved.getEntityId());
        assertEquals("删除账号 adm001", saved.getDetails());
        assertNotNull(saved.getCreatedAt());
    }

    /**
     * ★ X-User-Id 指向一个**不存在的用户**（伪造头，或账号已被删）→ 必须回落到
     * 按用户名查；都查不到就写 NULL。
     *
     * <p>这里不能"信任头部直接写"：{@code audit_logs.user_id} 有外键指向 {@code users.id}，
     * 写一个不存在的 id 会 {@code ERROR 1452} —— 而这条审计是在业务方法**内部**调用的，
     * 异常冒泡出去会把"删用户"整笔事务带崩。
     */
    @Test
    void record_fallsBackToUsernameWhenHeaderIdDoesNotExist() {
        withRequest("99999", "admin");
        when(userMapper.selectById(99999L)).thenReturn(null);
        when(userMapper.selectOne(any())).thenReturn(new User().setId(1L).setUsername("admin"));

        auditLogService.record("UPDATE", "WARNING_RULE", 3L, "修改预警规则");

        assertEquals(1L, capture().getUserId());
    }

    /** 两个头都解析不出来 → {@code user_id} 写 NULL，而不是 0。 */
    @Test
    void record_writesNullActorInsteadOfZero() {
        withRequest("abc", "查无此人");
        when(userMapper.selectOne(any())).thenReturn(null);

        auditLogService.record("HANDLE", "WARNING", 4L, "处理学业预警");

        AuditLog saved = capture();
        assertNull(saved.getUserId(), "解析不出操作人时写 NULL —— 写 0 会撞外键");
        assertEquals("HANDLE", saved.getActionType());
    }

    /** 不在请求线程里（定时任务/异步）→ 不 NPE，照常记一条无主日志。 */
    @Test
    void record_withoutRequestContextStillWrites() {
        RequestContextHolder.resetRequestAttributes();

        auditLogService.record("CREATE", "USER", 5L, "定时任务建号");

        assertNull(capture().getUserId());
    }

    /**
     * 落库失败**不能**影响业务 —— 这是"审计与业务分开"的全部意义。
     * 负向对照：把 record 里那个 try/catch 去掉，这条立刻红。
     */
    @Test
    void record_swallowsDatabaseFailure() {
        withRequest("1", "admin");
        when(userMapper.selectById(1L)).thenReturn(new User().setId(1L).setUsername("admin"));
        when(auditLogMapper.insert(any(AuditLog.class))).thenThrow(new RuntimeException("db down"));

        assertDoesNotThrow(() -> auditLogService.record("DELETE", "USER", 2L, "删除账号"));
    }

    /** 超长字段截断到列宽（{@code action_type}/{@code entity_type} 都是 varchar(50)）。 */
    @Test
    void record_truncatesOverlongTypes() {
        withRequest(null, null);

        auditLogService.record("A".repeat(120), "B".repeat(120), 1L, "d");

        AuditLog saved = capture();
        assertEquals(50, saved.getActionType().length());
        assertEquals(50, saved.getEntityType().length());
    }

    /** 兜底：mock 一个 null 的 request（理论上不会发生）也不该 NPE。 */
    @Test
    void record_handlesNullRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-User-Id")).thenReturn(null);
        when(request.getHeader("X-Username")).thenReturn(null);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        auditLogService.record("CREATE", "USER", 1L, "x");

        assertNull(capture().getUserId());
    }
}
