package com.academic.service.impl;

import com.academic.service.TongYiService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * `AIServiceImpl.analyzeScore` 对「取不到」与「确实没有」的区分（W26）。
 *
 * <p>同一句「暂无成绩数据，请先录入成绩」，在两种情况下含义完全相反：
 * 「这个学生真的没有成绩」是事实，「下游拒绝/挂了所以没取到」是**谎报**。
 * 上游 `AIController.analyzeScores` 取数失败时会带 `scoresUnavailable`，
 * 这里必须优先认它。
 */
@ExtendWith(MockitoExtension.class)
class AIServiceImplAnalyzeScoreHonestyTest {

    @Mock
    private TongYiService tongYiService;

    private AIServiceImpl service() {
        return new AIServiceImpl(tongYiService);
    }

    private static final String NO_DATA = "暂无成绩数据，请先录入成绩";

    @Test
    @DisplayName("带 scoresUnavailable → 说「读取不到」，绝不能说「暂无成绩数据」")
    @SuppressWarnings("unchecked")
    void testUnavailableIsNotReportedAsNoData() {
        Map<String, Object> req = new java.util.HashMap<>();
        req.put("studentId", "27");
        req.put("scoresUnavailable", "成绩服务返回 code=403：只能查看本人的成绩");

        Map<String, Object> result = (Map<String, Object>) service().analyzeScore(req);

        String suggestion = String.valueOf(result.get("suggestion"));
        assertNotEquals(NO_DATA, suggestion, "取不到不能报成「确实没有」");
        assertTrue(suggestion.contains("读取不到"), "要说清是读不到：" + suggestion);
        assertTrue(suggestion.contains("403"), "原因要透出来：" + suggestion);
        assertEquals(false, result.get("scoresAvailable"), "要显式标出这次没有成绩数据");
        assertEquals(0, result.get("totalCourses"));
    }

    @Test
    @DisplayName("真的没有成绩（无 scores、无 scoresUnavailable）→ 才回「暂无成绩数据」")
    @SuppressWarnings("unchecked")
    void testGenuineNoDataStillSaysNoData() {
        Map<String, Object> req = new java.util.HashMap<>();
        req.put("studentId", "27");

        Map<String, Object> result = (Map<String, Object>) service().analyzeScore(req);

        assertEquals(NO_DATA, result.get("suggestion"));
    }

    @Test
    @DisplayName("空成绩列表 → 同样算「真的没有」")
    @SuppressWarnings("unchecked")
    void testEmptyListIsNoData() {
        Map<String, Object> req = new java.util.HashMap<>();
        req.put("studentId", "27");
        req.put("scores", List.of());

        Map<String, Object> result = (Map<String, Object>) service().analyzeScore(req);

        assertEquals(NO_DATA, result.get("suggestion"));
    }

    @Test
    @DisplayName("有真成绩 → 正常统计，不出现「读取不到」")
    @SuppressWarnings("unchecked")
    void testRealScoresAreAnalysed() {
        Map<String, Object> req = new java.util.HashMap<>();
        req.put("studentId", "27");
        // ⚠️ courseId 是 ScoreCaliber.latestPerCourse 的必需键，缺了整行会被丢掉
        req.put("scores", List.of(
                Map.of("courseId", 1, "courseName", "高等数学", "scoreTotal", 56.5, "credits", 4, "gradePoint", 0.5, "semester", 3),
                Map.of("courseId", 2, "courseName", "人工智能导论", "scoreTotal", 86.0, "credits", 3, "gradePoint", 3.5, "semester", 3)));

        Map<String, Object> result = (Map<String, Object>) service().analyzeScore(req);

        assertEquals(2, result.get("totalCourses"));
        assertEquals(1, result.get("failCount"));
        assertFalse(String.valueOf(result.get("suggestion")).contains("读取不到"));
    }
}
