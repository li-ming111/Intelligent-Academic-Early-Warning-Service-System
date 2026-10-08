package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.feign.StudentServiceFeignClient;
import com.academic.service.AIService;
import com.academic.service.TongYiService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 三个**纯 AI** 端点在模型没答时，**不许**再拿一句"…分析中，请稍后重试"冒充结果（W28）。
 *
 * <p><b>修之前</b>：`analyze-warning-trend` / `analyze-warning` / `analyze-class-scores`
 * 在 {@code generalChat()} 返回 null（没配 key，或调用失败）时，各自
 * {@code result.put("aiAnalysis", "…分析中，请稍后重试")} **并包成 {@code success:true}**。
 * 那是一句**永远等不到**的进度话术 —— 没配 key 时重试一万次也不会有结果，
 * 用户只会以为"再等等就有了"。与 W26 已修的 `/ai/chat`、`/ai/qa` 是同一族
 * （说法与事实不符），当时**漏掉了这三条**。
 *
 * <p>本测试钉三件事，三件都要能各自判红：
 * <ol>
 *   <li>没配 key ⇒ 如实回 {@code error}，说清是**配置问题**；</li>
 *   <li>配了 key 但模型没答 ⇒ 也回 {@code error}，但要说是**调用失败**（重试有意义）——
 *       两者合并成一句就等于把配置问题伪装成瞬时故障；</li>
 *   <li>模型真答了 ⇒ 原样回真文本（防止"把所有路径一律改成报错"这种把功能修死
 *       也能让上面两条变绿的写法）。</li>
 * </ol>
 *
 * <p>另有一条**独立的**：{@code GET /ai/analyze/{studentId}} 拿不到评语时**不能整个回 error** ——
 * 同一个响应里的 {@code scoreAnalysis} / {@code riskPrediction} 是规则算出来的真数据。
 */
@ExtendWith(MockitoExtension.class)
class AIControllerAiUnavailableTest {

    @Mock private AIService aiService;
    @Mock private TongYiService tongYiService;
    @Mock private StudentServiceFeignClient studentServiceClient;

    @InjectMocks private AIController controller;

    /** 修之前那三条**永远等不到**的占位串 —— 返回值里一个都不许出现。 */
    private static final String[] OLD_FAKE_STRINGS = {
            "预警趋势分析中，请稍后重试",
            "预警分析中，请稍后重试",
            "班级分析中，请稍后重试"
    };

    /** 判据：这不是一次"成功"，且话里没有那三句占位串，也没有把动作说成在进行中。 */
    private void assertHonestFailure(ApiResponse<Object> resp, String mustMention) {
        assertNotEquals(200, resp.getCode(),
                "模型没答就不是成功 —— 修之前这里包的是 success:true");
        String msg = String.valueOf(resp.getMessage());
        assertTrue(msg.contains(mustMention), "要说清是哪种原因，实际: " + msg);
        for (String fake : OLD_FAKE_STRINGS) {
            assertFalse(msg.contains(fake), "不许再出现「" + fake + "」这种等不到的进度话术: " + msg);
        }
        assertFalse(msg.contains("请稍后重试"),
                "「请稍后重试」把配置问题伪装成瞬时故障 —— 没配 key 时重试永远没用: " + msg);
    }

    // ==================== 没配 key：配置问题 ====================

    @Test
    @DisplayName("预警趋势：没配 key ⇒ error + 说清是「未配置」，且不含旧占位串")
    void testWarningTrendUnconfigured() {
        when(tongYiService.isConfigured()).thenReturn(false);
        when(tongYiService.generalChat(anyString())).thenReturn(null);

        assertHonestFailure(controller.analyzeWarningTrend(3L), "未配置");
    }

    @Test
    @DisplayName("单条预警分析：没配 key ⇒ error + 同上一族")
    void testAnalyzeWarningUnconfigured() {
        when(tongYiService.isConfigured()).thenReturn(false);
        when(tongYiService.generalChat(anyString())).thenReturn(null);

        assertHonestFailure(controller.analyzeWarning(11L), "未配置");
    }

    @Test
    @DisplayName("班级成绩分析：没配 key ⇒ error + 同上一族")
    void testAnalyzeClassScoresUnconfigured() {
        when(tongYiService.isConfigured()).thenReturn(false);
        when(tongYiService.generalChat(anyString())).thenReturn(null);

        assertHonestFailure(controller.analyzeClassScores(7L), "未配置");
    }

