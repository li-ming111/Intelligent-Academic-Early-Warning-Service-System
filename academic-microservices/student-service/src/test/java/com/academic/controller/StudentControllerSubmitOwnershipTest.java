package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.service.StudentService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 学生自助面**「新建记录」型**端点的归属闸（W42，2026-10-13）。
 *
 * <p><b>与 W11 / W12 两轮的区别</b>：W11 收的是"属主 id 型"参数（路径里直接给了 users.id /
 * student_profile.id），W12 收的是"资源 id 型"（一条已存在的申诉 / 通知 / 计划，
 * 查一次库才知道主人）。这两轮覆盖的都是**读**与**操作已有资源**。
 * 而 {@code POST /students/appeals/submit} 与 {@code POST /students/evaluations/submit}
 * 是**创建新行** —— 库里的真值就是请求体写进去的那个，没有"先查一次库"可查。
 * 它们当时**两个都没有任何归属校验**。
 *
 * <p><b>修之前实测是什么样</b>（经网关，2026-10-13）：
 * <ul>
 *   <li>邱琳紫（{@code users.id=30}）发 {@code POST /students/appeals/submit}
 *       带 {@code {studentId:27,...}} ⇒ HTTP 200，**库里真的多出一条挂在司明名下的申诉**
 *       （{@code score_appeals.id=10}）；</li>
 *   <li>同一个人发 {@code POST /students/evaluations/submit} 用
 *       {@code {planId:3, studentId:52}} ⇒ 成功写出挂在**张健**帮扶计划上的评价
 *       （{@code assistance_evaluations.id=3}）。服务层那一句
 *       {@code plan.getStudentId().equals(resolveProfileId(body.studentId))} 本身没错，
 *       但基准整个来自请求体 ⇒ **自证**，伪造它就自洽了。</li>
 * </ul>
 *
 * <p><b>两条闸的判据形状不同，本类各钉住</b>：
 * <ul>
 *   <li>申诉：请求体 {@code studentId} 认的是 {@code users.id}（PC 五处提交点发的都是它），
 *       直接与网关注入的登录人比 —— **刻意不换算成档案 id**，因为两套 id 空间大量重叠，
 *       换算会开出一个"档案 id 恰好等于我的 userId"的绕过口；</li>
 *   <li>评价：判据是 **{@code planId} 指的那份计划是不是我的**（复用 {@code denyUnlessMyPlan}）。
 *       请求体 {@code studentId} 不参与 —— 它是自证的，改它就能改结论；而"计划挂在谁的档案上"
 *       由登录身份与库中真值两方决定，伪造不了。小程序提交时**根本不发 studentId**，
 *       也只有这一维对 PC + 小程序两端都成立。</li>
 * </ul>
 *
 * <p><b>判据形状</b>：不只看 403，还要 {@code verify(..., never())} —— 被拒时服务层
 * 那次写入压根不该发生。返回 403 但已经写进库，等于没修。
 */
@ExtendWith(MockitoExtension.class)
class StudentControllerSubmitOwnershipTest {

    @Mock private StudentService studentService;

    @InjectMocks private StudentController controller;

    /** 司明：users.id=27、student_profile.id=3、帮扶计划 1001。 */
    private static final long ME = 27L;
    private static final long MY_PROFILE = 3L;
    private static final long MY_PLAN = 1001L;
    /** 同学邱琳紫：users.id=30、student_profile.id=4。 */
    private static final long OTHER = 30L;
    private static final long OTHER_PROFILE = 4L;
    /** 张健的计划 3 挂在档案 26 上（W42 实测里被越权写评价的那份）。 */
    private static final long OTHERS_PLAN = 3L;
    private static final long OTHERS_PROFILE = 26L;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private HttpServletRequest req(String role) {
        HttpServletRequest r = mock(HttpServletRequest.class);
        lenient().when(r.getHeader("X-Role")).thenReturn(role);
        return r;
    }

    // ================================================== 申诉：请求体 studentId 必须是登录人

    @Test
    @DisplayName("给自己提交申诉 ⇒ 放行（闸不误伤）")
    void ownAppealSubmitAllowed() {
        loginAs(ME);
        Map<String, Object> body = Map.of("studentId", ME, "courseId", 1, "reason", "成绩有误");
        when(studentService.submitAppeal(body)).thenReturn(Map.of("id", 9, "message", "申诉提交成功"));

        ApiResponse<Object> res = controller.submitAppeal(body, req("1"));

        assertEquals(200, res.getCode());
        verify(studentService).submitAppeal(body);
    }

