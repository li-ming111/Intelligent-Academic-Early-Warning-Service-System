package com.academic.controller;

import com.academic.mapper.ExportHistoryMapper;
import com.academic.mapper.ScoreRecordMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 导出挂科学生：**失败时不能回一个"内容其实是错误文字"的 xlsx**（W26）。
 *
 * <p><b>修之前</b>：catch 里只有
 * {@code response.getWriter().write("导出失败: " + e.getMessage())} —— 没有 {@code setStatus}，
 * 而上面的代码**已经**设过 xlsx 的 Content-Type。于是失败响应是
 * 「HTTP 200 + Content-Type: …spreadsheetml.sheet + 一段纯文本」。
 * 前端 {@code responseType:'blob'} 看不出区别，会把它当文件存成 `.xlsx` ——
 * 用户拿到一个"下载成功"的坏文件，双击打不开，也永远看不到那句错误。
 *
 * <p>同一族的另外两条（{@code exportScores}、{@code downloadTemplate}）写法逐字相同，
 * 所以这里钉的是**这一族**的形状，不是单个端点。
 *
 * <p>判据两向：
 * <ul>
 *   <li>失败 → **HTTP 非 200**（blob 端点不能用"200 + body.code"那套，前端不解 blob 的 code）
 *       + {@code application/json} + 体是能解析的 JSON；</li>
 *   <li>成功 → 仍是真的 xlsx（真 HTTP 200 + xlsx Content-Type + OOXML 的 {@code PK} 魔数）。
 *       只钉失败路径的话，把成功路径一起改坏了也照样绿。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AdminControllerExportInsufficientStudentsFailureTest {

    @Mock private ScoreRecordMapper scoreRecordMapper;
    @Mock private ExportHistoryMapper exportHistoryMapper;

    @InjectMocks private AdminController controller;

    /** 让导出目录指向临时目录 —— 否则会往工作目录的 "null/" 下写文件。 */
    private void pointExportDirTo(Path dir) {
        ReflectionTestUtils.setField(controller, "exportDir", dir.toString());
    }

    @Test
    @DisplayName("导出失败 → HTTP 非 200 + application/json + 体是可解析的 JSON（不是纯文本 + xlsx 头）")
    void testFailureIsRealHttpErrorWithJsonBody(@TempDir Path tmp) throws Exception {
        pointExportDirTo(tmp);
        // 让导出在**查库**这一步就炸 —— 此刻 Content-Type 还没被设成 xlsx
        when(scoreRecordMapper.selectList(any())).thenThrow(new RuntimeException("数据库连接中断"));

        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.exportInsufficientStudents(response);

        assertNotEquals(200, response.getStatus(),
                "失败绝不能回 200 —— 前端 responseType 是 blob，只认 HTTP 状态");
        assertEquals(500, response.getStatus());
        assertTrue(response.getContentType() != null
                        && response.getContentType().toLowerCase().contains("application/json"),
                "失败体必须是 JSON，实际是: " + response.getContentType());
        assertFalse(String.valueOf(response.getContentType()).contains("spreadsheetml"),
                "Content-Type 必须被重置掉，否则前端仍会当成 xlsx 存盘");

        String body = response.getContentAsString();
        assertTrue(body.trim().startsWith("{"), "体应是 JSON 对象，实际: " + body);
        // 真解析一遍，不用字符串包含糊过去
        com.fasterxml.jackson.databind.JsonNode json =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
        assertEquals(500, json.get("code").asInt());
        assertEquals(false, json.get("success").asBoolean());
        assertTrue(json.get("message").asText().contains("导出失败"), "要带上人话: " + body);
    }

    @Test
    @DisplayName("成功路径未被改坏 → HTTP 200 + xlsx Content-Type + 真的是 OOXML（PK 魔数）")
    void testSuccessStillProducesRealXlsx(@TempDir Path tmp) throws Exception {
        pointExportDirTo(tmp);
        when(scoreRecordMapper.selectList(any())).thenReturn(List.of());
        when(exportHistoryMapper.insert(any())).thenReturn(1);

        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.exportInsufficientStudents(response);

        assertEquals(200, response.getStatus());
        assertTrue(String.valueOf(response.getContentType()).contains("spreadsheetml"),
                "成功时必须还是 xlsx，实际: " + response.getContentType());
        byte[] bytes = response.getContentAsByteArray();
        assertTrue(bytes.length > 2, "文件不该是空的");
        // xlsx 是 zip：前两字节必须是 PK
        assertEquals('P', bytes[0]);
        assertEquals('K', bytes[1]);
    }

    @Test
    @DisplayName("失败时**不写导出历史** —— 不能给一次失败的导出留下一条成功记录")
    void testFailureDoesNotWriteExportHistory(@TempDir Path tmp) throws Exception {
        pointExportDirTo(tmp);
        when(scoreRecordMapper.selectList(any())).thenThrow(new RuntimeException("boom"));

        controller.exportInsufficientStudents(new MockHttpServletResponse());

        verify(exportHistoryMapper, never()).insert(any());
    }
}
