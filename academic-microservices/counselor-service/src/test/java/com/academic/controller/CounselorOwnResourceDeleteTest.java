package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.common.entity.AssistancePlan;
import com.academic.common.entity.CommunicationLog;
import com.academic.service.*;
import com.academic.mapper.*;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 辅导员删除**自己的**帮扶计划 / 沟通记录（W12，2026-10-04）。
 *
 * <p><b>这不是"补一道闸"，是"把装反的闸掰正"</b>。这两条删除里一直有归属判断，
 * 但它比错了 id 空间：
 * <pre>
 *   assistance_plans.counselor_id   = 53   &lt;- 存的是 <b>users.id</b>
 *   counselor_profiles.id           = 1    &lt;- 存的是<b>档案 id</b>
 *   …
 *   plan.getCounselorId().equals(profile.getId())   // 53.equals(1) 恒假
 * </pre>
 * 活库真值（2026-10-04 直查）：{@code counselor_profiles} 只有一行 {@code id=1/user_id=53}；
 * {@code assistance_plans.counselor_id} 四行全是 53；{@code communication_logs.counselor_id}
 * 也是 53。两个数字没有一个相等过 ⇒ <b>辅导员删自己的帮扶计划/沟通记录一律被判 403</b>
 * —— 本人被拒（功能坏了），而不是"更安全了"。
 *
 * <p><b>为什么单测能钉住它</b>：这类"两套 id 都是数字、混用不报错"的错，接口层只看到
 * 一个 403，看不出是"该拒"还是"拒错了人"。所以把**活库那两个真数字**（53 与 1）写进用例：
 * 所有者 53 删 53 ⇒ 必须放行；把档案 id 1 当所有者 ⇒ 必须被拒。
 */
@ExtendWith(MockitoExtension.class)
class CounselorOwnResourceDeleteTest {

    @Mock private CounselorService counselorService;
    @Mock private AssistancePlanService assistancePlanService;
    @Mock private CommunicationLogService communicationLogService;
    @Mock private CounselorNotificationService counselorNotificationService;
    @Mock private com.academic.common.notify.StudentNotifier studentNotifier;
    @Mock private AssistanceEvaluationMapper assistanceEvaluationMapper;
    @Mock private StudentService studentService;
    @Mock private CounselorClassApplicationMapper counselorClassApplicationMapper;
    @Mock private ClassManagementService classManagementService;
    @Mock private ClassInfoService classInfoService;
    @Mock private ClassInfoMapper classInfoMapper;
    @Mock private ScoreMapper scoreMapper;
    @Mock private WarningMapper warningMapper;
    @Mock private StudentMapper studentMapper;

    @InjectMocks private CounselorController controller;

    /** 活库真值：唯一那位辅导员的 {@code users.id}。 */
    private static final long ME = 53L;
    /** 活库真值：另一位辅导员的 {@code users.id}（`counselor_profiles` 里没有，纯"别人"）。 */
    private static final long OTHER = 54L;
    /** 活库真值：{@code counselor_profiles.id} —— **档案 id**，不是 users.id。 */
    private static final long MY_PROFILE_ID = 1L;

    private static HttpServletRequest as(long userId) {
        HttpServletRequest r = mock(HttpServletRequest.class);
        lenient().when(r.getHeader("X-User-Id")).thenReturn(String.valueOf(userId));
        return r;
    }

    private static HttpServletRequest asAdmin(long userId) {
        HttpServletRequest r = mock(HttpServletRequest.class);
        lenient().when(r.getHeader("X-User-Id")).thenReturn(String.valueOf(userId));
        lenient().when(r.getHeader("X-Role")).thenReturn("4");
        return r;
    }

    private static AssistancePlan planOwnedBy(Long counselorId) {
        AssistancePlan p = new AssistancePlan();
        p.setId(1001L);
        p.setCounselorId(counselorId);
        return p;
    }

    private static CommunicationLog logOwnedBy(Long counselorId) {
        CommunicationLog c = new CommunicationLog();
        c.setId(2001L);
        c.setCounselorId(counselorId);
        return c;
    }

    // ================================================== 核心：本人删自己的

    @Test
    @DisplayName("辅导员 53 删自己的帮扶计划（counselor_id=53）⇒ 放行（修之前这里恒 403）")
    void ownPlanDeleteAllowed() {
        when(assistancePlanService.getById(1001L)).thenReturn(planOwnedBy(ME));
        when(assistancePlanService.deletePlan(1001L)).thenReturn(true);

        ApiResponse<String> res = controller.deleteAssistancePlan(1001L, as(ME));

        assertEquals(200, res.getCode(), "修之前：53.equals(counselor_profiles.id=1) 恒假 ⇒ 本人也被拒");
        verify(assistancePlanService).deletePlan(1001L);
    }