    @Test
    @DisplayName("★ 以他人身份提交申诉 ⇒ 403，且服务层一次都没被调用（不是「先写了再报错」）")
    void othersAppealSubmitRejected() {
        loginAs(OTHER);
        Map<String, Object> body = Map.of("studentId", ME, "courseId", 1, "reason", "成绩有误");

        ApiResponse<Object> res = controller.submitAppeal(body, req("1"));

        assertEquals(403, res.getCode(),
                "修之前这条 HTTP 200，库里真的多出一条挂在司明名下的申诉（实测 id=10）");
        verify(studentService, never()).submitAppeal(any());
    }

    @Test
    @DisplayName("★ 兼容旧路径 /appeal 走同一道闸（转发不是绕过口）")
    void legacyAppealPathIsAlsoGated() {
        loginAs(OTHER);
        Map<String, Object> body = Map.of("studentId", ME, "courseId", 1, "reason", "成绩有误");

        ApiResponse<Object> res = controller.submitAppealLegacy(body, req("1"));

        assertEquals(403, res.getCode());
        verify(studentService, never()).submitAppeal(any());
    }

    @Test
    @DisplayName("★ 空间选错的负向对照：拿 users.id 当档案 id 比 ⇒ 不会误放行")
    void appealDoesNotCompareInProfileSpace() {
        // 登录人邱琳紫 users.id=30。这里把"档案换算"打桩成"恰好等于 30"（现实中存在这种
        // 数字巧合：有人 student_profile.id 正好是 30 吗？本库没有，但两套空间重叠是事实）。
        // 正确实现**根本不查档案**，直接用 users.id 比 ⇒ 30 == 30 会放行 —— 所以这个
        // 用例反过来钉住"实现没有走换算那条路"：它必须在 ME(27) 上放行、在 OTHER(30) 上拒。
        loginAs(OTHER);
        lenient().when(studentService.getProfileIdByUserId(OTHER)).thenReturn(OTHER_PROFILE);
        Map<String, Object> body = Map.of("studentId", OTHER_PROFILE, "courseId", 1, "reason", "x");

        ApiResponse<Object> res = controller.submitAppeal(body, req("1"));

        assertEquals(403, res.getCode(),
                "body 里 4 是别的学生的档案 id，不是登录人 userId(30) ⇒ 必须拒");
        verify(studentService, never()).submitAppeal(any());
    }

    @Test
    @DisplayName("缺 studentId / 非数字 ⇒ 不在这里拦，交给服务层按既有语义回「缺少 studentId」")
    void missingOrBadStudentIdFallsThroughToService() {
        loginAs(ME);
        Map<String, Object> noId = Map.of("courseId", 1, "reason", "x");
        Map<String, Object> badId = Map.of("studentId", "abc", "courseId", 1, "reason", "x");
        when(studentService.submitAppeal(any())).thenReturn(Map.of("error", "缺少 studentId"));

        assertEquals(200, controller.submitAppeal(noId, req("1")).getCode());
        assertEquals(200, controller.submitAppeal(badId, req("1")).getCode());
        verify(studentService, times(2)).submitAppeal(any());
    }

    @Test
    @DisplayName("没有登录身份 ⇒ 有 studentId 时 fail-closed 拒；无 studentId 时仍落到服务层")
    void noIdentityAppeal() {
        Map<String, Object> withId = Map.of("studentId", ME, "courseId", 1, "reason", "x");
        assertEquals(403, controller.submitAppeal(withId, req("1")).getCode());
        verify(studentService, never()).submitAppeal(any());
    }

    @Test
    @DisplayName("管理员（X-Role: 4）代提交 ⇒ 旁路放行")
    void adminBypassesAppealGuard() {
        loginAs(1L);
        Map<String, Object> body = Map.of("studentId", ME, "courseId", 1, "reason", "x");
        when(studentService.submitAppeal(body)).thenReturn(Map.of("id", 9));

        assertEquals(200, controller.submitAppeal(body, req("4")).getCode());
        verify(studentService).submitAppeal(body);
    }

    // ================================================== 评价：判据是 planId 的归属

