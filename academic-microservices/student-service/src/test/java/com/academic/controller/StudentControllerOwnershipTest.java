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
 * 学生自助面**写端点**的归属闸（W10，2026-10-03）。
 *
 * <p><b>修之前是什么样</b>：这些端点的第一参数都是客户端给的 id，服务端拿去直接写库，
 * 从不比对登录人。实测（经网关 8076）：司明（users.id 27）打
 * {@code GET /api/students/scores/30} 拿到了同学邱琳紫的**整份成绩单（10 条）**；
 * 写侧同形 —— 换个 id 就能撤销别人的申诉、改别人的通知开关与隐私档位。
 *
 * <p><b>这一族有一个非平凡的坑：两套 id 空间。</b>
 * <ul>
 *   <li>{@code /appeals/**}、{@code /settings/**}、{@code /notification-center/**}
 *       收的是 <b>users.id</b>（司明 = 27）；</li>
 *   <li>{@code /subscription/**} 收的是 <b>student_profile.id</b>（司明 = 3）——
 *       见 {@code StudentServiceImpl.updateSubscriptionPreferences} 里
 *       "这里要的是 student_profile.id"。</li>
 * </ul>
 * 拿 users.id 去比订阅族的路径参数会**永远不相等** ⇒ 把自己的请求也拦掉。
 * 所以下面专门有一条用例钉这个：**自己拿自己的 users.id 去调订阅接口，同样必须被拒**。
 *
 * <p>判据形状照本仓守卫类用例的定式：不只是断言返回码，
 * 还要 {@code verify(..., never())} —— <b>那次写压根不该发生</b>。
 */
@ExtendWith(MockitoExtension.class)
class StudentControllerOwnershipTest {

    @Mock private StudentService studentService;

    @InjectMocks private StudentController controller;

    /** 司明：users.id=27、student_profile.id=3。 */
    private static final long ME = 27L;
    private static final long MY_PROFILE = 3L;
    /** 同学邱琳紫：users.id=30、student_profile.id=4。 */
    private static final long OTHER = 30L;
    private static final long OTHER_PROFILE = 4L;

    private static final String MSG_FORBIDDEN = "只能操作本人的通知";

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

    // ================================================== 撤销申诉（users.id 空间）

    @Test
    @DisplayName("撤销别人的申诉 ⇒ 403，且不落任何写")
    void withdrawAnotherStudentsAppealIsRejected() {
        loginAs(ME);

        ApiResponse<Object> res = controller.withdrawAppeal(6L, OTHER, req("1"));

        assertEquals(403, res.getCode(), "必须是归属拒绝");
        verify(studentService, never()).withdrawAppeal(anyLong(), anyLong());
    }

    @Test
    @DisplayName("撤销自己的申诉 ⇒ 放行（闸不误伤）")
    void withdrawOwnAppealIsAllowed() {
        loginAs(ME);
        when(studentService.withdrawAppeal(6L, ME)).thenReturn(Map.of("success", true));

        ApiResponse<Object> res = controller.withdrawAppeal(6L, ME, req("1"));

        assertEquals(200, res.getCode());
        verify(studentService).withdrawAppeal(6L, ME);
    }

    @Test
    @DisplayName("没有登录身份 ⇒ 拒绝（fail-closed，不是放行）")
    void withdrawWithoutIdentityIsRejected() {
        SecurityContextHolder.clearContext();

        ApiResponse<Object> res = controller.withdrawAppeal(6L, ME, req(null));

        assertEquals(403, res.getCode());
        verify(studentService, never()).withdrawAppeal(anyLong(), anyLong());
    }

    // ================================================== 设置 / 隐私（users.id 空间）

    @Test
    @DisplayName("改别人的设置 ⇒ 403")
    void updateAnotherStudentsSettingsIsRejected() {
        loginAs(ME);

        ApiResponse<Object> res = controller.updateStudentSettings(String.valueOf(OTHER), Map.of(), req("1"));

        assertEquals(403, res.getCode());
        verify(studentService, never()).updateStudentSettings(anyLong(), any());
    }

    @Test
    @DisplayName("改自己的设置 ⇒ 放行")
    void updateOwnSettingsIsAllowed() {
        loginAs(ME);
        when(studentService.updateStudentSettings(eq(ME), any())).thenReturn(Map.of("success", true));

        ApiResponse<Object> res = controller.updateStudentSettings(String.valueOf(ME), Map.of(), req("1"));

        assertEquals(200, res.getCode());
        verify(studentService).updateStudentSettings(eq(ME), any());
    }

    @Test
    @DisplayName("改别人的隐私档位 ⇒ 403，且不落写")
    void updateAnotherStudentsPrivacyIsRejected() {
        loginAs(ME);

        ApiResponse<Object> res = controller.updatePrivacyLevel(String.valueOf(OTHER), 0, req("1"));

        assertEquals(403, res.getCode());
        verify(studentService, never()).updatePrivacyLevel(anyLong(), any());
    }

    // ================================================== 通知中心（users.id 空间）

    @Test
    @DisplayName("把别人的通知标成已读 ⇒ 403，且不落写")
    void markAnotherStudentsNotificationsIsRejected() {
        loginAs(ME);

        ApiResponse<Object> res = controller.markNotificationsBatchRead(String.valueOf(OTHER), List.of(1L), req("1"));

        assertEquals(403, res.getCode());
        assertEquals(MSG_FORBIDDEN, res.getMessage());
        verify(studentService, never()).markNotificationsBatchRead(anyLong(), any());
    }

    @Test
    @DisplayName("清空别人的已读通知 ⇒ 403，且不落写")
    void clearAnotherStudentsReadNotificationsIsRejected() {
        loginAs(ME);

        ApiResponse<Object> res = controller.clearReadNotifications(String.valueOf(OTHER), req("1"));

        assertEquals(403, res.getCode());
        verify(studentService, never()).clearReadNotifications(anyLong());
    }

    @Test
    @DisplayName("老路径把别人的通知标已读 ⇒ 403")
    void markAnotherStudentsNotificationLegacyIsRejected() {
        loginAs(ME);

        ApiResponse<Object> res = controller.markNotificationAsReadLegacy(String.valueOf(OTHER), 9L, req("1"));

        assertEquals(403, res.getCode());
        verify(studentService, never()).markNotificationsBatchRead(anyLong(), any());
    }

    // ================================================== 订阅族（★ student_profile.id 空间）

    @Test
    @DisplayName("★ 订阅族：改别人的订阅 ⇒ 403（比的是档案 id）")
    void updateAnotherStudentsSubscriptionIsRejected() {
        loginAs(ME);
        when(studentService.getProfileIdByUserId(ME)).thenReturn(MY_PROFILE);

        ApiResponse<Object> res = controller.updateSubscriptionPreferences(String.valueOf(OTHER_PROFILE), Map.of(), req("1"));

        assertEquals(403, res.getCode());
        verify(studentService, never()).updateSubscriptionPreferences(anyLong(), any());
    }

    @Test
    @DisplayName("★ 订阅族：改自己的订阅 ⇒ 放行")
    void updateOwnSubscriptionIsAllowed() {
        loginAs(ME);
        when(studentService.getProfileIdByUserId(ME)).thenReturn(MY_PROFILE);
        when(studentService.updateSubscriptionPreferences(eq(MY_PROFILE), any())).thenReturn(Map.of("success", true));

        ApiResponse<Object> res = controller.updateSubscriptionPreferences(String.valueOf(MY_PROFILE), Map.of(), req("1"));

        assertEquals(200, res.getCode());
        verify(studentService).updateSubscriptionPreferences(eq(MY_PROFILE), any());
    }

    /**
     * ⭐⭐ 两套 id 空间的负向对照 —— **本文件最该存在的一条**。
     *
     * <p>司明拿**自己的 users.id（27）**去调订阅接口：27 在 {@code student_profile} 里
     * **不存在**（司明自己的档案是 3）。若守卫写成 {@code isSelf(路径id)}（比 users.id），
     * 这条会**放行**（27 == 27），于是它去改档案 27 的订阅 —— 那是**别人**或**不存在**的行。
     * 只有真的按 {@code student_profile.id} 比，这条才会被拒。
     *
     * <p>换句话说：这条用例能抓到"守卫选错了 id 空间"这个错，
     * 而上面那两条（比 4 拒、比 3 放行）抓不到 —— 它们在**两个空间里数字不同**时都成立。
     */
    @Test
    @DisplayName("★ 订阅族：拿自己的 users.id（27）当档案 id 提交 ⇒ 必须被拒（空间选对了才拦得住）")
    void subscriptionGuardMustCompareProfileIdNotUserId() {
        loginAs(ME);
        when(studentService.getProfileIdByUserId(ME)).thenReturn(MY_PROFILE);

        ApiResponse<Object> res = controller.updateSubscriptionPreferences(String.valueOf(ME), Map.of(), req("1"));

        assertEquals(403, res.getCode(), "27 不是司明的档案 id（3）⇒ 必须拒；放了就是守卫选错了 id 空间");
        verify(studentService, never()).updateSubscriptionPreferences(anyLong(), any());
    }

    @Test
    @DisplayName("订阅族：订阅预警等级 / 设渠道 同样受闸")
    void subscriptionLevelAndChannelAreGuarded() {
        loginAs(ME);
        when(studentService.getProfileIdByUserId(ME)).thenReturn(MY_PROFILE);

        assertEquals(403, controller.subscribeWarningLevel(String.valueOf(OTHER_PROFILE), "high", req("1")).getCode());
        assertEquals(403, controller.unsubscribeWarningLevel(String.valueOf(OTHER_PROFILE), "high", req("1")).getCode());
        assertEquals(403, controller.setPushChannel(String.valueOf(OTHER_PROFILE), "email", true, req("1")).getCode());
        verify(studentService, never()).subscribeWarningLevel(anyLong(), any());
        verify(studentService, never()).unsubscribeWarningLevel(anyLong(), any());
        verify(studentService, never()).setPushChannel(anyLong(), any(), any());
    }

    // ================================================== 管理员旁路

    @Test
    @DisplayName("管理员（X-Role: 4）不受本人限制 —— 否则会打断后台代操作")
    void adminBypassesOwnershipForUsersIdSpace() {
        // 管理员身份：users.id=1
        loginAs(1L);
        when(studentService.clearReadNotifications(OTHER)).thenReturn(Map.of("success", true));

        ApiResponse<Object> res = controller.clearReadNotifications(String.valueOf(OTHER), req("4"));

        assertEquals(200, res.getCode());
        verify(studentService).clearReadNotifications(OTHER);
    }

    /** 降级实体返回 null（认不出档案）时，订阅族必须**拒绝**而不是放行。 */
    @Test
    @DisplayName("订阅族：认不出自己的档案 ⇒ 拒绝（fail-closed）")
    void subscriptionGuardFailsClosedWhenProfileUnknown() {
        loginAs(ME);
        when(studentService.getProfileIdByUserId(ME)).thenReturn(null);

        ApiResponse<Object> res = controller.updateSubscriptionPreferences(String.valueOf(MY_PROFILE), Map.of(), req("1"));

        assertEquals(403, res.getCode());
        verify(studentService, never()).updateSubscriptionPreferences(anyLong(), any());
    }
}
