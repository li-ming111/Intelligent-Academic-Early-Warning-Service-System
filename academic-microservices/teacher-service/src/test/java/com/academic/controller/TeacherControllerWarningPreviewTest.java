package com.academic.controller;

import com.academic.common.constant.ScoreCaliber;
import com.academic.common.dto.ApiResponse;
import com.academic.common.entity.Course;
import com.academic.common.entity.ScoreRecord;
import com.academic.service.CourseService;
import com.academic.service.ScoreRecordService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * {@code POST /teachers/scores/warnings} 的判据（W24，2026-10-05）。
 *
 * <p><b>为什么有这个测试</b>：这个端点从前回 {@code "已为 N 名学生生成预警"}，
 * 而它**一行都没落库** —— 只把成绩流了一遍、数了数低于及格线的人数就返回。
 * 谁调它谁就得到一句"预警已生成"的**假确认**（HTTP 200 + success）。
 * 名字（{@code triggerWarnings}／{@code warningsTriggered}）也在重复这个说法。
 *
 * <p>本轮的修法是**把说法改成事实**：不再伪称"触发了预警"，而是如实说"只做统计"。
 * 所以这里钉住的正是"它没说谎"这件事 —— 尤其是**响应里不再出现
 * {@code warningsTriggered} 这个冒充动作的键**。
 *
 * <p>另有两条同族的既有口径一并钉住：① 未录入的成绩（{@code score == null}）
 * **不算 0 分**，不能计入；② 别人的课程要 403，且**连成绩都不该去查**。
 */
@ExtendWith(MockitoExtension.class)
class TeacherControllerWarningPreviewTest {

    @Mock private CourseService courseService;
    @Mock private ScoreRecordService scoreRecordService;

    @InjectMocks private TeacherController controller;

    private static final long TEACHER_29 = 29L;
    private static final long COURSE_1 = 1L;

    private HttpServletRequest req(String userId, String role) {
        HttpServletRequest r = mock(HttpServletRequest.class);
        lenient().when(r.getHeader("X-User-Id")).thenReturn(userId);
        lenient().when(r.getHeader("X-Role")).thenReturn(role);
        return r;
    }

    private void courseOwnedBy(long teacherId) {
        Course c = new Course();
        c.setId(COURSE_1);
        c.setTeacherId(teacherId);
        when(courseService.getById(COURSE_1)).thenReturn(c);
    }

    private static ScoreRecord row(long studentId, Double score) {
        ScoreRecord r = new ScoreRecord();
        r.setId(studentId);
        r.setStudentId(studentId);
        r.setCourseId(COURSE_1);
        r.setSemester("1");
        r.setScore(score);
        return r;
    }

    // ---------------------------------------------------------------- 说法与事实

    @Test
    @DisplayName("★ 响应里**不再有** warningsTriggered —— 这个键名断言了「预警被触发」，而事实是没有")
    @SuppressWarnings("unchecked")
    void responseNoLongerClaimsWarningsWereTriggered() {
        courseOwnedBy(TEACHER_29);
        when(scoreRecordService.getScoresByCourseId(COURSE_1))
                .thenReturn(List.of(row(11L, 55.0), row(12L, 80.0)));

        ApiResponse<Object> res = controller.inspectScoresForWarnings(COURSE_1, req("29", "2"));

        assertTrue(res.isSuccess(), "实际：" + res.getMessage());
        Map<String, Object> data = (Map<String, Object>) res.getData();
        assertFalse(data.containsKey("warningsTriggered"),
                "这个键在冒充一个没有发生的动作，必须去掉。实际键：" + data.keySet());
        assertEquals(1, ((Number) data.get("studentsBelowPassLine")).intValue(),
                "55 分那一个低于及格线。实际：" + data.get("studentsBelowPassLine"));
        assertEquals(ScoreCaliber.PASS_LINE, ((Number) data.get("passLine")).intValue(),
                "把及格线一并回给前端，这个数字才可解释");
    }

    @Test
    @DisplayName("message 如实说「只做统计、不生成预警记录」，不再自称已生成")
    void messageTellsTheTruth() {
        courseOwnedBy(TEACHER_29);
        when(scoreRecordService.getScoresByCourseId(COURSE_1)).thenReturn(List.of(row(11L, 42.0)));

        ApiResponse<Object> res = controller.inspectScoresForWarnings(COURSE_1, req("29", "2"));

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) res.getData();
        String message = String.valueOf(data.get("message"));
        assertTrue(message.contains("只做统计"), "必须说清这个端点到底做了什么。实际：" + message);
        assertTrue(message.contains("不生成预警记录"), "必须否掉「已生成」。实际：" + message);
        assertFalse(message.startsWith("已为"), "不能再用「已为 N 名学生生成预警」那种句式。实际：" + message);
    }

    // ---------------------------------------------------------------- 计数口径

    @Test
    @DisplayName("未录入的成绩不算 0 分 —— 不能被计成「低于及格线」")
    void missingScoreIsNotCountedAsZero() {
        courseOwnedBy(TEACHER_29);
        when(scoreRecordService.getScoresByCourseId(COURSE_1))
                .thenReturn(List.of(row(11L, null), row(12L, 59.0), row(13L, 60.0)));

        ApiResponse<Object> res = controller.inspectScoresForWarnings(COURSE_1, req("29", "2"));

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) res.getData();
        assertEquals(3, ((Number) data.get("totalStudents")).intValue(), "三个人都在册");
        assertEquals(1, ((Number) data.get("studentsBelowPassLine")).intValue(),
                "只有 59 分那个低于线：null 不算 0，60 分是及格（严格小于）。"
                        + "实际：" + data.get("studentsBelowPassLine"));
    }

    // ---------------------------------------------------------------- 归属闸

    @Test
    @DisplayName("别人的课程 → 403，且**连成绩都不去查**")
    void otherTeachersCourseIsRejectedWithoutReadingScores() {
        courseOwnedBy(18L);   // 课程属于 teacher001，调用者是 29

        ApiResponse<Object> res = controller.inspectScoresForWarnings(COURSE_1, req("29", "2"));

        assertFalse(res.isSuccess(), "不能对别人的课程做统计");
        assertEquals(403, res.getCode());
        verify(scoreRecordService, never()).getScoresByCourseId(anyLong());
    }

    @Test
    @DisplayName("管理员（X-Role: 4）旁路归属闸，照常统计")
    void adminBypassesOwnershipGuard() {
        when(scoreRecordService.getScoresByCourseId(COURSE_1)).thenReturn(List.of(row(11L, 30.0)));

        ApiResponse<Object> res = controller.inspectScoresForWarnings(COURSE_1, req("1", "4"));

        assertTrue(res.isSuccess(), "实际：" + res.getMessage());
        verify(courseService, never()).getById(any());
    }
}
