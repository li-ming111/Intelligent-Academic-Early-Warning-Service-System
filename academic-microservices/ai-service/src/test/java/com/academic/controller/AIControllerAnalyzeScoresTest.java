package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.feign.StudentServiceFeignClient;
import com.academic.service.AIService;
import com.academic.service.TongYiService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * `GET /ai/analyze-scores/{studentId}` 的取数协议（W26）。
 *
 * <p>钉的两个缺陷（都在同一条路径上，实测现场见
 * `testsprite/local-check/_w26_ai_scores_probe.mjs`）：
 *
 * <ol>
 *   <li><b>没解包</b>：Feign 声明是 {@code ApiResponse<Object>}，旧代码把**整个信封**
 *       塞进 {@code request.put("scores", ...)}，而 {@code AIServiceImpl.analyzeScore}
 *       直接 {@code (List<Map>) request.get("scores")} ⇒ ClassCastException。</li>
 *   <li><b>取不到被说成"没有"</b>：下游 403（缺身份头 / 越权）被内层 catch 吞掉后，
 *       {@code scores} 键根本不存在，{@code analyzeScore} 便回
 *       "暂无成绩数据，请先录入成绩" —— 而该生库里有 29 行成绩。
 *       这就是本仓最忌讳的"把'没拿到'渲染成'确实没有'"。</li>
 * </ol>
 *
 * <p>判据分三组，各自都要能把对应的写法判红：
 * 成功 → 传下去的是 **List**（不是信封）；下游拒绝 → 传 {@code scoresUnavailable}
 * 且**不带** {@code scores}；抛异常 → 同样带 {@code scoresUnavailable}。
 */
@ExtendWith(MockitoExtension.class)
class AIControllerAnalyzeScoresTest {

    @Mock
    private AIService aiService;

    @Mock
    private TongYiService tongYiService;

    @Mock
    private StudentServiceFeignClient studentServiceClient;

    @InjectMocks
    private AIController controller;

    /** 捕获真正交给 AIService 的那个 request。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> capturedRequest() {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(aiService, times(1)).analyzeScore(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("下游返回成功 → 交给 AIService 的 scores 必须是 List，不能是 ApiResponse 信封")
    void testScoresAreUnwrapped() {
        List<Map<String, Object>> rows = List.of(
                Map.of("courseName", "高等数学", "scoreTotal", 56.5, "credits", 4, "gradePoint", 0.5));
        when(studentServiceClient.getStudentScores("27"))
                .thenReturn(ApiResponse.success(rows));
        when(aiService.analyzeScore(any())).thenReturn(Map.of("totalCourses", 1));

        ApiResponse<Object> resp = controller.analyzeScores("27");

        assertEquals(200, resp.getCode());
        Object passed = capturedRequest().get("scores");
        assertNotNull(passed, "成功取到时必须把成绩传下去");
        // ⭐ 这条就是"没解包"的判据：信封整个传下去时这里拿到的是 ApiResponse
        assertInstanceOf(List.class, passed, "scores 必须是解包后的 List，不能是 ApiResponse 信封");
        assertEquals(rows, passed);
        assertNull(capturedRequest().get("scoresUnavailable"), "取到了就不该再标「取不到」");
    }

    @Test
    @DisplayName("下游业务拒绝(code=403) → 传 scoresUnavailable，且**不**传 scores")
    void testDownstreamBusinessDenialIsReportedAsUnavailable() {
        when(studentServiceClient.getStudentScores("27"))
                .thenReturn(ApiResponse.error(403, "只能查看本人的成绩"));
        when(aiService.analyzeScore(any())).thenReturn(Map.of("totalCourses", 0));

        controller.analyzeScores("27");

        Map<String, Object> req = capturedRequest();
        assertNull(req.get("scores"), "被拒时绝不能带一个空 scores 进去 —— 那会被说成「确实没有」");
        Object why = req.get("scoresUnavailable");
        assertNotNull(why, "必须把「取不到」标出来，否则会被渲染成「暂无成绩数据」");
        String text = String.valueOf(why);
        assertTrue(text.contains("403"), "原因里要带上游的 code，便于排查：" + text);
        assertTrue(text.contains("只能查看本人的成绩"), "下游的原话要透出来：" + text);
    }

    @Test
    @DisplayName("Feign 抛异常(下游 403 空体/连不上) → 同样标 scoresUnavailable，不静默降级")
    void testFeignExceptionIsReportedAsUnavailable() {
        when(studentServiceClient.getStudentScores("27"))
                .thenThrow(new RuntimeException("403 Forbidden"));
        when(aiService.analyzeScore(any())).thenReturn(Map.of("totalCourses", 0));

        controller.analyzeScores("27");

        Map<String, Object> req = capturedRequest();
        assertNull(req.get("scores"));
        assertNotNull(req.get("scoresUnavailable"), "调用失败也要标出来，不能只剩一个 log.warn");
        assertTrue(String.valueOf(req.get("scoresUnavailable")).contains("403"));
    }

    @Test
    @DisplayName("下游回 200 但 data 为 null → 也算「取不到」，不算「没有成绩」")
    void testNullDataIsUnavailable() {
        when(studentServiceClient.getStudentScores("27"))
                .thenReturn(ApiResponse.success(null));
        when(aiService.analyzeScore(any())).thenReturn(Map.of("totalCourses", 0));

        controller.analyzeScores("27");

        Map<String, Object> req = capturedRequest();
        assertNull(req.get("scores"));
        assertNotNull(req.get("scoresUnavailable"));
    }
}