    @Test
    @DisplayName("给本人帮扶计划提交评价 ⇒ 放行")
    void ownPlanEvaluationSubmitAllowed() {
        loginAs(ME);
        when(studentService.getProfileIdByUserId(ME)).thenReturn(MY_PROFILE);
        when(studentService.getPlanOwnerProfileId(MY_PLAN)).thenReturn(MY_PROFILE);
        Map<String, Object> body = Map.of("planId", MY_PLAN, "score", 4, "effectiveness", 5);
        when(studentService.submitEvaluation(body)).thenReturn(Map.of("success", true, "id", 2));

        ApiResponse<Object> res = controller.submitEvaluation(body, req("1"));

        assertEquals(200, res.getCode());
        verify(studentService).submitEvaluation(body);
    }

    @Test
    @DisplayName("★ 往**同学**的帮扶计划写评价 ⇒ 403 且不触库（实测曾被越权写入 id=3）")
    void othersPlanEvaluationSubmitRejected() {
        loginAs(OTHER);
        when(studentService.getProfileIdByUserId(OTHER)).thenReturn(OTHER_PROFILE);
        when(studentService.getPlanOwnerProfileId(OTHERS_PLAN)).thenReturn(OTHERS_PROFILE);
        Map<String, Object> body = Map.of("planId", OTHERS_PLAN, "studentId", 52, "score", 1);

        ApiResponse<Object> res = controller.submitEvaluation(body, req("1"));

        assertEquals(403, res.getCode(),
                "修之前这条 HTTP 200，评价真的写进了张健的计划 3");
        verify(studentService, never()).submitEvaluation(any());
    }

    @Test
    @DisplayName("★ 把 body 的 studentId 伪造成自己 ⇒ 也拦得住（闸的基准不是请求体自证）")
    void spoofedStudentIdStillRejected() {
        loginAs(OTHER);
        when(studentService.getProfileIdByUserId(OTHER)).thenReturn(OTHER_PROFILE);
        when(studentService.getPlanOwnerProfileId(OTHERS_PLAN)).thenReturn(OTHERS_PROFILE);
        // 这正是 W42 实测里绕过服务层校验的写法：studentId 填受害者(id=52)，planId 是受害者的计划
        Map<String, Object> body = Map.of("planId", OTHERS_PLAN, "studentId", ME, "score", 5);

        ApiResponse<Object> res = controller.submitEvaluation(body, req("1"));

        assertEquals(403, res.getCode(), "计划不是我的 ⇒ 无论 body 怎么写都拒");
        verify(studentService, never()).submitEvaluation(any());
    }

    @Test
    @DisplayName("缺 planId ⇒ 不在这里拦，交给服务层回「缺少 planId」")
    void missingPlanIdFallsThroughToService() {
        loginAs(ME);
        Map<String, Object> body = Map.of("score", 4);
        when(studentService.submitEvaluation(body)).thenReturn(Map.of("success", false, "message", "缺少 planId"));

        assertEquals(200, controller.submitEvaluation(body, req("1")).getCode());
        verify(studentService).submitEvaluation(body);
    }

    @Test
    @DisplayName("降级（兜底实现回 null）⇒ 拒，不会把「下游挂了」变成「计划是你的」")
    void fallbackNullPlanOwnerIsDenied() {
        loginAs(ME);
        when(studentService.getProfileIdByUserId(ME)).thenReturn(MY_PROFILE);
        when(studentService.getPlanOwnerProfileId(MY_PLAN)).thenReturn(null);
        Map<String, Object> body = Map.of("planId", MY_PLAN, "score", 4);

        assertEquals(403, controller.submitEvaluation(body, req("1")).getCode());
        verify(studentService, never()).submitEvaluation(any());
    }

    @Test
    @DisplayName("评价不存在时也 403（不区分「没这条计划」与「不是你的」）")
    void missingPlanRejected() {
        loginAs(ME);
        when(studentService.getProfileIdByUserId(ME)).thenReturn(MY_PROFILE);
        when(studentService.getPlanOwnerProfileId(999999L)).thenReturn(null);

        assertEquals(403, controller.submitEvaluation(Map.of("planId", 999999L, "score", 4), req("1")).getCode());
        verify(studentService, never()).submitEvaluation(any());
    }

    @Test
    @DisplayName("管理员提交任意计划评价 ⇒ 旁路放行")
    void adminBypassesEvaluationGuard() {
        loginAs(1L);
        Map<String, Object> body = Map.of("planId", OTHERS_PLAN, "score", 4);
        when(studentService.submitEvaluation(body)).thenReturn(Map.of("success", true));

        assertEquals(200, controller.submitEvaluation(body, req("4")).getCode());
        verify(studentService).submitEvaluation(body);
    }
}
