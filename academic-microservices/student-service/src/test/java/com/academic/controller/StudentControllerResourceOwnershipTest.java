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
 * 学生自助面**资源 id 型**端点的归属闸（W12，2026-10-04）。
 *
 * <p><b>与 W11 那一轮的区别</b>：W11 收的是「属主 id 型」参数 —— 路径里直接给的就是
 * {@code users.id} / {@code student_profile.id}，比一次就完事。这一族不一样：
 * 路径里给的是一个**东西的 id**（一条申诉 6、一条通知 1001、一份帮扶计划 1001），
 * "它是不是我的"必须**按 id 查一次库**才知道。所以闸门长成"先问 service 这条资源
 * 的主人是谁，再和身份比"。
 *
 * <p><b>修之前是什么样</b>（经备用端口直连实测，只读）：
 * <ul>
 *   <li>{@code GET /students/appeals/6/detail} —— 拿邱琳紫（users.id=30）的身份去读
 *       <b>司明的</b>申诉 6，HTTP 200 + 整份载荷，里面明明白白写着 {@code "studentId":3}；
 *   <li>{@code POST /students/notification-center/{id}/mark-read|delete} ——
 *       {@code markAsRead(id)} / {@code softDelete(id)} 的 SQL 里**没有 user_id 条件**，
 *       谁都能把别人的通知标已读、软删；</li>
 *   <li>{@code GET /students/evaluations/{planId}/detail} —— 同样按 planId 直取。</li>
 * </ul>
 *
 * <p><b>三处 id 空间不同，本类各钉一条判据</b>：申诉与帮扶计划的属主存的是
 * {@code student_profile.id}（司明=3），通知的属主存的是 {@code users.id}（司明=27）。
 * 这两套 id 不相交 —— 混用的后果是"本人的请求也被拦"或"拦不住"，两个方向都**不报错**。
 *
 * <p><b>判据形状</b>：不只看返回码，还要 {@code verify(..., never())} ——
 * 那次读/写压根不该发生。返回 403 信封但服务层被调用过，仍然是泄漏。
 */
@ExtendWith(MockitoExtension.class)
class StudentControllerResourceOwnershipTest {

    @Mock private StudentService studentService;

    @InjectMocks private StudentController controller;

    /** 司明：users.id=27、student_profile.id=3。 */
    private static final long ME = 27L;
    private static final long MY_PROFILE = 3L;
    /** 同学邱琳紫：users.id=30、student_profile.id=4。 */
    private static final long OTHER = 30L;
    private static final long OTHER_PROFILE = 4L;

    /** 活库里的真值：申诉 6、帮扶计划 1001 都是司明的（档案 3）；通知 1001 是司明的（users 27）。 */
    private static final long MY_APPEAL = 6L;
    private static final long MY_PLAN = 1001L;
    private static final long MY_NOTIFICATION = 1001L;
    /** 不属于司明的同表资源：帮扶计划 1 属于档案 13；通知 1 属于 users 39。 */
    private static final long OTHERS_PLAN = 1L;
    private static final long OTHERS_NOTIFICATION = 1L;

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

    // ================================================== 申诉（student_profile.id 空间）

    @Test
    @DisplayName("读本人的申诉详情 ⇒ 放行（闸不误伤）")
    void ownAppealAllowed() {
        loginAs(ME);
        when(studentService.getProfileIdByUserId(ME)).thenReturn(MY_PROFILE);
        when(studentService.getAppealOwnerProfileId(MY_APPEAL)).thenReturn(MY_PROFILE);
        when(studentService.getAppealDetail(MY_APPEAL)).thenReturn(Map.of("id", MY_APPEAL));

        ApiResponse<Object> res = controller.getAppealDetail(MY_APPEAL, req("1"));

        assertEquals(200, res.getCode());
        verify(studentService).getAppealDetail(MY_APPEAL);
    }

    @Test
    @DisplayName("★ 读**同学**的申诉详情 ⇒ 403，且服务层一次都没被调用（不是「先查了再报错」）")
    void othersAppealRejected() {
        loginAs(OTHER);
        when(studentService.getProfileIdByUserId(OTHER)).thenReturn(OTHER_PROFILE);
        // 申诉 6 挂在司明的档案 3 上
        when(studentService.getAppealOwnerProfileId(MY_APPEAL)).thenReturn(MY_PROFILE);

        ApiResponse<Object> res = controller.getAppealDetail(MY_APPEAL, req("1"));

        assertEquals(403, res.getCode(), "修之前这条返回 200 + 整份申诉载荷（含 studentId:3）");
        verify(studentService, never()).getAppealDetail(anyLong());
    }

    @Test
    @DisplayName("申诉不存在 ⇒ 也 403（不区分「没这条」与「不是你的」，别做存在性预言）")
    void missingAppealRejected() {
        loginAs(ME);
        when(studentService.getProfileIdByUserId(ME)).thenReturn(MY_PROFILE);
        when(studentService.getAppealOwnerProfileId(999999L)).thenReturn(null);

        ApiResponse<Object> res = controller.getAppealDetail(999999L, req("1"));

        assertEquals(403, res.getCode());
        verify(studentService, never()).getAppealDetail(anyLong());
    }

