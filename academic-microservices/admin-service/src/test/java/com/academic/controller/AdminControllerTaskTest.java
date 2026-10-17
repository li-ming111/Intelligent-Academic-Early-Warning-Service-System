package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.entity.AdminTask;
import com.academic.mapper.AdminTaskMapper;
import com.academic.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 管理端任务 CRUD 的单元测试（W1.11，2026-10-02）。
 *
 * <p>这四个端点原来是**整组桩**：列表写死空 {@code ArrayList}，创建/改状态/删除只
 * {@code log.info} 一行就回 {@code "…successfully"}。PC `AdminMessages.vue` 的
 * 「任务管理」tab 早就接好了，对着一个永远空的列表渲染 ⇒ 点什么都"成功"但什么也不发生。
 *
 * <p>这里钉三件事：<br>
 * ① <b>写操作真的落库</b>（{@code insert}/{@code updateStatus}/{@code deleteById} 被调用）；<br>
 * ② <b>非法入参在接口层就被挡</b>（标题空、优先级/状态不在白名单），且**不碰 mapper**；<br>
 * ③ <b>改/删不存在的 id 必须报错</b> —— 判据是**受影响行数**，不是"没抛异常"
 * （MyBatis-Plus 的 {@code updateById} 对不存在的 id 同样"成功"，只有行数能区分）。
 */
@ExtendWith(MockitoExtension.class)
class AdminControllerTaskTest {

    @Mock
    private AdminTaskMapper adminTaskMapper;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private AdminController controller;

    private static Map<String, Object> body(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    private AdminTask capturedInsert() {
        ArgumentCaptor<AdminTask> cap = ArgumentCaptor.forClass(AdminTask.class);
        verify(adminTaskMapper).insert(cap.capture());
        return cap.getValue();
    }

    // ---------------- 创建 ----------------

    /** 标题为空 → 报错，且一个字节都不写。 */
    @Test
    void create_rejectsBlankTitle() {
        ApiResponse<Map<String, Object>> r = controller.createTask(body("title", "   ", "priority", "high"));

        assertNotEquals(200, r.getCode());
        assertTrue(r.getMessage().contains("标题"), "错误信息要指出是标题的问题，实际: " + r.getMessage());
        verify(adminTaskMapper, never()).insert(any());
    }

    /** 标题整个缺失（前端漏传）→ 同一条分支。 */
    @Test
    void create_rejectsMissingTitle() {
        assertNotEquals(200, controller.createTask(body("priority", "high")).getCode());
        verify(adminTaskMapper, never()).insert(any());
    }

    /** 优先级不在 low/medium/high 里 → 报错。页面只有这三个标签，别的值落库后渲染不出来。 */
    @Test
    void create_rejectsUnknownPriority() {
        ApiResponse<Map<String, Object>> r = controller.createTask(body("title", "整理答辩材料", "priority", "urgent"));

        assertNotEquals(200, r.getCode());
        assertTrue(r.getMessage().contains("优先级"), "实际: " + r.getMessage());
        verify(adminTaskMapper, never()).insert(any());
    }

    /** 不传优先级 → 默认 medium（页面上"中"那个标签）。 */
    @Test
    void create_defaultsPriorityToMedium() {
        ApiResponse<Map<String, Object>> r = controller.createTask(body("title", "整理答辩材料"));

        assertEquals(200, r.getCode());
        assertEquals("medium", capturedInsert().getPriority());
    }

    /**
     * 正常创建：状态恒为 {@code pending}（与页面「待处理」统计卡对得上），
     * 时间戳显式写（本仓没有 {@code MetaObjectHandler}，UPDATE 路径不写就永远不动）。
     */
    @Test
    void create_writesPendingTaskWithTimestamps() {
        when(auditLogService.currentActorId()).thenReturn(1L);

        ApiResponse<Map<String, Object>> r = controller.createTask(
                body("title", "整理答辩材料", "description", "把 28 轮改动汇总", "priority", "high"));

        assertEquals(200, r.getCode());
        AdminTask saved = capturedInsert();
        assertEquals("整理答辩材料", saved.getTitle());
        assertEquals("把 28 轮改动汇总", saved.getDescription());
        assertEquals("high", saved.getPriority());
        assertEquals("pending", saved.getStatus(), "新建的一律是待处理");
        assertEquals(1L, saved.getCreatedBy());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
    }

    /**
     * ★ 解析不出操作人时写 {@code NULL}，**不写 0**。
     *
     * <p>{@code admin_tasks.created_by} 有指向 {@code users.id} 的**真外键** ——
     * 塞 0 会 {@code ERROR 1452} 把整笔插入打回去。本项目在 {@code messages.sender_id}
     * 上就踩过"恒设 0"这个坑。
     */
    @Test
    void create_writesNullCreatorInsteadOfZero() {
        when(auditLogService.currentActorId()).thenReturn(null);

        assertEquals(200, controller.createTask(body("title", "无主任务")).getCode());
        assertNull(capturedInsert().getCreatedBy());
    }

    /** 标题超过列宽（varchar(200)）→ 截断，不靠数据库报错。 */
    @Test
    void create_truncatesOverlongTitle() {
        when(auditLogService.currentActorId()).thenReturn(1L);

        controller.createTask(body("title", "标".repeat(260)));

        assertEquals(200, capturedInsert().getTitle().length());
    }

    /**
     * 落库抛异常 → 如实回「错误」这件事，但**不把异常原文透给客户端**。
     *
     * <p>W45：原断言写成 {@code r.getMessage().contains("db down")}，即"必须回显内部异常原文"，
     * 而 mock 抛的是 <b>裸 {@code RuntimeException}</b>（恰好是本仓承载业务文案的形状）。
     * 前端 {@code client.js} 会把 message 直接弹给用户 ⇒ 真 DB 故障若带 SQL 原文就会泄露。
     * 现在把 mock 换成数据库层真实抛的 {@code DataAccessException}，断言只回中性文案。
     */
    @Test
    void create_reportsInsertFailure() {
        when(auditLogService.currentActorId()).thenReturn(1L);
        when(adminTaskMapper.insert(any(AdminTask.class)))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("db down"));

        ApiResponse<Map<String, Object>> r = controller.createTask(body("title", "任务"));

        assertNotEquals(200, r.getCode());
        assertEquals(com.academic.common.util.ExceptionMessages.GENERIC, r.getMessage(),
                "数据库类异常只回中性文案，不暴露内部细节");
        assertFalse(String.valueOf(r.getMessage()).contains("db down"),
                "内部异常原文不能出现在给客户端的 message 里");
    }

