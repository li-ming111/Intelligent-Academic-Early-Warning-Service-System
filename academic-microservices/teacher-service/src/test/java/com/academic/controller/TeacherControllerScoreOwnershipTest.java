package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.common.entity.ScoreRecord;
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
 * {@code PUT /teachers/scores/{scoreId}} 的归属闸（W10，2026-10-03）。
 *
 * <p><b>修之前是什么样</b>：这个端点只有 {@code scoreId} 一个入参，连"谁在改"都不收，
 * 而**同一个资源**的 {@code DELETE /teachers/scores/{scoreId}} 一直有归属闸 ——
 * 同一个 id、两个动词、两套口径。实测（经网关 8076）：教师 18 用
 * {@code PUT /teachers/scores/{29 名下的记录 id}} 成功改掉了教师 29 录入的成绩，
 * 回 200「成绩更新成功」。成绩是这套系统里最敏感的数据，比读看板严重得多。
 *
 * <p><b>判据的形状</b>（照本仓"守卫类用例"的定式）：不只是断言返回值，
 * 还要 {@code verify(..., never()).updateScore(...)} —— <b>那次写压根不该发生</b>。
 * 只看返回码会被"先改了再报错"骗过。
 *
 * <p>另外钉一条**负向对照**：记录不存在时守卫必须**放行**（把"查不到"交给 service 去抛），
 * 否则就是把"不存在"和"不是你的"合并成同一个拒——那会把既有行为也改掉。
 */
@ExtendWith(MockitoExtension.class)
class TeacherControllerScoreOwnershipTest {

    @Mock private ScoreRecordService scoreRecordService;

    @InjectMocks private TeacherController controller;

    /** teacher001：users.id=18。 */
    private static final long TEACHER_18 = 18L;
    /** 10001：users.id=29，活库里司明那批成绩的录入人。 */
    private static final long TEACHER_29 = 29L;

    private HttpServletRequest req(String userId, String role) {
        HttpServletRequest r = mock(HttpServletRequest.class);
        lenient().when(r.getHeader("X-User-Id")).thenReturn(userId);
        lenient().when(r.getHeader("X-Role")).thenReturn(role);
        return r;
    }

    private static ScoreRecord recordOwnedBy(long teacherId) {
        ScoreRecord rec = new ScoreRecord();
        rec.setId(277L);
        rec.setTeacherId(teacherId);
        return rec;
    }

    private static Map<String, Object> body(double score) {
        Map<String, Object> m = new HashMap<>();
        m.put("score", score);
        return m;
    }

    // ---------------------------------------------------------------- 拒绝

    @Test
    @DisplayName("改别人录入的成绩 ⇒ body code 403，且一行都不写")
    void updatingAnotherTeachersScoreIsRejected() {
        when(scoreRecordService.getById(277L)).thenReturn(recordOwnedBy(TEACHER_29));

        ApiResponse<Object> res = controller.updateScore(277L, body(99.0), req("18", "2"));

        assertEquals(403, res.getCode(), "必须是归属拒绝，不是通用失败");
        // ⭐ 真正的判据：那次写不该发生
        verify(scoreRecordService, never()).updateScore(any(), any(), any(), any());
    }

    @Test
    @DisplayName("坏 token（没有 X-User-Id）⇒ 同样拒绝，不放行")
    void missingIdentityHeaderIsRejected() {
        when(scoreRecordService.getById(277L)).thenReturn(recordOwnedBy(TEACHER_29));

        ApiResponse<Object> res = controller.updateScore(277L, body(99.0), req(null, "2"));

        assertEquals(403, res.getCode());
        verify(scoreRecordService, never()).updateScore(any(), any(), any(), any());
    }

    // ---------------------------------------------------------------- 放行

    @Test
    @DisplayName("改自己录入的成绩 ⇒ 正常更新（闸不误伤）")
    void updatingOwnScoreIsAllowed() {
        when(scoreRecordService.getById(277L)).thenReturn(recordOwnedBy(TEACHER_18));

        ApiResponse<Object> res = controller.updateScore(277L, body(88.0), req("18", "2"));

        assertEquals(200, res.getCode());
        verify(scoreRecordService).updateScore(277L, 88.0, null, "教师");
    }

    @Test
    @DisplayName("管理员（X-Role: 4）改任何人的成绩 ⇒ 放行")
    void adminCanUpdateAnyScore() {
        when(scoreRecordService.getById(277L)).thenReturn(recordOwnedBy(TEACHER_29));

        ApiResponse<Object> res = controller.updateScore(277L, body(90.0), req("1", "4"));

        assertEquals(200, res.getCode());
        verify(scoreRecordService).updateScore(277L, 90.0, null, "教师");
    }

    /**
     * 负向对照：记录不存在时**不能**被守卫拦下 —— "查不到"是 service 的事，
     * 守卫只回答"是不是你的"。少了这条，把守卫写成 `record == null ⇒ 403`
     * 也会全绿，而那会改掉既有的"不存在"语义。
     */
    @Test
    @DisplayName("记录不存在 ⇒ 守卫放行，交给 service（不把'不存在'吞成 403）")
    void missingRecordIsNotSwallowedByTheGuard() {
        when(scoreRecordService.getById(999L)).thenReturn(null);

        ApiResponse<Object> res = controller.updateScore(999L, body(70.0), req("18", "2"));

        assertEquals(200, res.getCode(), "service 正常返回时不应被守卫改写成 403");
        verify(scoreRecordService).updateScore(999L, 70.0, null, "教师");
    }

    /** 录入人为空的记录（历史数据）同样不该被守卫拦 —— 守卫只比对**已知**的归属。 */
    @Test
    @DisplayName("记录没有 teacherId ⇒ 守卫放行（不猜归属）")
    void recordWithoutTeacherIdIsAllowed() {
        ScoreRecord rec = new ScoreRecord();
        rec.setId(277L);
        rec.setTeacherId(null);
        when(scoreRecordService.getById(277L)).thenReturn(rec);

        ApiResponse<Object> res = controller.updateScore(277L, body(60.0), req("18", "2"));

        assertEquals(200, res.getCode());
        verify(scoreRecordService).updateScore(277L, 60.0, null, "教师");
    }
}
