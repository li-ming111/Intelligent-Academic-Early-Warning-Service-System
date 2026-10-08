package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.common.entity.AcademicWarning;
import com.academic.common.entity.CommunicationLog;
import com.academic.common.entity.Student;
import com.academic.entity.ClassInfo;
import com.academic.mapper.*;
import com.academic.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 辅导员端**归属闸**（W13，2026-10-04）。
 *
 * <p><b>威胁模型</b>：网关的 {@code PREFIX_COUNSELOR} 那段只保证"打进来的是个辅导员"，
 * 挡得住学生/教师，**挡不住辅导员之间互相看/改**。而这条前缀下有近 60 个端点，
 * 标识符（{@code counselor_id}、{@code classId}、{@code studentId}、{@code warningId}、
 * {@code planId}）**全由客户端给**，控制器收到就照查。
 *
 * <p><b>本轮最尖锐的一条</b>是 W4.12 那个"装了一半"的闸：
 * <pre>
 *   GET /scores/class/{classId}?counselor_id=
 *   → isClassOfCounselor(classId, counselorId)   // counselorId 是**客户端传的**
 * </pre>
 * 甲辅导员要读乙的班，只要把 {@code counselor_id} 也填成**乙**，那句判断就从
 * "这个班是不是你的"变成了"这个班是不是他的" —— 于是放行。用例
 * {@link #crossClassScoresWithBorrowedCounselorIdRejected()} 正是钉这个：把乙名下的班
 * 配成"乙的班"，甲拿乙的 id 来读必须被拒，且**根本不去查成绩**。
 *
 * <p><b>第二类是提权</b>：{@code POST /class-management/applications/{id}/review}
 * 挂在 {@code /api/counselors/**} 下 ⇒ 辅导员自己能调，可以**批掉自己刚提交的申请**白拿一个班。
 *
 * <p><b>第三类是"身份跟着请求体走"</b>：{@code /students/notify}、{@code /class-management/apply}
 * 的 {@code counselorId} 在 body 里 —— 不核对就能以别人的名义发通知 / 交申请。
 */
@ExtendWith(MockitoExtension.class)
class CounselorOwnershipGuardTest {

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
    /** 另一位辅导员的 {@code users.id}（活库没有第二个辅导员账号，纯"别人"）。 */
    private static final long OTHER = 54L;
    /** 我带的班（{@code classes.id}）。 */
    private static final long MY_CLASS = 1L;
    /** `student_profile.id`（`assistance_plans.student_id` / `academic_warning.student_id` 用这套）。 */
    private static final long MY_STUDENT = 3L;

    // ---------------------------------------------------------------- 脚手架

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

    private static HttpServletRequest anonymous() {
        return mock(HttpServletRequest.class);
    }

    /** 让 {@code isClassOfCounselor(classId, counselorId)} 认为这个班属于该辅导员。 */
    private void classBelongsTo(long counselorId, long classId) {
        lenient().when(classInfoService.getClassesByCounselorId(counselorId))
                .thenReturn(List.of(Map.of("id", classId)));
    }

    private Student studentInClass(long profileId, long classId) {
        Student s = new Student();
        s.setId(profileId);
        s.setClassId(classId);
        lenient().when(studentService.getById(profileId)).thenReturn(s);
        return s;
    }

    private AcademicWarning warningOf(long warningId, long profileId) {
        AcademicWarning w = new AcademicWarning();
        w.setId(warningId);
        w.setStudentId(profileId);
        w.setTitle("挂科预警");
        lenient().when(warningMapper.selectById(warningId)).thenReturn(w);
        return w;
    }

    // ============================================ 1. 借用别人的 counselor_id 读他的班（W4.12 的缺口）

    @Test
    @DisplayName("★ 甲拿**乙的** counselor_id 读乙的班 ⇒ 403，且一次成绩都不查（W4.12 那句判断曾被这么绕开）")
    void crossClassScoresWithBorrowedCounselorIdRejected() {
        classBelongsTo(OTHER, MY_CLASS); // 这个班是**乙**的

        ApiResponse<List<Map<String, Object>>> res =
                controller.getClassScores(MY_CLASS, OTHER, as(ME));

        assertEquals(403, res.getCode(),
                "缺了 W13 的身份核对，isClassOfCounselor(班, 乙) 会判 true ⇒ 甲读到乙整班成绩");
        verify(scoreMapper, never()).getScoresByClassId(anyLong());
    }

    @Test
    @DisplayName("本人读自己的班 ⇒ 200（没有过度拦截）")
    void ownClassScoresAllowed() {
        classBelongsTo(ME, MY_CLASS);
        when(scoreMapper.getScoresByClassId(MY_CLASS)).thenReturn(List.of());

        assertEquals(200, controller.getClassScores(MY_CLASS, ME, as(ME)).getCode());
        verify(scoreMapper).getScoresByClassId(MY_CLASS);
    }

    @Test
    @DisplayName("本人拿**别人的**班 id ⇒ 403（第二重：班不是我的）")
    void ownIdForeignClassRejected() {
        classBelongsTo(ME, MY_CLASS); // 我只带 1 班

        assertEquals(403, controller.getClassScores(99L, ME, as(ME)).getCode());
        verify(scoreMapper, never()).getScoresByClassId(anyLong());
    }

    // ============================================ 2. 提权：辅导员自批班级申请

    @Test
    @DisplayName("★ 辅导员调「审核班级申请」⇒ 403，一行都不写（自批=白拿一个班）")
    void counselorCannotReviewOwnApplication() {
        ApiResponse<String> res = controller.reviewCounselorApplication(
                7L, Map.of("status", 1), as(ME));

        assertEquals(403, res.getCode());
        verify(classManagementService, never()).reviewApplication(anyLong(), any(), any());
    }

    @Test
    @DisplayName("管理员调「审核班级申请」⇒ 放行（旁路没被这轮改动破坏）")
    void adminCanReviewApplication() {
        when(classManagementService.reviewApplication(7L, 1, null)).thenReturn(null);

        assertEquals(200, controller.reviewCounselorApplication(7L, Map.of("status", 1), asAdmin(1L)).getCode());
    }

    // ============================================ 3. 身份写在请求体里

    @Test
    @DisplayName("★ 以**别的辅导员**的 counselor_id 发通知 ⇒ 403，一条通知都不发")
    void cannotNotifyAsAnotherCounselor() {
        Map<String, Object> body = new HashMap<>();
        body.put("counselor_id", OTHER);
        body.put("title", "x");
        body.put("content", "y");

        assertEquals(403, controller.notifyStudents(body, as(ME)).getCode());
        verify(counselorNotificationService, never()).send(anyMap());
    }

    @Test
    @DisplayName("体里的 counselor_id 是 **Integer** 53 时也必须放行（别把 Integer(53) 与 Long(53) 比成不等）")
    void integerBodyIdDoesNotOverBlock() {
        // 这是"过度拦截"的负向对照：JSON 数字经 Jackson 出来是 Integer，
        // 若守卫直接 `Object.equals(Long)`，本人也会被拒 —— 闸就装反了。
        Map<String, Object> body = new HashMap<>();
        body.put("counselor_id", 53); // Integer
        body.put("title", "x");
        body.put("content", "y");
        when(counselorNotificationService.send(anyMap())).thenReturn(Map.of("sent", 1));

        assertEquals(200, controller.notifyStudents(body, as(ME)).getCode());
        verify(counselorNotificationService).send(anyMap());
    }

    @Test
    @DisplayName("★ 以**别的辅导员**的名义提交班级申请 ⇒ 403，一张申请单都不落库")
    void cannotApplyForClassAsAnotherCounselor() {
        Map<String, Object> body = new HashMap<>();
        body.put("counselorId", OTHER);
        body.put("classId", MY_CLASS);
        body.put("className", "计科2306班");

        assertEquals(403, controller.applyForClass(body, as(ME)).getCode());
        verify(classManagementService, never()).applyForClass(anyLong(), anyLong(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("★ 以**别的辅导员**的 counselor_id 解除班级管理 ⇒ 403")
    void cannotCancelAnotherCounselorsClass() {
        Map<String, Object> body = new HashMap<>();
        body.put("counselorId", OTHER);
        body.put("classId", MY_CLASS);

        assertEquals(403, controller.cancelClassManagement(body, as(ME)).getCode());
        verify(classManagementService, never()).cancelClassManagement(anyLong(), anyLong());
    }

    // ============================================ 4. 资源 id 型：别人的预警

    @Test
    @DisplayName("★ 处理**别人班学生**的预警 ⇒ 403，一行都不改")
    void cannotProcessForeignWarning() {
        warningOf(395L, MY_STUDENT);
        classBelongsTo(OTHER, 2L);              // 这名学生挂在**乙**的 2 班
        studentInClass(MY_STUDENT, 2L);

        ApiResponse<Map<String, Object>> res =
                controller.processWarning(395L, Map.of("handleResult", "已谈话"), as(ME));

        assertEquals(403, res.getCode());
        verify(warningMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("处理**自己**班学生的预警 ⇒ 200（并真写库）")
    void canProcessOwnWarning() {
        AcademicWarning w = warningOf(395L, MY_STUDENT);
        classBelongsTo(ME, MY_CLASS);
        studentInClass(MY_STUDENT, MY_CLASS);

        ApiResponse<Map<String, Object>> res =
                controller.processWarning(395L, Map.of("handleResult", "已谈话"), as(ME));

        assertEquals(200, res.getCode());
        verify(warningMapper).updateById(w);
        assertEquals(1, w.getStatus());
    }

    @Test
    @DisplayName("★ 批量处理里混进**一条别人的**预警 ⇒ 整单 403，一条都不改")
    void batchWithOneForeignWarningRejectsWholeBatch() {
        // 100 = 我班学生的，200 = 乙班学生的
        warningOf(100L, MY_STUDENT);
        warningOf(200L, 13L);
        classBelongsTo(ME, MY_CLASS);
        studentInClass(MY_STUDENT, MY_CLASS);
        studentInClass(13L, 2L);
        classBelongsTo(OTHER, 2L);

        ApiResponse<Map<String, Object>> res =
                controller.batchProcessWarnings(List.of(100L, 200L), as(ME));

        assertEquals(403, res.getCode(), "半单成功会让『越权被拒』与『部分成功』长得一样");
        verify(warningMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("全部是**自己**班学生的预警 ⇒ 200，逐条写库")
    void batchOfOwnWarningsSucceeds() {
        warningOf(100L, MY_STUDENT);
        classBelongsTo(ME, MY_CLASS);
        studentInClass(MY_STUDENT, MY_CLASS);

        assertEquals(200, controller.batchProcessWarnings(List.of(100L), as(ME)).getCode());
        verify(warningMapper).updateById(any(AcademicWarning.class));
    }

    // ============================================ 5. 资源 id 型：别人的班

    @Test
    @DisplayName("★ 读**别人**班的详情 / 名单 / 预警概览 ⇒ 三条全 403")
    void foreignClassDetailFamilyRejected() {
        // 我的班是 1，乙的班是 2；classInfoService 只认 1
        classBelongsTo(ME, MY_CLASS);

        assertEquals(403, controller.getClassDetail(2L, as(ME)).getCode());
        assertEquals(403, controller.getClassStudents(2L, as(ME)).getCode());
        assertEquals(403, controller.getClassWarningOverview(2L, as(ME)).getCode());
        verify(classInfoMapper, never()).getStudentsByClassIdMap(anyLong());
    }

    @Test
    @DisplayName("读**自己**班的详情 ⇒ 200（没有过度拦截）")
    void ownClassDetailAllowed() {
        classBelongsTo(ME, MY_CLASS);
        ClassInfo ci = new ClassInfo();
        ci.setId(MY_CLASS);
        ci.setName("计科2306班");
        when(classInfoMapper.selectById(MY_CLASS)).thenReturn(ci);
        when(classInfoMapper.getStudentsByClassIdMap(MY_CLASS)).thenReturn(List.of());

        assertEquals(200, controller.getClassDetail(MY_CLASS, as(ME)).getCode());
    }

    // ============================================ 6. 资源 id 型：别人的学生 / 帮扶计划

    @Test
    @DisplayName("★ 读**别人**班学生的成绩趋势 ⇒ 403，一条成绩都不查")
    void foreignStudentTrendRejected() {
        studentInClass(13L, 2L);   // 学生挂在乙的 2 班
        classBelongsTo(OTHER, 2L);

        assertEquals(403, controller.getStudentScoreTrend(13L, as(ME)).getCode());
        verify(scoreMapper, never()).getScoresByStudentId(anyLong());
    }

    @Test
    @DisplayName("读**自己**班学生的成绩趋势 ⇒ 200")
    void ownStudentTrendAllowed() {
        studentInClass(MY_STUDENT, MY_CLASS);
        classBelongsTo(ME, MY_CLASS);
        when(scoreMapper.getScoresByStudentId(MY_STUDENT)).thenReturn(List.of());

        assertEquals(200, controller.getStudentScoreTrend(MY_STUDENT, as(ME)).getCode());
    }

    @Test
    @DisplayName("★ 读**别人**的帮扶计划列表 ⇒ 403（W13 顺带把这个接口的『收了参数却不用』也修了）")
    void foreignCounselorPlansRejected() {
        assertEquals(403, controller.getCounselorAssistancePlans(OTHER, as(ME)).getCode());
        verify(assistancePlanService, never()).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }

    @Test
    @DisplayName("读**自己**的帮扶计划列表 ⇒ 只按自己的 counselor_id 过滤（不再是全库所有人的）")
    void ownCounselorPlansFilteredByMe() {
        when(assistancePlanService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of());

        assertEquals(200, controller.getCounselorAssistancePlans(ME, as(ME)).getCode());

        org.mockito.ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.QueryWrapper> cap =
                org.mockito.ArgumentCaptor.forClass(
                        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class);
        verify(assistancePlanService).list(cap.capture());
        String sql = cap.getValue().getSqlSegment();
        assertTrue(sql.contains("counselor_id"),
                "缺了过滤就是『接口名叫我的、内容是大家的』；实际 SQL 片段=" + sql);
        assertTrue(cap.getValue().getParamNameValuePairs().containsValue(ME),
                "过滤值必须是登录人自己的 users.id；实际=" + cap.getValue().getParamNameValuePairs());
    }

    // ============================================ 7. 统计族（counselor_id 型）逐条

    @Test
    @DisplayName("★ 统计族拿**别人的** counselor_id ⇒ 六条全 403，且一次不查库")
    void analyticsFamilyRejectsForeignCounselorId() {
        assertEquals(403, controller.getCreditInsufficientRate(OTHER, as(ME)).getCode());
        assertEquals(403, controller.getWarningDistribution(OTHER, as(ME)).getCode());
        assertEquals(403, controller.getWarningHandlingEfficiency(OTHER, as(ME)).getCode());
        assertEquals(403, controller.getCreditAchievementRanking(OTHER, as(ME)).getCode());
        assertEquals(403, controller.getWarningTrend(OTHER, as(ME)).getCode());
        assertEquals(403, controller.getAssistancePlanCompletionRate(OTHER, as(ME)).getCode());

        verify(scoreMapper, never()).getClassScoreStatsByCounselorId(anyLong());
        verify(warningMapper, never()).getWarningDistributionByCounselor(anyLong());
        verify(assistancePlanService, never()).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }

    @Test
    @DisplayName("统计族拿**自己的** counselor_id ⇒ 200")
    void analyticsFamilyAllowsSelf() {
        when(scoreMapper.getClassScoreStatsByCounselorId(ME)).thenReturn(List.of());
        when(warningMapper.getWarningDistributionByCounselor(ME)).thenReturn(List.of());
        when(warningMapper.getWarningTrendByCounselor(ME)).thenReturn(List.of());
        when(assistancePlanService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of());

        assertEquals(200, controller.getCreditInsufficientRate(ME, as(ME)).getCode());
        assertEquals(200, controller.getWarningDistribution(ME, as(ME)).getCode());
        assertEquals(200, controller.getCreditAchievementRanking(ME, as(ME)).getCode());
        assertEquals(200, controller.getWarningTrend(ME, as(ME)).getCode());
        assertEquals(200, controller.getAssistancePlanCompletionRate(ME, as(ME)).getCode());
    }

    // ============================================ 8. 其余 counselor_id 型

    @Test
    @DisplayName("★ 学分监控/学分不足/低分名单/班级列表/通知历史/周通知数/班级预警对比 拿别人的 id ⇒ 全 403")
    void otherCounselorIdFamiliesRejected() {
        assertEquals(403, controller.getInsufficientCreditStudents(OTHER, 1, 20, as(ME)).getCode());
        assertEquals(403, controller.getLowScoreStudents(OTHER, as(ME)).getCode());
        assertEquals(403, controller.getClasses(OTHER, as(ME)).getCode());
        assertEquals(403, controller.getNotificationHistory(OTHER, 1, 10, as(ME)).getCode());
        assertEquals(403, controller.getWeeklyNotificationCount(OTHER, as(ME)).getCode());
        assertEquals(403, controller.compareClassWarnings(OTHER, as(ME)).getCode());

        verify(scoreMapper, never()).getInsufficientCreditStudents(anyLong());
        verify(scoreMapper, never()).getLowScoresByCounselorId(anyLong(), anyInt());
        verify(counselorNotificationService, never()).history(anyLong(), anyInt(), anyInt());
        verify(counselorNotificationService, never()).weeklyCount(anyLong());
    }

    @Test
    @DisplayName("可空的 counselor_id：为空 ⇒ 维持原状（回空表）；填了别人的 ⇒ 403")
    void optionalCounselorIdIsStillGuarded() {
        // getEnrollments / getCourseScoreDistribution 的 counselor_id 都是 required=false
        assertEquals(200, controller.getEnrollments(null, as(ME)).getCode());
        assertEquals(403, controller.getEnrollments(OTHER, as(ME)).getCode());
        assertEquals(403, controller.getCourseScoreDistribution(1L, OTHER, as(ME)).getCode());
        verify(scoreMapper, never()).getScoresByCourseIdAndCounselor(anyLong(), anyLong());
    }

    // ============================================ 9. 兜底：没有身份/管理员

    @Test
    @DisplayName("★ 没有登录身份（X-User-Id 缺失）⇒ 一律拒绝，不是『放行』")
    void anonymousIsDenied() {
        assertEquals(403, controller.getClasses(ME, anonymous()).getCode());
        assertEquals(403, controller.getClassScores(MY_CLASS, ME, anonymous()).getCode());
        assertEquals(403, controller.getClassStudents(MY_CLASS, anonymous()).getCode());
        assertEquals(403, controller.getStudentScoreTrend(MY_STUDENT, anonymous()).getCode());
        verify(scoreMapper, never()).getScoresByClassId(anyLong());
    }

    @Test
    @DisplayName("管理员 ⇒ 不受归属闸限制（旁路本身也要是活的）")
    void adminBypassesOwnershipGuards() {
        classBelongsTo(ME, MY_CLASS);      // 班是 53 的
        classBelongsTo(OTHER, 2L);         // 2 班是 54 的
        studentInClass(13L, 2L);
        when(scoreMapper.getScoresByClassId(MY_CLASS)).thenReturn(List.of());
        when(scoreMapper.getScoresByStudentId(13L)).thenReturn(List.of());
        when(scoreMapper.getLowScoresByCounselorId(OTHER, 70)).thenReturn(List.of());

        assertEquals(200, controller.getClassScores(MY_CLASS, ME, asAdmin(1L)).getCode());
        assertEquals(200, controller.getStudentScoreTrend(13L, asAdmin(1L)).getCode());
        assertEquals(200, controller.getLowScoreStudents(OTHER, asAdmin(1L)).getCode());
    }

    @Test
    @DisplayName("别人的沟通记录：改/删都要被挡（写侧）")
    void foreignCommunicationLogWriteRejected() {
        CommunicationLog foreign = new CommunicationLog();
        foreign.setId(2002L);
        foreign.setCounselorId(OTHER);
        when(communicationLogService.getById(2002L)).thenReturn(foreign);

        assertEquals(403, controller.deleteCommunicationLog(2002L, as(ME)).getCode());
        assertEquals(403, controller.updateCommunicationLog(2002L, foreign, as(ME)).getCode());
        verify(communicationLogService, never()).deleteLog(anyLong());
    }

    // ============================================ 10. W17：发通知的身份（W13 那道闸只读了下划线）

    @Test
    @DisplayName("★ 体里的 `counselorId`（**驼峰**）也必须是本人 —— 两个真实页面发的正是这个拼写")
    void cannotNotifyAsAnotherCounselor_camelCase() {
        // 这条是 W13 漏掉的那一半：控制器只读 `counselor_id`，而
        // `CounselorNotifications.vue` / `CounselorCourses.vue` 发的是 `counselorId`；
        // 服务层 `firstLong(counselorId, counselor_id)` 又恰好驼峰优先
        // ⇒ 守卫整个被跳过，但身份照样被采纳（冒名群发 + 把记录记到别人头上）。
        Map<String, Object> body = new HashMap<>();
        body.put("counselorId", OTHER);
        body.put("recipients", List.of("all"));
        body.put("content", "y");

        assertEquals(403, controller.notifyStudents(body, as(ME)).getCode(),
                "真实调用方发的就是 counselorId，只认下划线等于这道闸从来没装");
        verify(counselorNotificationService, never()).send(anyMap());
    }

    @Test
    @DisplayName("★ 体里**不写身份**只给 student_ids ⇒ 必须把本人 id 回填进 payload")
    void missingIdentityIsBackfilledSoRosterCheckRuns() {
        // 光拒绝"写了别人的"挡不住"什么都不写"：服务层 `roster == null` 时，
        // 那句"学生 X 不在该辅导员名下，已跳过"**根本不执行** ⇒ 可给任意学生发通知。
        // 判据落在**递给服务的 payload** 上 —— 拿不到回填，这条必须红。
        Map<String, Object> body = new HashMap<>();
        body.put("student_ids", List.of(3L));
        body.put("content", "y");
        when(counselorNotificationService.send(anyMap())).thenReturn(Map.of("sent", 1));

        assertEquals(200, controller.notifyStudents(body, as(ME)).getCode());

        org.mockito.ArgumentCaptor<Map<String, Object>> cap = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(counselorNotificationService).send(cap.capture());
        assertEquals(Long.valueOf(ME), cap.getValue().get("counselorId"),
                "不回填 = 服务层不取 rosters = 收件人不受『本人名下』约束；实际 payload=" + cap.getValue());
        assertFalse(cap.getValue().containsKey("counselor_id"),
                "两种拼写不能都留着，免得服务层 '驼峰优先' 的规则再被绕过");
    }

    @Test
    @DisplayName("本人用**驼峰 Integer** 报自己的 id ⇒ 放行（别把 Integer(53) 与 Long(53) 比成不等）")
    void ownCamelCaseIntegerIdAllowed() {
        Map<String, Object> body = new HashMap<>();
        body.put("counselorId", 53); // Integer
        body.put("content", "y");
        when(counselorNotificationService.send(anyMap())).thenReturn(Map.of("sent", 1));

        assertEquals(200, controller.notifyStudents(body, as(ME)).getCode());
        verify(counselorNotificationService).send(anyMap());
    }

    @Test
    @DisplayName("★ 没有登录身份 ⇒ 403，一条都不发（身份为空的正确处置是拒绝，不是原样转发）")
    void anonymousNotifyDenied() {
        Map<String, Object> body = new HashMap<>();
        body.put("counselorId", OTHER);
        body.put("content", "y");

        assertEquals(403, controller.notifyStudents(body, anonymous()).getCode());
        verify(counselorNotificationService, never()).send(anyMap());
    }

    @Test
    @DisplayName("管理员指名别的辅导员发送 ⇒ 放行，且 payload 不被改写（旁路本身也要是活的）")
    void adminMayNotifyAsAnotherCounselor() {
        Map<String, Object> body = new HashMap<>();
        body.put("counselorId", OTHER);
        body.put("content", "y");
        when(counselorNotificationService.send(anyMap())).thenReturn(Map.of("sent", 1));

        assertEquals(200, controller.notifyStudents(body, asAdmin(1L)).getCode());

        org.mockito.ArgumentCaptor<Map<String, Object>> cap = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(counselorNotificationService).send(cap.capture());
        assertEquals(Long.valueOf(OTHER), cap.getValue().get("counselorId"),
                "管理员这条旁路被顺手改掉的话，跨辅导员运维就没了");
    }
}
