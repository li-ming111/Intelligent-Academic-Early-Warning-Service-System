package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.exception.ResourceNotFoundException;
import com.academic.service.SupportResourceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 这一层钉的是**响应信封**：失败必须带着非 200 的 {@code code} 出去。
 *
 * <p>历史缺陷：service 把"资源不存在""资源标题不能为空"写成返回值里的一段
 * {@code {status:"error"}}，而本控制器无条件 {@code ApiResponse.success(...)}
 * ⇒ **HTTP 200 + 响应体 code=200**。前端 axios 拦截器（{@code client.js}）只认
 * {@code code === 200} 为成功，于是这段错误被当成成功数据交给调用方；
 * 只看 HTTP 状态的调用方更是完全看不到失败。
 *
 * <p>所以下面每个失败用例都**同时**断言三件事：具体 code、{@code code != 200}、
 * {@code isSuccess() == false}。只断言 code 的话，"把 404 改成 200"这种回归可能漏网。
 */
@ExtendWith(MockitoExtension.class)
class SupportResourceControllerTest {

    @Mock
    private SupportResourceService supportResourceService;

    @InjectMocks
    private SupportResourceController controller;

    // ------------------------------------------------------------------
    // 失败：必须是非 200 的 code
    // ------------------------------------------------------------------

    @Test
    @DisplayName("详情：资源不存在 → code=404（原来 200/200 冒充成功）")
    void testDetail_NotFound() {
        when(supportResourceService.getResourceDetail("999"))
                .thenThrow(new ResourceNotFoundException("资源不存在: 999"));

        ApiResponse<Object> resp = controller.getResourceDetail("999");

        assertEquals(404, resp.getCode());
        assertNotEquals(200, resp.getCode());
        assertFalse(resp.isSuccess());
        assertTrue(resp.getMessage().contains("999"));
    }

    @Test
    @DisplayName("详情：资源ID不是数字 → code=400（原来是 NPE/NumberFormatException 冒成 500）")
    void testDetail_BadId() {
        when(supportResourceService.getResourceDetail("abc"))
                .thenThrow(new IllegalArgumentException("资源ID不是数字: abc"));

        ApiResponse<Object> resp = controller.getResourceDetail("abc");

        assertEquals(400, resp.getCode());
        assertNotEquals(200, resp.getCode());
        assertFalse(resp.isSuccess());
    }

    @Test
    @DisplayName("新增：标题为空 → code=400")
    void testAdd_BlankTitle() {
        when(supportResourceService.addResource(any()))
                .thenThrow(new IllegalArgumentException("资源标题不能为空"));

        ApiResponse<Object> resp = controller.addResource(new HashMap<>());

        assertEquals(400, resp.getCode());
        assertNotEquals(200, resp.getCode());
        assertFalse(resp.isSuccess());
    }

    @Test
    @DisplayName("更新：资源不存在 → code=404")
    void testUpdate_NotFound() {
        when(supportResourceService.updateResource(anyString(), any()))
                .thenThrow(new ResourceNotFoundException("资源不存在: 999"));

        ApiResponse<Object> resp = controller.updateResource("999", new HashMap<>());

        assertEquals(404, resp.getCode());
        assertNotEquals(200, resp.getCode());
        assertFalse(resp.isSuccess());
    }

    @Test
    @DisplayName("更新：字段显式传 null → code=400，而不是「更新成功」")
    void testUpdate_ExplicitNull() {
        when(supportResourceService.updateResource(anyString(), any()))
                .thenThrow(new IllegalArgumentException("字段 title 不能为 null：更新接口只更新非 null 字段"));

        Map<String, Object> request = new HashMap<>();
        request.put("title", null);
        ApiResponse<Object> resp = controller.updateResource("1", request);

        assertEquals(400, resp.getCode());
        assertNotEquals(200, resp.getCode());
        assertFalse(resp.isSuccess());
    }

    @Test
    @DisplayName("删除：资源不存在 → code=404；ID 非法 → code=400")
    void testDelete_Failures() {
        when(supportResourceService.deleteResource("999"))
                .thenThrow(new ResourceNotFoundException("资源不存在: 999"));
        when(supportResourceService.deleteResource("abc"))
                .thenThrow(new IllegalArgumentException("资源ID不是数字: abc"));

        ApiResponse<Object> notFound = controller.deleteResource("999");
        assertEquals(404, notFound.getCode());
        assertNotEquals(200, notFound.getCode());
        assertFalse(notFound.isSuccess());

        ApiResponse<Object> badId = controller.deleteResource("abc");
        assertEquals(400, badId.getCode());
        assertNotEquals(200, badId.getCode());
        assertFalse(badId.isSuccess());
    }

    @Test
    @DisplayName("没预料到的异常 → code=500（不能变成 200）")
    void testUnexpected_500() {
        when(supportResourceService.getResourceList(any()))
                .thenThrow(new IllegalStateException("数据库连接断了"));

        ApiResponse<Object> resp = controller.getResourceList(new HashMap<>());

        assertEquals(500, resp.getCode());
        assertNotEquals(200, resp.getCode());
        assertFalse(resp.isSuccess());
    }

    // ------------------------------------------------------------------
    // 成功：仍然是 200，且 data 原样带出
    // ------------------------------------------------------------------

    @Test
    @DisplayName("成功：详情 → code=200，data 是 service 的返回值")
    void testDetail_Success() {
        Map<String, Object> data = new HashMap<>();
        data.put("id", 1L);
        data.put("views", 151);
        when(supportResourceService.getResourceDetail("1")).thenReturn(data);

        ApiResponse<Object> resp = controller.getResourceDetail("1");

        assertEquals(200, resp.getCode());
        assertTrue(resp.isSuccess());
        assertSame(data, resp.getData());
    }

    @Test
    @DisplayName("成功：列表 → code=200（列表本身不产生 404）")
    void testList_Success() {
        Map<String, Object> data = new HashMap<>();
        data.put("resources", Collections.emptyList());
        data.put("total", 0);
        when(supportResourceService.getResourceList(null)).thenReturn(data);

        ApiResponse<Object> resp = controller.getResourceList(null);

        assertEquals(200, resp.getCode());
        assertTrue(resp.isSuccess());
    }
}