    // ==================== 配了 key 但调用失败：瞬时故障 ====================

    @Test
    @DisplayName("配了 key 但模型没答 ⇒ error 且说明是「调用失败」，与「未配置」分开说")
    void testConfiguredButCallFailedIsDistinguishedFromUnconfigured() {
        when(tongYiService.isConfigured()).thenReturn(true);
        when(tongYiService.generalChat(anyString())).thenReturn(null);

        ApiResponse<Object> resp = controller.analyzeWarningTrend(3L);
        assertHonestFailure(resp, "失败");
        // ⭐ 判据的另一半：这句话**不能**说成"未配置"（那会把瞬时故障说成配置问题，用户去改配置）
        assertFalse(String.valueOf(resp.getMessage()).contains("未配置"),
                "两种原因必须分开：这是调用失败，不是没配 key");
    }

    @Test
    @DisplayName("空白串也算「没拿到」—— 不能把 \"\" 当成分析结果回下去")
    void testBlankAnswerIsNotAResult() {
        when(tongYiService.isConfigured()).thenReturn(true);
        when(tongYiService.generalChat(anyString())).thenReturn("   ");

        assertHonestFailure(controller.analyzeWarning(11L), "失败");
    }

    // ==================== 真答了：不能被改坏 ====================

    @Test
    @DisplayName("模型真答了 ⇒ 200 + 原样回真文本（三条端点各一次）")
    void testRealAnswerStillSucceeds() {
        when(tongYiService.generalChat(anyString())).thenReturn("这是真实的分析结果");
        // isConfigured() 在成功路径上**不被调用** ⇒ 不 stub，避免 UnnecessaryStubbing

        for (ApiResponse<Object> resp : new ApiResponse[]{
                controller.analyzeWarningTrend(3L),
                controller.analyzeWarning(11L),
                controller.analyzeClassScores(7L)}) {
            assertEquals(200, resp.getCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) resp.getData();
            assertEquals("这是真实的分析结果", data.get("aiAnalysis"));
        }
    }

    // ==================== analyzeStudent：不能连坐丢掉真数据 ====================

    @Test
    @DisplayName("综合分析拿不到评语 ⇒ 仍 200，保住 scoreAnalysis/riskPrediction，只把评语换成带原因的字段")
    void testAnalyzeStudentKeepsRealDataWhenCommentUnavailable() {
        when(aiService.analyzeScore(any())).thenReturn(Map.of("suggestion", "多做题"));
        when(aiService.predictRisk(any())).thenReturn(Map.of("riskLevel", "中"));
        when(tongYiService.isConfigured()).thenReturn(false);
        when(tongYiService.generalChat(anyString())).thenReturn(null);

        ApiResponse<Object> resp = controller.analyzeStudent("27");

        assertEquals(200, resp.getCode(), "评语没拿到不该把规则算出的成绩/风险一起丢掉");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals(Map.of("suggestion", "多做题"), data.get("scoreAnalysis"));
        assertEquals(Map.of("riskLevel", "中"), data.get("riskPrediction"));
        assertNull(data.get("aiComment"), "没拿到就不能给 aiComment 塞东西");
        String why = String.valueOf(data.get("aiCommentUnavailable"));
        assertNotNull(data.get("aiCommentUnavailable"),
                "必须显式说明评语为什么没有，否则前端分不清「没配 key」和「这学生没评语」");
        assertTrue(why.contains("未配置"), "原因要说人话: " + why);
    }

    @Test
    @DisplayName("综合分析拿到评语 ⇒ aiComment 是真文本，且**不带** aiCommentUnavailable")
    void testAnalyzeStudentWithComment() {
        when(aiService.analyzeScore(any())).thenReturn(Map.of("suggestion", "多做题"));
        when(aiService.predictRisk(any())).thenReturn(Map.of("riskLevel", "中"));
        when(tongYiService.generalChat(anyString())).thenReturn("该生整体尚可，建议加强实践。");

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) controller.analyzeStudent("27").getData();

        assertEquals("该生整体尚可，建议加强实践。", data.get("aiComment"));
        assertNull(data.get("aiCommentUnavailable"), "有评语时不该同时挂一个「未生成」的字段");
    }
}
