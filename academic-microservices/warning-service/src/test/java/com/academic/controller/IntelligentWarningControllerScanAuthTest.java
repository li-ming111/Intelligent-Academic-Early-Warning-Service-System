package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.task.WarningScheduledTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * `POST /warnings/scan` 的角色闸（W26）。
 *
 * <p>钉的缺陷：这个端点会**全库重扫**并写 `academic_warning` / `warning_release_records` /
 * `notifications`，还会给学生本人与辅导员**发通知** —— 但改之前它**零角色判定**，
 * 而网关对 `/api/warnings/**` 在 role 1/2/3 的任一前缀分支里都没有，
 * 一路落到 `AuthenticationFilter:446 return true`（网关单测自己就写着
 * `allow(STUDENT, GET, "/api/warnings/scan")`）。
 * 也就是说：**任何登录用户**（含学生）都能触发一次全校重扫 + 群发通知。
 *
 * <p>拒绝形状跟本仓既有口径一致：HTTP 200 + body `code=403`（前端 `client.js` 按 code 判）。
 *
 * <p>下面两组判据是配对的，缺一不可：
 * <ul>
 *   <li>被拒的角色 → code 403 <b>且</b> `triggerNowDetailed()` **一次都没被调用**。
 *       只断 403 不够：闸写在了调用之后，照样回 403，但库已经被扫改过了。</li>
 *   <li>放行的角色 → code 200 <b>且</b> 恰好调用一次。只断 200 不够：可能压根没触发。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class IntelligentWarningControllerScanAuthTest {

    @Mock
    private WarningScheduledTask warningScheduledTask;

    @InjectMocks
    private IntelligentWarningController controller;

    private static MockHttpServletRequest requestWithRole(String role) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (role != null) {
            request.addHeader("X-Role", role);
        }
        return request;
    }

    // ------------------------------------------------------------------
    // 必须被拒：学生(1) / 教师(2) / 无角色头
    // ------------------------------------------------------------------

    @Test
    @DisplayName("学生(role=1)触发扫描 → code=403，且扫描方法一次都没被调用")
    void testStudentCannotTriggerScan() {
        ApiResponse<Object> resp = controller.triggerScan(requestWithRole("1"));

        assertEquals(403, resp.getCode(), "学生不该能触发全校重扫");
        assertNotEquals(200, resp.getCode());
        assertFalse(resp.isSuccess());
        // ⭐ 关键：闸必须在**调用之前**。写在后面照样回 403，但库已经变了。
        verify(warningScheduledTask, never()).triggerNowDetailed();
        verify(warningScheduledTask, never()).triggerNow();
    }

    @Test
    @DisplayName("教师(role=2)触发扫描 → code=403，且扫描方法一次都没被调用")
    void testTeacherCannotTriggerScan() {
        ApiResponse<Object> resp = controller.triggerScan(requestWithRole("2"));

        assertEquals(403, resp.getCode());
        assertFalse(resp.isSuccess());
        verify(warningScheduledTask, never()).triggerNowDetailed();
    }

    @Test
    @DisplayName("缺少 X-Role 头 → code=403（默认拒绝，不是默认放行）")
    void testMissingRoleIsDenied() {
        ApiResponse<Object> resp = controller.triggerScan(requestWithRole(null));

        assertEquals(403, resp.getCode(), "读不到角色时必须拒绝");
        verify(warningScheduledTask, never()).triggerNowDetailed();
    }

    @Test
    @DisplayName("未知角色(role=99)触发扫描 → code=403")
    void testUnknownRoleIsDenied() {
        ApiResponse<Object> resp = controller.triggerScan(requestWithRole("99"));

        assertEquals(403, resp.getCode());
        verify(warningScheduledTask, never()).triggerNowDetailed();
    }

    // ------------------------------------------------------------------
    // 必须被放行：辅导员(3) / 管理员(4) / 学院管理员(5)，以及两个字符串写法
    // ------------------------------------------------------------------

    @Test
    @DisplayName("辅导员(role=3)触发扫描 → code=200，且扫描恰好执行一次")
    void testCounselorCanTriggerScan() {
        when(warningScheduledTask.triggerNowDetailed())
                .thenReturn(new WarningScheduledTask.ScanResult(2, 1, false));

        ApiResponse<Object> resp = controller.triggerScan(requestWithRole("3"));

        assertEquals(200, resp.getCode());
        assertTrue(resp.isSuccess());
        verify(warningScheduledTask, times(1)).triggerNowDetailed();
    }

    @Test
    @DisplayName("管理员(role=4)触发扫描 → code=200，返回体带 generatedCount / releasedCount")
    void testAdminCanTriggerScanAndSeesBothCounts() {
        when(warningScheduledTask.triggerNowDetailed())
                .thenReturn(new WarningScheduledTask.ScanResult(5, 3, false));

        ApiResponse<Object> resp = controller.triggerScan(requestWithRole("4"));

        assertEquals(200, resp.getCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) resp.getData();
        assertNotNull(body, "成功时必须带 data");
        assertEquals(5, body.get("generatedCount"));
        assertEquals(3, body.get("releasedCount"), "自动解除的条数必须一起报，不能只报新增");
        assertEquals(false, body.get("skipped"));
        verify(warningScheduledTask, times(1)).triggerNowDetailed();
    }

    @Test
    @DisplayName("学院管理员(role=5)触发扫描 → code=200")
    void testCollegeAdminCanTriggerScan() {
        when(warningScheduledTask.triggerNowDetailed())
                .thenReturn(new WarningScheduledTask.ScanResult(0, 0, false));

        ApiResponse<Object> resp = controller.triggerScan(requestWithRole("5"));

        assertEquals(200, resp.getCode());
        verify(warningScheduledTask, times(1)).triggerNowDetailed();
    }

    @Test
    @DisplayName("角色的字符串写法(admin)同样放行 —— 与各服务 isAdmin 的双写法一致")
    void testStringRoleNameIsAccepted() {
        when(warningScheduledTask.triggerNowDetailed())
                .thenReturn(new WarningScheduledTask.ScanResult(0, 0, false));

        assertEquals(200, controller.triggerScan(requestWithRole("admin")).getCode());
        assertEquals(200, controller.triggerScan(requestWithRole("counselor")).getCode());
    }

    // ------------------------------------------------------------------
    // 并发守卫：已在扫描中时如实说"跳过"，不许报成"生成了 0 条"
    // ------------------------------------------------------------------

    @Test
    @DisplayName("已有扫描在进行中 → code=200 但 skipped=true，且 message 说明未重复执行")
    void testConcurrentScanIsReportedAsSkipped() {
        when(warningScheduledTask.triggerNowDetailed())
                .thenReturn(new WarningScheduledTask.ScanResult(0, 0, true));

        ApiResponse<Object> resp = controller.triggerScan(requestWithRole("4"));

        assertEquals(200, resp.getCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) resp.getData();
        assertEquals(true, body.get("skipped"));
        assertNotNull(body.get("message"), "跳过一次扫描必须说明原因，不能静默回 0");
    }
}