    // ---------------- 改状态 ----------------

    /** 状态不在白名单 → 报错，且不碰 mapper。 */
    @Test
    void updateStatus_rejectsUnknownStatus() {
        ApiResponse<Map<String, Object>> r = controller.updateTaskStatus(7L, body("status", "done"));

        assertNotEquals(200, r.getCode());
        assertTrue(r.getMessage().contains("状态"), "实际: " + r.getMessage());
        verify(adminTaskMapper, never()).updateStatus(anyLong(), anyString());
    }

    /** 缺 status 字段 → 同一条分支。 */
    @Test
    void updateStatus_rejectsMissingStatus() {
        assertNotEquals(200, controller.updateTaskStatus(7L, body()).getCode());
        verify(adminTaskMapper, never()).updateStatus(anyLong(), anyString());
    }

    /**
     * ★ 改一个**不存在**的 id → 必须回「任务不存在」。
     *
     * <p>负向对照：把 controller 里那个 {@code if (rows == 0)} 去掉，
     * 这条立刻红 —— 它会回 200 说"改好了"，而库里一行没变。
     */
    @Test
    void updateStatus_reportsMissingTask() {
        when(adminTaskMapper.updateStatus(eq(99999L), eq("completed"))).thenReturn(0);

        ApiResponse<Map<String, Object>> r = controller.updateTaskStatus(99999L, body("status", "completed"));

        assertNotEquals(200, r.getCode());
        assertTrue(r.getMessage().contains("任务不存在"), "实际: " + r.getMessage());
        verify(auditLogService, never()).record(anyString(), anyString(), anyLong(), anyString());
    }

    /** 正常改状态 → 三态都放行，回里带上 id/status。 */
    @Test
    void updateStatus_acceptsAllThreeStates() {
        when(adminTaskMapper.updateStatus(eq(7L), anyString())).thenReturn(1);

        for (String s : List.of("pending", "in_progress", "completed")) {
            ApiResponse<Map<String, Object>> r = controller.updateTaskStatus(7L, body("status", s));
            assertEquals(200, r.getCode(), "状态 " + s + " 应当合法");
            assertEquals(s, r.getData().get("status"));
            assertEquals(7L, r.getData().get("id"));
        }
        verify(adminTaskMapper, times(3)).updateStatus(eq(7L), anyString());
        verify(auditLogService, times(3)).record(eq("UPDATE"), eq("ADMIN_TASK"), eq(7L), anyString());
    }

    /** 状态值带空格（前端偶尔会）→ trim 后仍然合法。 */
    @Test
    void updateStatus_trimsWhitespace() {
        when(adminTaskMapper.updateStatus(eq(7L), eq("in_progress"))).thenReturn(1);

        assertEquals(200, controller.updateTaskStatus(7L, body("status", "  in_progress ")).getCode());
    }

    // ---------------- 删除 ----------------

