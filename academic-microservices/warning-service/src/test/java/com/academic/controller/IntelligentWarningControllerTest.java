package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.exception.WarningNotFoundException;
import com.academic.service.IntelligentWarningService;
import com.academic.task.WarningScheduledTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * `/warnings/**` 失败路径的协议形状。
 *
 * <p>钉的缺陷：这三条路径（处理 / 详情 / 改状态）原先把"预警不存在"写成返回值里的
 * {@code {status:"error", message:"预警不存在"}}，controller 无条件
 * {@code ApiResponse.success(result)} —— 于是**失败在协议上是成功**：
 * HTTP 200 + 响应体 {@code code:200}。前端 axios 拦截器（{@code client.js}：
 * "只有 {@code code === 200} 才算成功，否则 reject"）会把这段错误当 data 交给调用方；
 * 只看 HTTP 状态的调用方更是完全看不见失败。改状态时 {@code status} 为空则
 * 一个字段都不改、照样回"更新成功"。
 *
 * <p>所以下面每个用例都同时断言两件事：<b>code 是错误码</b>、<b>不是 200</b>。
 * 只断言"不等于成功"不够 —— 断言必须能把"又变回 200"判红。
 */
@ExtendWith(MockitoExtension.class)
class IntelligentWarningControllerTest {

    @Mock
    private IntelligentWarningService intelligentWarningService;

    @Mock
    private WarningScheduledTask warningScheduledTask;

    @InjectMocks
    private IntelligentWarningController controller;

    // ------------------------------------------------------------------
    // 目标不存在 → 404（不是"成功"）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("处理预警：预警不存在 → body code=404，绝不能是 200")
    void testHandleWarning_NotFoundIsNotSuccess() {
        when(intelligentWarningService.handleWarning(any()))
                .thenThrow(new WarningNotFoundException("预警不存在: 999"));

        ApiResponse<Object> response = controller.handleWarning(Map.of("warningId", "999"));

        assertEquals(404, response.getCode(), "预警不存在应是 404");
        assertNotEquals(200, response.getCode(), "失败不能被报成成功");
        assertFalse(response.isSuccess());
        assertNull(response.getData(), "失败时不该带 data");
    }

    @Test
    @DisplayName("预警详情：预警不存在 → body code=404")
    void testGetWarningDetail_NotFoundIsNotSuccess() {
        when(intelligentWarningService.getWarningDetail("999"))
                .thenThrow(new WarningNotFoundException("预警不存在: 999"));

        ApiResponse<Object> response = controller.getWarningDetail("999");

        assertEquals(404, response.getCode());
        assertNotEquals(200, response.getCode());
        assertFalse(response.isSuccess());
    }

    @Test
    @DisplayName("改状态：预警不存在 → body code=404")
    void testUpdateWarningStatus_NotFoundIsNotSuccess() {
        when(intelligentWarningService.updateWarningStatus(eq("999"), any()))
                .thenThrow(new WarningNotFoundException("预警不存在: 999"));

        ApiResponse<Object> response = controller.updateWarningStatus("999", Map.of("status", 1));

        assertEquals(404, response.getCode());
        assertNotEquals(200, response.getCode());
        assertFalse(response.isSuccess());
    }

    // ------------------------------------------------------------------
    // 参数缺失 / 非法 → 400
    // ------------------------------------------------------------------

    @Test
    @DisplayName("改状态：缺 status 字段 → body code=400（原来是『更新成功』）")
    void testUpdateWarningStatus_MissingStatusIsBadRequest() {
        when(intelligentWarningService.updateWarningStatus(eq("9"), any()))
                .thenThrow(new IllegalArgumentException("缺少 status 字段"));

        ApiResponse<Object> response = controller.updateWarningStatus("9", new HashMap<>());

        assertEquals(400, response.getCode());
        assertNotEquals(200, response.getCode());
    }

    @Test
    @DisplayName("处理预警：缺 warningId → body code=400（原来是 {status:error} 被包成成功）")
    void testHandleWarning_MissingIdIsBadRequest() {
        when(intelligentWarningService.handleWarning(any()))
                .thenThrow(new IllegalArgumentException("预警ID不能为空"));

        ApiResponse<Object> response = controller.handleWarning(new HashMap<>());

        assertEquals(400, response.getCode());
        assertNotEquals(200, response.getCode());
    }

    @Test
    @DisplayName("生成预警：ruleId 指向不存在的规则 → body code=400")
    void testGenerateWarning_UnknownRuleIsBadRequest() {
        when(intelligentWarningService.generateWarning(any()))
                .thenThrow(new IllegalArgumentException("预警规则不存在: 99"));

        ApiResponse<Object> response = controller.generateWarning(Map.of("studentId", 13L, "ruleId", 99L));

        assertEquals(400, response.getCode());
        assertNotEquals(200, response.getCode());
    }

    @Test
    @DisplayName("预警详情：ID 不是数字 → body code=400")
    void testGetWarningDetail_NonNumericIdIsBadRequest() {
        when(intelligentWarningService.getWarningDetail("abc"))
                .thenThrow(new IllegalArgumentException("预警ID不是数字: abc"));

        ApiResponse<Object> response = controller.getWarningDetail("abc");

        assertEquals(400, response.getCode());
        assertNotEquals(200, response.getCode());
    }

    // ------------------------------------------------------------------
    // 真正的异常仍然是 500；成功路径仍是 200
    // ------------------------------------------------------------------

    @Test
    @DisplayName("处理预警：数据库炸了 → body code=500，不能吞成成功")
    void testHandleWarning_UnexpectedFailureIsServerError() {
        when(intelligentWarningService.handleWarning(any()))
                .thenThrow(new RuntimeException("communications link failure"));

        ApiResponse<Object> response = controller.handleWarning(Map.of("warningId", 9));

        assertEquals(500, response.getCode());
        assertNotEquals(200, response.getCode());
        assertFalse(response.isSuccess());
    }

    @Test
    @DisplayName("成功路径：正常处理仍应回 200 + data，别把好路径判红")
    void testHandleWarning_SuccessPathStillReturns200() {
        when(intelligentWarningService.handleWarning(any()))
                .thenReturn(Map.of("warningId", 9L, "message", "预警处理成功"));

        ApiResponse<Object> response = controller.handleWarning(Map.of("warningId", 9));

        assertEquals(200, response.getCode());
        assertTrue(response.isSuccess());
        assertNotNull(response.getData());
    }
}