    @Test
    @DisplayName("辅导员 53 删自己的沟通记录（counselor_id=53）⇒ 放行")
    void ownLogDeleteAllowed() {
        when(communicationLogService.getById(2001L)).thenReturn(logOwnedBy(ME));
        when(communicationLogService.deleteLog(2001L)).thenReturn(true);

        ApiResponse<String> res = controller.deleteCommunicationLog(2001L, as(ME));

        assertEquals(200, res.getCode());
        verify(communicationLogService).deleteLog(2001L);
    }

    // ================================================== 负向：别人的东西还是删不掉

    @Test
    @DisplayName("删**别人**的帮扶计划 ⇒ 403，且一行都不删")
    void othersPlanDeleteRejected() {
        when(assistancePlanService.getById(1001L)).thenReturn(planOwnedBy(OTHER));

        ApiResponse<String> res = controller.deleteAssistancePlan(1001L, as(ME));

        assertEquals(403, res.getCode());
        verify(assistancePlanService, never()).deletePlan(anyLong());
    }

    @Test
    @DisplayName("删**别人**的沟通记录 ⇒ 403，且一行都不删")
    void othersLogDeleteRejected() {
        when(communicationLogService.getById(2001L)).thenReturn(logOwnedBy(OTHER));

        ApiResponse<String> res = controller.deleteCommunicationLog(2001L, as(ME));

        assertEquals(403, res.getCode());
        verify(communicationLogService, never()).deleteLog(anyLong());
    }

    // ================================================== 空间选错的两个方向，各钉一条

    @Test
    @DisplayName("★ 把**档案 id(1)** 当所有者 ⇒ 必须被拒（这正是修之前那条恒假比较的另一面）")
    void profileIdIsNotOwnerId() {
        // 若闸门又退回"拿档案 id 比"，53 的所有者会被判成"不是我的" ⇒ 本人 403；
        // 反过来，一条恰好属于 users 1 的计划会被放行。第二种情况这里钉住。
        when(assistancePlanService.getById(1001L)).thenReturn(planOwnedBy(MY_PROFILE_ID));

        ApiResponse<String> res = controller.deleteAssistancePlan(1001L, as(ME));

        assertEquals(403, res.getCode(), "所有者存的是 users.id，1 != 53 ⇒ 必须 403");
        verify(assistancePlanService, never()).deletePlan(anyLong());
    }

    @Test
    @DisplayName("没有所有者信息的行（counselor_id 为 NULL）⇒ 保持原行为放行（历史数据不能变成删不掉）")
    void nullOwnerStillDeletable() {
        when(assistancePlanService.getById(1001L)).thenReturn(planOwnedBy(null));
        when(assistancePlanService.deletePlan(1001L)).thenReturn(true);

        assertEquals(200, controller.deleteAssistancePlan(1001L, as(ME)).getCode());
    }

    @Test
    @DisplayName("计划不存在 ⇒ 保持原行为走下去（由 service 回 404，不是这里判）")
    void missingPlanFallsThrough() {
        when(assistancePlanService.getById(1001L)).thenReturn(null);
        when(assistancePlanService.deletePlan(1001L)).thenReturn(false);

        assertEquals(404, controller.deleteAssistancePlan(1001L, as(ME)).getCode());
    }

    // ================================================== 管理员旁路

    @Test
    @DisplayName("管理员 ⇒ 删谁的都放行（旁路没被这轮改动破坏）")
    void adminBypassesOwnerCheck() {
        when(assistancePlanService.getById(1001L)).thenReturn(planOwnedBy(OTHER));
        when(assistancePlanService.deletePlan(1001L)).thenReturn(true);
        when(communicationLogService.getById(2001L)).thenReturn(logOwnedBy(OTHER));
        when(communicationLogService.deleteLog(2001L)).thenReturn(true);

        assertEquals(200, controller.deleteAssistancePlan(1001L, asAdmin(1L)).getCode());
        assertEquals(200, controller.deleteCommunicationLog(2001L, asAdmin(1L)).getCode());
    }

    @Test
    @DisplayName("没有登录身份（X-User-Id 缺失）⇒ 别人的东西仍然删不掉")
    void anonymousCannotDeleteOthers() {
        when(assistancePlanService.getById(1001L)).thenReturn(planOwnedBy(OTHER));

        assertEquals(403, controller.deleteAssistancePlan(1001L, mock(HttpServletRequest.class)).getCode());
        verify(assistancePlanService, never()).deletePlan(anyLong());
    }
}
