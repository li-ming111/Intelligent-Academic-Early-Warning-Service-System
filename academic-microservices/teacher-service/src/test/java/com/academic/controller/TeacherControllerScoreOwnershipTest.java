package com.academic.controller;

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

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@code PUT /teachers/scores/{scoreId}} 的归属闸（W10，2026-10-03；W36 改判据）。
 *
 * <p><b>修之前是什么样</b>：这个端点只有 {@code scoreId} 一个入参，连"谁在改"都不收，
 * 而**同一个资源**的 {@code DELETE /teachers/scores/{scoreId}} 一直有归属闸 ——
 * 同一个 id、两个动词、两套口径。实测（经网关 8076）：教师 18 用
 * {@code PUT /teachers/scores/{29 名下的记录 id}} 成功改掉了教师 29 录入的成绩，
 * 回 200「成绩更新成功」。成绩是这套系统里最敏感的数据，比读看板严重得多。
 *
 * <p><b>W36（2026-10-10）改判据</b>：{@code scoreId} 的语义由 {@code score_record.id}
 * 变为 {@code scores.id}，而 {@code scores} 表**没有** {@code teacher_id} ——
 * 归属落在**课程**上（{@code courses.teacher_id = users.id}）。因此这条闸的判据由
 * "录入人是不是我"改为"这门课是不是我教的"，与同族读端点逐字同形。
 * 这个测试文件原本钉的那条"记录没有 teacherId 就放行"随之**失效**
 * （列已经不存在了），换成对应的新负向对照："课程查不到 ⇒ 拒绝，不猜归属"。
 *
 * <p><b>判据的形状</b>（照本仓"守卫类用例"的定式）：不只是断言返回值，
 * 还要 {@code verify(..., never()).updateScore(...)} —— <b>那次写压根不该发生</b>。
 * 只看返回码会被"先改了再报错"骗过。
 */
@ExtendWith(MockitoExtension.class)
class TeacherControllerScoreOwnershipTest {

    @Mock private ScoreRecordService scoreRecordService;
    @Mock private CourseService courseService;

    @InjectMocks private TeacherController controller;

    /** teacher001：users.id=18。 */
    private static final long TEACHER_18 = 18L;
    /** 10001：users.id=29，活库里司明那批成绩所属课程的授课教师。 */
    private static final long TEACHER_29 = 29L;

    /** 活库真值风格：我要改的那一行所属课程（我教的）。 */
    private static final long MY_COURSE = 6L;
    /** 别人教的课程。 */
    private static final long OTHERS_COURSE = 1L;

    private HttpServletRequest req(String userId, String role) {
        HttpServletRequest r = mock(HttpServletRequest.class);
        lenient().when(r.getHeader("X-User-Id")).thenReturn(userId);
        lenient().when(r.getHeader("X-Role")).thenReturn(role);
        return r;
    }

    private static Course courseOwnedBy(long teacherId) {
        Course c = new Course();
        c.setId(MY_COURSE);
        c.setTeacherId(teacherId);
        return c;
    }

    /** 一行成绩：{@code scores.id} + 所属课程 —— 归属判据是课程，不是这行自己。 */
    private static ScoreRecord scoreInCourse(long courseId) {
        ScoreRecord rec = new ScoreRecord();
        rec.setId(277L);
        rec.setCourseId(courseId);
        return rec;
    }

    private static Map<String, Object> body(double score) {
        Map<String, Object> m = new HashMap<>();
        m.put("score", score);
        return m;
    }

    // ---------------------------------------------------------------- 拒绝

    @Test
    @DisplayName("改别人所授课程的成绩 ⇒ body code 403，且一行都不写")
    void updatingAnotherTeachersScoreIsRejected() {
        when(scoreRecordService.getById(277L)).thenReturn(scoreInCourse(OTHERS_COURSE));
        when(courseService.getById(OTHERS_COURSE)).thenReturn(courseOwnedBy(TEACHER_29));

        ApiResponse<Object> res = controller.updateScore(277L, body(99.0), req("18", "2"));

        assertEquals(403, res.getCode(), "必须是归属拒绝，不是通用失败");
        // ⭐ 真正的判据：那次写不该发生
        verify(scoreRecordService, never()).updateScore(any(), any(), any(), any());
    }

    @Test
    @DisplayName("坏 token（没有 X-User-Id）⇒ 同样拒绝，不放行")
    void missingIdentityHeaderIsRejected() {
        when(scoreRecordService.getById(277L)).thenReturn(scoreInCourse(MY_COURSE));
        when(courseService.getById(MY_COURSE)).thenReturn(courseOwnedBy(TEACHER_18));

        ApiResponse<Object> res = controller.updateScore(277L, body(99.0), req(null, "2"));

        assertEquals(403, res.getCode());
        verify(scoreRecordService, never()).updateScore(any(), any(), any(), any());
    }

    // ---------------------------------------------------------------- 放行

    @Test
    @DisplayName("改自己课上的成绩 ⇒ 正常更新（闸不误伤）")
    void updatingOwnScoreIsAllowed() {
        when(scoreRecordService.getById(277L)).thenReturn(scoreInCourse(MY_COURSE));
        when(courseService.getById(MY_COURSE)).thenReturn(courseOwnedBy(TEACHER_18));

        ApiResponse<Object> res = controller.updateScore(277L, body(88.0), req("18", "2"));

        assertEquals(200, res.getCode());
        verify(scoreRecordService).updateScore(277L, 88.0, null, "教师");
    }

    @Test
    @DisplayName("管理员（X-Role: 4）改任何课的成绩 ⇒ 放行，不查课程")
    void adminCanUpdateAnyScore() {
        when(scoreRecordService.getById(277L)).thenReturn(scoreInCourse(OTHERS_COURSE));

        ApiResponse<Object> res = controller.updateScore(277L, body(90.0), req("1", "4"));

        assertEquals(200, res.getCode());
        verify(scoreRecordService).updateScore(277L, 90.0, null, "教师");
        verify(courseService, never()).getById(any());
    }

    /**
     * 负向对照：记录不存在时**不会**被守卫改写成 403 —— 走的是显式的 404
     * （"这一行不存在"与"这一行不是你的"是两件事，混成一个会让调用方无法分辨）。
     * 少了这条，把守卫写成 {@code record == null ⇒ 403} 也会全绿。
     */
    @Test
    @DisplayName("记录不存在 ⇒ 404，且不写库（不把'不存在'吞成 403，也不假装成功）")
    void missingRecordIsNotSwallowedByTheGuard() {
        when(scoreRecordService.getById(999L)).thenReturn(null);

        ApiResponse<Object> res = controller.updateScore(999L, body(70.0), req("18", "2"));

        assertEquals(404, res.getCode());
        verify(scoreRecordService, never()).updateScore(any(), any(), any(), any());
    }

    /**
     * 新负向对照（替代已失效的"记录没有 teacherId"那条）：记录在、但它所属的课程
     * **查不到** ⇒ 拒绝。归属无从谈起时不能猜，也不能放行。
     */
    @Test
    @DisplayName("记录所属课程查不到 ⇒ 拒绝（归属无从谈起时不猜、不放行）")
    void recordWhoseCourseIsMissingIsRejected() {
        when(scoreRecordService.getById(277L)).thenReturn(scoreInCourse(OTHERS_COURSE));
        when(courseService.getById(OTHERS_COURSE)).thenReturn(null);

        ApiResponse<Object> res = controller.updateScore(277L, body(60.0), req("18", "2"));

        assertEquals(403, res.getCode());
        verify(scoreRecordService, never()).updateScore(any(), any(), any(), any());
    }
}
