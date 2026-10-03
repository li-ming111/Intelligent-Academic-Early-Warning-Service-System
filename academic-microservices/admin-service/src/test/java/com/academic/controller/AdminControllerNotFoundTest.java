package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.common.entity.College;
import com.academic.entity.WarningRule;
import com.academic.common.entity.CounselorProfile;
import com.academic.mapper.CounselorProfileMapper;
import com.academic.service.AuditLogService;
import com.academic.service.CollegeService;
import com.academic.service.CounselorService;
import com.academic.service.ReportTemplateService;
import com.academic.service.UserService;
import com.academic.service.WarningRuleService;
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
 * 对**不存在的 id** 删除/更新，不能回「成功」（W7，2026-10-03）。
 *
 * <p><b>修之前是什么样</b>：MyBatis-Plus 的 {@code deleteById}/{@code updateById} 对不存在的 id
 * 返回 <b>0 行且不抛异常</b>；控制器不读返回值，于是 {@code DELETE /admin/colleges/999999}
 * 回 HTTP 200 + {@code "College deleted successfully"} —— 删了个空气，前端弹「删除成功」。
 * 同一族共 25 条端点。
 *
 * <p><b>这里钉四件事</b>：
 * <br>① 0 行 → {@code code 404}（本仓控制器业务拒绝的形状：HTTP 200 + body code 404）；
 * <br>② 有行 → 仍然 {@code code 200}（负向对照，别把正常删除改坏）；
 * <br>③ **审计只在真的删/改到时写** —— 原来 rules 无论有没有命中都「照写审计」，
 *     审计表里于是存在「修改了不存在的规则」这种查不下去的记录；
 * <br>④ {@code deleteCounselor} 的 ID 空间：路径 id 是 {@code counselor_profile.id}，
 *     账号要按 {@code profile.userId} 删。原实现拿同一个 id 去删 users 表，
 *     会删掉**另一个编号的账号** —— 所以"绝不按 profile id 删账号"是一条独立断言。
 */
@ExtendWith(MockitoExtension.class)
class AdminControllerNotFoundTest {

    @Mock private CollegeService collegeService;
    @Mock private WarningRuleService warningRuleService;
    @Mock private AuditLogService auditLogService;
    @Mock private ReportTemplateService reportTemplateService;
    @Mock private CounselorService counselorService;
    @Mock private CounselorProfileMapper counselorProfileMapper;
    @Mock private UserService userService;

    @InjectMocks private AdminController controller;

    // ---------------- 更新/删除：0 行 → 404 ----------------

    @Test
    @DisplayName("更新不存在的学院 → code 404（不是 200）")
    void updateCollege_missing_returns404() {
        when(collegeService.updateById(any(College.class))).thenReturn(false);

        // ⚠️ 请求体必须带一个真列（W8 起如此）：全 null 的体会在更早的 UpdateBodyGuard
        //    那一关回 400 —— 那是"请求体不对"，与"这个 id 不存在"是两件事，别混成一个用例。
        College body = new College();
        body.setName("计算机学院");
        ApiResponse<String> resp = controller.updateCollege(999999L, body);

        assertEquals(404, resp.getCode(), "更新 0 行必须回 404，不能回 success");
        assertFalse(resp.isSuccess());
    }

    @Test
    @DisplayName("删除不存在的学院 → code 404；删到 → code 200（负向对照）")
    void deleteCollege_missing_returns404_existing_returns200() {
        when(collegeService.removeById(999999L)).thenReturn(false);
        assertEquals(404, controller.deleteCollege(999999L).getCode());

        when(collegeService.removeById(1L)).thenReturn(true);
        assertEquals(200, controller.deleteCollege(1L).getCode());
    }

    // ---------------- 审计只在真的改到时写 ----------------

    @Test
    @DisplayName("更新不存在的预警规则 → 404 且**不写审计**")
    void updateRule_missing_returns404_andSkipsAudit() {
        when(warningRuleService.updateById(any(WarningRule.class))).thenReturn(false);

        WarningRule rule = new WarningRule();
        rule.setRuleName("不及格率阈值");
        ApiResponse<String> resp = controller.updateRule(999999L, rule);

        assertEquals(404, resp.getCode());
        verify(auditLogService, never()).record(anyString(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("删除不存在的预警规则 → 404 且**不写审计**")
    void deleteRule_missing_returns404_andSkipsAudit() {
        when(warningRuleService.removeById(999999L)).thenReturn(false);

        assertEquals(404, controller.deleteRule(999999L).getCode());
        verify(auditLogService, never()).record(anyString(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("更新预警规则命中 → 200 且审计照写（负向对照）")
    void updateRule_existing_returns200_andAudits() {
        when(warningRuleService.updateById(any(WarningRule.class))).thenReturn(true);

        WarningRule rule = new WarningRule();
        rule.setRuleName("不及格率阈值");
        assertEquals(200, controller.updateRule(1L, rule).getCode());

        verify(auditLogService).record(eq("UPDATE"), eq("WARNING_RULE"), eq(1L), anyString());
    }

    // ---------------- 报表模板（走 service 返回的 boolean） ----------------

    @Test
    @DisplayName("删除不存在的报表模板 → 404；service 说删掉了 → 200")
    void deleteReportTemplate_carriesServiceResult() {
        when(reportTemplateService.deleteTemplate(999999L)).thenReturn(false);
        assertEquals(404, controller.deleteReportTemplate(999999L).getCode());

        when(reportTemplateService.deleteTemplate(7L)).thenReturn(true);
        assertEquals(200, controller.deleteReportTemplate(7L).getCode());
    }

    // ---------------- 辅导员的 ID 空间 ----------------

    @Test
    @DisplayName("删除不存在的辅导员 → 404，且**绝不**按路径 id 去删 users 表")
    void deleteCounselor_missing_returns404_andTouchesNoAccount() {
        when(counselorProfileMapper.selectById(999999L)).thenReturn(null);

        assertEquals(404, controller.deleteCounselor(999999L).getCode());
        verify(userService, never()).removeById(anyLong());
    }

    @Test
    @DisplayName("删除存在的辅导员 → 账号按 profile.userId 删，而不是按 profile.id")
    void deleteCounselor_existing_deletesAccountByUserId() {
        CounselorProfile profile = new CounselorProfile();
        profile.setId(3L);
        profile.setUserId(29L);
        when(counselorProfileMapper.selectById(3L)).thenReturn(profile);
        when(counselorService.removeById(3L)).thenReturn(true);

        assertEquals(200, controller.deleteCounselor(3L).getCode());

        // 关键：删的是 users#29（该辅导员的账号），不是 users#3
        verify(userService).removeById(29L);
        verify(userService, never()).removeById(3L);
    }
}