    @Test
    @DisplayName("★ 空间选错的负向对照：拿 users.id 当档案 id 去比 ⇒ **不会**误放行")
    void wrongIdSpaceDoesNotLeak() {
        // 登录人是邱琳紫（users.id=30、档案 4），资源属主是档案 **30**（不存在的人，纯数字巧合）。
        // 如果闸门把 users.id 与档案 id 混着比，这里就会放行。真实现里 30 != 4 ⇒ 必拒。
        loginAs(OTHER);
        when(studentService.getProfileIdByUserId(OTHER)).thenReturn(OTHER_PROFILE);
        when(studentService.getAppealOwnerProfileId(MY_APPEAL)).thenReturn(OTHER);   // 属主 = 数字 30

        ApiResponse<Object> res = controller.getAppealDetail(MY_APPEAL, req("1"));

        assertEquals(403, res.getCode(), "属主 30 与登录人档案 4 不相等，必须是 403");
        verify(studentService, never()).getAppealDetail(anyLong());
    }

    // ================================================== 通知（users.id 空间）

    @Test
    @DisplayName("标记本人的通知已读 ⇒ 放行")
    void ownNotificationMarkReadAllowed() {
        loginAs(ME);
        when(studentService.getNotificationOwnerUserId(MY_NOTIFICATION)).thenReturn(ME);
        when(studentService.markNotificationRead(MY_NOTIFICATION)).thenReturn(Map.of("affected", 1));

        ApiResponse<Object> res = controller.markSingleNotificationRead(MY_NOTIFICATION, req("1"));

        assertEquals(200, res.getCode());
        verify(studentService).markNotificationRead(MY_NOTIFICATION);
    }

    @Test
    @DisplayName("★ 标记**别人的**通知已读 ⇒ 403 且不触库（markAsRead 的 SQL 里没有 user_id 条件）")
    void othersNotificationMarkReadRejected() {
        loginAs(ME);
        when(studentService.getNotificationOwnerUserId(OTHERS_NOTIFICATION)).thenReturn(39L);

        ApiResponse<Object> res = controller.markSingleNotificationRead(OTHERS_NOTIFICATION, req("1"));

        assertEquals(403, res.getCode());
        verify(studentService, never()).markNotificationRead(anyLong());
    }

    @Test
    @DisplayName("★ 删除**别人的**通知 ⇒ 403 且不触库（原来谁都能软删别人的通知）")
    void othersNotificationDeleteRejected() {
        loginAs(ME);
        when(studentService.getNotificationOwnerUserId(OTHERS_NOTIFICATION)).thenReturn(39L);

        ApiResponse<Object> res = controller.deleteNotification(OTHERS_NOTIFICATION, req("1"));

        assertEquals(403, res.getCode());
        verify(studentService, never()).deleteNotification(anyLong());
    }

    @Test
    @DisplayName("删除本人的通知 ⇒ 放行")
    void ownNotificationDeleteAllowed() {
        loginAs(ME);
        when(studentService.getNotificationOwnerUserId(MY_NOTIFICATION)).thenReturn(ME);
        when(studentService.deleteNotification(MY_NOTIFICATION)).thenReturn(Map.of("success", true));

        ApiResponse<Object> res = controller.deleteNotification(MY_NOTIFICATION, req("1"));

        assertEquals(200, res.getCode());
        verify(studentService).deleteNotification(MY_NOTIFICATION);
    }

    @Test
    @DisplayName("★ 通知族认的是 users.id：拿我**自己的 profile.id(3)** 当 user_id ⇒ 不是同一人，不放行")
    void notificationGuardUsesUserIdNotProfileId() {
        // 登录人司明 users.id=27。若闸门错用 getProfileIdByUserId 得到 3 去比，
        // 那么一条恰好属于 users 3 的通知就会被放行 —— 这里把属主设成 3 钉住它。
        loginAs(ME);
        // ⚠️ 这一句必须 lenient：**正确实现根本不查档案**。不这么写会被 Mockito 的
        // 严格模式判成 UnnecessaryStubbing 而报错；而如果写成 when(...) 又会因为
        // 未打桩时 Mockito 对 Long 返回 null（而不是 0），让"空间选错"这个 bug
        // 恰好也被拒掉 —— 这条判据就成了打不红的样子货（实测过：靶 C 下它不红）。
        lenient().when(studentService.getProfileIdByUserId(ME)).thenReturn(MY_PROFILE);
        when(studentService.getNotificationOwnerUserId(MY_NOTIFICATION)).thenReturn(MY_PROFILE);

        ApiResponse<Object> res = controller.markSingleNotificationRead(MY_NOTIFICATION, req("1"));

        assertEquals(403, res.getCode(), "通知属主是 users.id 空间，27 != 3 ⇒ 必须 403");
        verify(studentService, never()).markNotificationRead(anyLong());
    }

    // ================================================== 帮扶计划（student_profile.id 空间）

