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
 * 学生自助面**读端点**的归属闸（W11，2026-10-04）。
 *
 * <p><b>修之前是什么样</b>：{@code StudentController} 65 个映射只有 1 处读登录身份，
 * 读端点一律"客户端给什么 id 就读什么 id"。全量扫描实测（经网关 8076，只读）
 * 82 条归属人型端点里 **32 条越权、0 条被拦**，学生侧占 21 条 —— 其中
 * {@code GET /api/students/scores/30} 返回同学邱琳紫的整份成绩单（10 条，
 * 结构签名与她自己看自己**逐字一致**）。
 *
 * <p><b>与写侧（W10）同一套帮手</b>：{@code denyUnlessSelf}（users.id 空间）与
 * {@code denyUnlessSelfProfile}（student_profile.id 空间）。两套 id 不相交
 * （司明 users.id=27 / student_profile.id=3）—— 用错帮手的后果是"本人也被拦"
 * 或"拿 users.id 反而放行"，所以下面各钉一条。
 *
 * <p>判据形状：不只看返回码，还要 {@code verify(..., never())} —— <b>那次读压根不该发生</b>。
 * 读端点即使返回 403 信封，只要服务层被调用了，就说明数据已经从库里取出来过
 * （"先查了再报错"同样是泄漏）。
 */
@ExtendWith(MockitoExtension.class)
class StudentControllerReadOwnershipTest {

    @Mock private StudentService studentService;

    @InjectMocks private StudentController controller;

    /** 司明：users.id=27、student_profile.id=3。 */
    private static final long ME = 27L;
    private static final long MY_PROFILE = 3L;
    /** 同学邱琳紫：users.id=30、student_profile.id=4。 */
    private static final long OTHER = 30L;
    private static final long OTHER_PROFILE = 4L;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /** 把 SecurityContext 装成"网关注入的已验签身份"（GatewayAuthFilter 就是这么放的）。 */
    private void loginAs(long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private HttpServletRequest req(String role) {
        HttpServletRequest r = mock(HttpServletRequest.class);
        lenient().when(r.getHeader("X-Role")).thenReturn(role);
        return r;
    }

    // ================================================== users.id 空间

    @Test
    @DisplayName("读本人的成绩 ⇒ 放行（闸不误伤）")
    void ownScoresAllowed() {
        loginAs(ME);
        when(studentService.getStudentScores(ME, null)).thenReturn(List.of(Map.of("id", 1)));

        ApiResponse<Object> res = controller.getStudentScores(String.valueOf(ME), null, req("1"));

        assertEquals(200, res.getCode());
        verify(studentService).getStudentScores(ME, null);
    }

    @Test
    @DisplayName("★ 读同学的成绩 ⇒ 403，且**服务层一次都没被调用**（不是「先查了再报错」）")
    void otherStudentsScoresRejected() {
        loginAs(ME);

        ApiResponse<Object> res = controller.getStudentScores(String.valueOf(OTHER), null, req("1"));

        assertEquals(403, res.getCode(), "换个 users.id 就能读到同学整份成绩单，是本轮要收口的那条");
        verify(studentService, never()).getStudentScores(anyLong(), any());
    }

    @Test
    @DisplayName("读别人的看板 / 预警 / 班级排名 / 未读数 ⇒ 一律 403 且不触库")
    void otherStudentsOtherReadsRejected() {
        loginAs(ME);

        assertEquals(403, controller.getStudentDashboard(String.valueOf(OTHER), req("1")).getCode());
        assertEquals(403, controller.getStudentWarnings(String.valueOf(OTHER), req("1")).getCode());
        assertEquals(403, controller.getClassRanking(String.valueOf(OTHER), req("1")).getCode());
        assertEquals(403, controller.getNotificationUnreadCount(String.valueOf(OTHER), req("1")).getCode());
        assertEquals(403, controller.getLatestBenchmark(String.valueOf(OTHER), req("1")).getCode());

        verify(studentService, never()).getStudentDashboard(anyLong());
        verify(studentService, never()).getStudentWarnings(anyLong());
        verify(studentService, never()).getBenchmarkClassRanking(anyLong(), any(), any());
    }

    @Test
    @DisplayName("管理员（X-Role: 4）读任意学生 ⇒ 旁路放行（后台代查不断）")
    void adminBypassesReadGuard() {
        loginAs(1L);
        when(studentService.getStudentScores(OTHER, null)).thenReturn(List.of());

        ApiResponse<Object> res = controller.getStudentScores(String.valueOf(OTHER), null, req("4"));

        assertEquals(200, res.getCode());
        verify(studentService).getStudentScores(OTHER, null);
    }

    @Test
    @DisplayName("没有登录身份（认不出人）⇒ 拒绝，fail-closed")
    void noIdentityRejected() {
        ApiResponse<Object> res = controller.getStudentScores(String.valueOf(ME), null, req("1"));

        assertEquals(403, res.getCode());
        verify(studentService, never()).getStudentScores(anyLong(), any());
    }

    // ================================================== student_profile.id 空间

    @Test
    @DisplayName("★ 订阅族：拿自己的**档案 id(3)** 读 ⇒ 放行（空间选对了才放得过）")
    void ownSubscriptionAllowedByProfileId() {
        loginAs(ME);
        when(studentService.getProfileIdByUserId(ME)).thenReturn(MY_PROFILE);
        when(studentService.getSubscriptionPreferences(MY_PROFILE)).thenReturn(Map.of("success", true));

        ApiResponse<Object> res = controller.getSubscriptionPreferences(String.valueOf(MY_PROFILE), req("1"));

        assertEquals(200, res.getCode());
        verify(studentService).getSubscriptionPreferences(MY_PROFILE);
    }

    @Test
    @DisplayName("★ 订阅族：拿**自己的 users.id(27)** 提交 ⇒ 必须 403（27 不是司明的档案 id）")
    void subscriptionGuardMustCompareProfileIdNotUserId() {
        loginAs(ME);
        when(studentService.getProfileIdByUserId(ME)).thenReturn(MY_PROFILE);

        ApiResponse<Object> res = controller.getSubscriptionPreferences(String.valueOf(ME), req("1"));

        assertEquals(403, res.getCode(), "27 不是司明的档案 id(3) ⇒ 必须拒；放了就是守卫选错了 id 空间");
        verify(studentService, never()).getSubscriptionPreferences(anyLong());
    }

    @Test
    @DisplayName("订阅族：读同学的档案 ⇒ 403 且不触库")
    void otherStudentSubscriptionRejected() {
        loginAs(ME);
        when(studentService.getProfileIdByUserId(ME)).thenReturn(MY_PROFILE);

        ApiResponse<Object> res = controller.getSubscriptionPreferences(String.valueOf(OTHER_PROFILE), req("1"));

        assertEquals(403, res.getCode());
        verify(studentService, never()).getSubscriptionPreferences(anyLong());
    }

    @Test
    @DisplayName("★ 评价族也收 student_profile.id —— 与订阅族同一条判据")
    void evaluationsAlsoUseProfileIdSpace() {
        loginAs(ME);
        when(studentService.getProfileIdByUserId(ME)).thenReturn(MY_PROFILE);

        assertEquals(403, controller.getEvaluationsByStudent(String.valueOf(ME), req("1")).getCode(),
                "users.id(27) 不是档案 id ⇒ 评价族也该拒");
        verify(studentService, never()).getEvaluationsByStudent(anyLong());
    }
}