    /** ★ 删一个不存在的 id → 报「任务不存在」，而不是"删除成功"。 */
    @Test
    void delete_reportsMissingTask() {
        when(adminTaskMapper.deleteById(99999L)).thenReturn(0);

        ApiResponse<String> r = controller.deleteTask(99999L);

        assertNotEquals(200, r.getCode());
        assertTrue(r.getMessage().contains("任务不存在"), "实际: " + r.getMessage());
        verify(auditLogService, never()).record(anyString(), anyString(), anyLong(), anyString());
    }

    /** 正常删除 → 真调 deleteById，并记一条审计。 */
    @Test
    void delete_removesAndAudits() {
        when(adminTaskMapper.deleteById(7L)).thenReturn(1);

        assertEquals(200, controller.deleteTask(7L).getCode());
        verify(adminTaskMapper).deleteById(7L);
        verify(auditLogService).record(eq("DELETE"), eq("ADMIN_TASK"), eq(7L), anyString());
    }

    // ---------------- 列表 ----------------

    /** 列表回 {@code {total, items}} —— 页面读的就是这两个键（分页控件要真 total）。 */
    @Test
    void list_returnsTotalAndItems() {
        when(adminTaskMapper.countWithFilter(null)).thenReturn(3L);
        when(adminTaskMapper.selectPage(null, 0, 20)).thenReturn(List.of(Map.of("id", 3, "title", "任务三")));

        ApiResponse<Map<String, Object>> r = controller.getTasksList(1, 20, null);

        assertEquals(200, r.getCode());
        assertEquals(3L, r.getData().get("total"));
        assertEquals(1, ((List<?>) r.getData().get("items")).size());
    }

    /** {@code status=all}（页面「全部」按钮）等同于不过滤 —— 别拿字面量 "all" 去查库。 */
    @Test
    void list_treatsAllAsNoFilter() {
        when(adminTaskMapper.countWithFilter(null)).thenReturn(0L);
        when(adminTaskMapper.selectPage(null, 0, 20)).thenReturn(List.of());

        assertEquals(200, controller.getTasksList(1, 20, "all").getCode());
        verify(adminTaskMapper).countWithFilter(null);
    }

    /** 带状态筛选时按状态查。 */
    @Test
    void list_filtersByStatus() {
        when(adminTaskMapper.countWithFilter("pending")).thenReturn(1L);
        when(adminTaskMapper.selectPage("pending", 0, 20)).thenReturn(List.of(Map.of("id", 1)));

        controller.getTasksList(1, 20, "pending");

        verify(adminTaskMapper).countWithFilter("pending");
        verify(adminTaskMapper).selectPage("pending", 0, 20);
    }

    /** 页码/页长越界要收敛（page<1 → 1；size>200 → 200），否则 SQL 的 LIMIT 会拿到负数。 */
    @Test
    void list_clampsPageAndSize() {
        when(adminTaskMapper.countWithFilter(null)).thenReturn(0L);
        when(adminTaskMapper.selectPage(null, 0, 200)).thenReturn(List.of());

        controller.getTasksList(0, 9999, null);

        verify(adminTaskMapper).selectPage(null, 0, 200);
    }

    /** 第二页的 offset 要是 (page-1)*size。 */
    @Test
    void list_computesOffset() {
        when(adminTaskMapper.countWithFilter(null)).thenReturn(50L);
        when(adminTaskMapper.selectPage(null, 20, 20)).thenReturn(List.of());

        controller.getTasksList(2, 20, null);

        verify(adminTaskMapper).selectPage(null, 20, 20);
    }

    /** 查库抛异常 → 如实回错误。 */
    @Test
    void list_reportsQueryFailure() {
        when(adminTaskMapper.countWithFilter(null)).thenThrow(new RuntimeException("db down"));

        assertNotEquals(200, controller.getTasksList(1, 20, null).getCode());
    }

    /** 页面 `<el-table-column prop="createdAt">` 读的是这个键名，别让 SQL 别名漂掉。 */
    @Test
    void list_itemKeysMatchFrontendProps() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1L);
        row.put("title", "t");
        row.put("description", "d");
        row.put("priority", "high");
        row.put("status", "pending");
        row.put("createdBy", 1L);
        row.put("createdByUsername", "admin");
        row.put("createdAt", LocalDateTime.now());
        row.put("updatedAt", LocalDateTime.now());
        when(adminTaskMapper.countWithFilter(null)).thenReturn(1L);
        when(adminTaskMapper.selectPage(null, 0, 20)).thenReturn(List.of(row));

        @SuppressWarnings("unchecked")
        Map<String, Object> item = ((List<Map<String, Object>>) controller.getTasksList(1, 20, null)
                .getData().get("items")).get(0);

        for (String key : List.of("id", "title", "description", "priority", "status",
                "createdBy", "createdByUsername", "createdAt", "updatedAt")) {
            assertTrue(item.containsKey(key), "列表少了下标 " + key + " ⇒ 页面那一格会空");
        }
    }
}