    @Test
    @DisplayName("读本人帮扶计划的评价 ⇒ 放行")
    void ownPlanEvaluationAllowed() {
        loginAs(ME);
        when(studentService.getProfileIdByUserId(ME)).thenReturn(MY_PROFILE);
        when(studentService.getPlanOwnerProfileId(MY_PLAN)).thenReturn(MY_PROFILE);
        when(studentService.getPlanEvaluation(MY_PLAN)).thenReturn(List.of());

        ApiResponse<Object> res = controller.getPlanEvaluation(MY_PLAN, req("1"));

        assertEquals(200, res.getCode());
        verify(studentService).getPlanEvaluation(MY_PLAN);
    }

    @Test
    @DisplayName("★ 读**别人的**帮扶计划评价 ⇒ 403 且不触库")
    void othersPlanEvaluationRejected() {
        loginAs(ME);
        when(studentService.getProfileIdByUserId(ME)).thenReturn(MY_PROFILE);
        when(studentService.getPlanOwnerProfileId(OTHERS_PLAN)).thenReturn(13L);

        ApiResponse<Object> res = controller.getPlanEvaluation(OTHERS_PLAN, req("1"));

        assertEquals(403, res.getCode());
        verify(studentService, never()).getPlanEvaluation(anyLong());
    }

    // ================================================== 身份缺陷与旁路

    @Test
    @DisplayName("没有登录身份（认不出人）⇒ 三条资源闸都 fail-closed")
    void noIdentityRejected() {
        // 未登录：getProfileIdByUserId 那一步就不该被走到（me == null 直接短路）
        assertEquals(403, controller.getAppealDetail(MY_APPEAL, req("1")).getCode());
        assertEquals(403, controller.markSingleNotificationRead(MY_NOTIFICATION, req("1")).getCode());
        assertEquals(403, controller.getPlanEvaluation(MY_PLAN, req("1")).getCode());

        verify(studentService, never()).getAppealDetail(anyLong());
        verify(studentService, never()).markNotificationRead(anyLong());
        verify(studentService, never()).getPlanEvaluation(anyLong());
    }

    @Test
    @DisplayName("降级（带 Sentinel 的那份兜底实现回 null）⇒ 拒绝，不会把「下游挂了」变成「资源是你的」")
    void fallbackNullOwnerIsDenied() {
        loginAs(ME);
        when(studentService.getProfileIdByUserId(ME)).thenReturn(MY_PROFILE);
        // StudentServiceFallback 这三个方法就是 return null —— fail-closed 的落点
        when(studentService.getAppealOwnerProfileId(MY_APPEAL)).thenReturn(null);
        when(studentService.getPlanOwnerProfileId(MY_PLAN)).thenReturn(null);
        when(studentService.getNotificationOwnerUserId(MY_NOTIFICATION)).thenReturn(null);

        assertEquals(403, controller.getAppealDetail(MY_APPEAL, req("1")).getCode());
        assertEquals(403, controller.getPlanEvaluation(MY_PLAN, req("1")).getCode());
        assertEquals(403, controller.markSingleNotificationRead(MY_NOTIFICATION, req("1")).getCode());

        verify(studentService, never()).getAppealDetail(anyLong());
        verify(studentService, never()).getPlanEvaluation(anyLong());
        verify(studentService, never()).markNotificationRead(anyLong());
    }

    @Test
    @DisplayName("管理员（X-Role: 4）读任意资源 ⇒ 旁路放行（后台代查不断）")
    void adminBypassesResourceGuard() {
        loginAs(1L);
        when(studentService.getAppealDetail(MY_APPEAL)).thenReturn(Map.of("id", MY_APPEAL));
        when(studentService.getPlanEvaluation(OTHERS_PLAN)).thenReturn(List.of());
        when(studentService.markNotificationRead(OTHERS_NOTIFICATION)).thenReturn(Map.of("affected", 1));

        assertEquals(200, controller.getAppealDetail(MY_APPEAL, req("4")).getCode());
        assertEquals(200, controller.getPlanEvaluation(OTHERS_PLAN, req("4")).getCode());
        assertEquals(200, controller.markSingleNotificationRead(OTHERS_NOTIFICATION, req("4")).getCode());
    }

    @Test
    @DisplayName("闸门在查库**之前**：拒绝路径上不该问「这条资源是谁的」以外的东西")
    void guardQueriesOwnerOnly() {
        // 「先查了再报错」同样是泄漏 —— 这里钉住被拒时只发生了一次归属查询，
        // 真正的取数方法（getAppealDetail / getPlanEvaluation）一次都没碰。
        loginAs(OTHER);
        when(studentService.getProfileIdByUserId(OTHER)).thenReturn(OTHER_PROFILE);
        when(studentService.getAppealOwnerProfileId(MY_APPEAL)).thenReturn(MY_PROFILE);

        controller.getAppealDetail(MY_APPEAL, req("1"));

        verify(studentService, times(1)).getAppealOwnerProfileId(MY_APPEAL);
        verify(studentService, never()).getAppealDetail(anyLong());
    }
}
