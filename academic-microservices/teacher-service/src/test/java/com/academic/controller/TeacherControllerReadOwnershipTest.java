package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.common.entity.Course;
import com.academic.common.entity.TeacherProfile;
import com.academic.entity.Feedback;
import com.academic.service.ClassInfoService;
import com.academic.service.CourseService;
import com.academic.service.FeedbackService;
import com.academic.service.ScoreRecordService;
import com.academic.service.TeacherClassApplicationService;
import com.academic.service.TeacherService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 教师自助面**读端点**的归属闸（W11，2026-10-04）。
 *
 * <p><b>修之前是什么样</b>：{@code TeacherController} 的读端点把路径/查询参数里的 id
 * 直接当查询条件用，谁都能换个数字读别人的东西。全量扫描（经网关 8076，只读）实测
 * **11 条越权、0 条被拦**，最狠的一条是
 * {@code GET /api/teachers/enrollments?teacherId=29} —— 教师 18 打过去拿到
 * **246 行带学生姓名的花名册**。
 *
 * <p><b>本控制器与 student-service 的差别</b>：身份不从 {@code SecurityContext} 读，
 * 而是 {@code getUserIdFromGateway(request)} 读 {@code X-User-Id}；管理员旁路读
 * {@code X-Role: 4}。所以这组用例的头是<b>两个</b>（谁 + 什么角色），
 * 学生侧那组是一个（X-Role 只管管理员）。
 *
 * <p>判据形状：返回码 + {@code verify(..., never())} —— 被拦的那次**读压根不该发生**。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TeacherController 读端点归属闸（W11）")
class TeacherControllerReadOwnershipTest {

    @Mock private ClassInfoService classInfoService;
    @Mock private TeacherClassApplicationService applicationService;
    @Mock private CourseService courseService;
    @Mock private TeacherService teacherService;
    @Mock private FeedbackService feedbackService;
    /** courseId 分支会去查选课学生 —— 放行面必须喂住，否则 NPE 会伪装成 500。 */
    @Mock private ScoreRecordService scoreRecordService;
    @Mock private com.academic.service.TeacherCourseService teacherCourseService;

    @InjectMocks private TeacherController controller;

    /** 教师 A：users.id=18（teacher001 刘教授）。 */
    private static final long ME = 18L;
    /** 教师 B：users.id=29（10001 于凤海）——"别人"。 */
    private static final long OTHER = 29L;

    /** 网关注入的身份：X-User-Id = 谁。 */
    private static HttpServletRequest as(long userId) {
        HttpServletRequest r = mock(HttpServletRequest.class);
        lenient().when(r.getHeader("X-User-Id")).thenReturn(String.valueOf(userId));
        return r;
    }

    /** 网关注入的身份 + 管理员角色（X-Role: 4）。 */
    private static HttpServletRequest asAdmin(long userId) {
        HttpServletRequest r = mock(HttpServletRequest.class);
        lenient().when(r.getHeader("X-User-Id")).thenReturn(String.valueOf(userId));
        lenient().when(r.getHeader("X-Role")).thenReturn("4");
        return r;
    }

    /** 没有任何注入头的裸请求（未登录 / 直连未过网关）。 */
    private static HttpServletRequest anonymous() {
        return mock(HttpServletRequest.class);
    }

    private static Course courseOwnedBy(long teacherId) {
        Course c = new Course();
        c.setId(7L);
        c.setTeacherId(teacherId);
        return c;
    }

    // ================================================== 放行面（闸不误伤）

    @Test
    @DisplayName("读本人的消息 / 未读数 ⇒ 放行")
    void ownReadsAllowed() {
        when(feedbackService.getFeedbacks(ME, null)).thenReturn(new ArrayList<>());

        assertEquals(200, controller.getMessages(String.valueOf(ME), as(ME)).getCode());
        assertEquals(200, controller.getUnreadCount(String.valueOf(ME), as(ME)).getCode());

        verify(feedbackService, times(2)).getFeedbacks(ME, null);
    }

    @Test
    @DisplayName("读本人工作台 / 待办 ⇒ 放行")
    void ownDashboardAndTodosAllowed() {
        when(teacherService.getByUserId(ME)).thenReturn(null);
        when(classInfoService.getClassesByTeacherId(ME)).thenReturn(new ArrayList<>());
        when(courseService.getCoursesByTeacherId(ME)).thenReturn(new ArrayList<>());
        when(applicationService.getApplicationsByTeacherId(ME)).thenReturn(new ArrayList<>());
        when(feedbackService.getFeedbacks(ME, null)).thenReturn(new ArrayList<>());

        assertEquals(200, controller.getDashboard(String.valueOf(ME), as(ME)).getCode());
        assertEquals(200, controller.getTodos(String.valueOf(ME), as(ME)).getCode());

        verify(teacherService).getByUserId(ME);
    }

    @Test
    @DisplayName("管理员（X-Role: 4）读任意教师 ⇒ 旁路放行")
    void adminBypassesReadGuard() {
        when(feedbackService.getFeedbacks(OTHER, null)).thenReturn(new ArrayList<>());

        assertEquals(200, controller.getMessages(String.valueOf(OTHER), asAdmin(1L)).getCode());
        verify(feedbackService).getFeedbacks(OTHER, null);
    }

    // ================================================== 拦截面

    @Test
    @DisplayName("★ 读别人的消息 ⇒ 403，且 **feedbackService 一次都没被调用**")
    void otherTeacherMessagesRejected() {
        ApiResponse<Object> res = controller.getMessages(String.valueOf(OTHER), as(ME));

        assertEquals(403, res.getCode());
        verify(feedbackService, never()).getFeedbacks(anyLong(), any());
    }

    @Test
    @DisplayName("★ 读别人的花名册（那条 246 行带姓名的）⇒ 403 且不触库")
    void otherTeacherEnrollmentsRejected() {
        ApiResponse<Object> res = controller.getEnrollments(OTHER, null, as(ME));

        assertEquals(403, res.getCode(), "换个数就拿到别人 246 行带姓名的选课花名册，正是本轮要收口的");
        verify(classInfoService, never()).getClassesByTeacherId(anyLong());
    }

    @Test
    @DisplayName("读别人的看板 / 学生 / 预警 / 待办 / 审计日志 / 反馈 / 未读数 ⇒ 一律 403 且不触库")
    void otherTeacherAllReadsRejected() {
        assertEquals(403, controller.getDashboard(String.valueOf(OTHER), as(ME)).getCode());
        assertEquals(403, controller.getStudents(String.valueOf(OTHER), as(ME)).getCode());
        assertEquals(403, controller.getWarnings(String.valueOf(OTHER), null, as(ME)).getCode());
        assertEquals(403, controller.getTodos(String.valueOf(OTHER), as(ME)).getCode());
        assertEquals(403, controller.getAuditLogs(String.valueOf(OTHER), 1, 20, as(ME)).getCode());
        assertEquals(403, controller.getScoreLogs(String.valueOf(OTHER), 1, 20, as(ME)).getCode());
        assertEquals(403, controller.getFeedbackList(String.valueOf(OTHER), "", as(ME)).getCode());
        assertEquals(403, controller.getUnreadCount(String.valueOf(OTHER), as(ME)).getCode());

        verify(teacherService, never()).getByUserId(anyLong());
        verify(classInfoService, never()).getClassesByTeacherId(anyLong());
        verify(feedbackService, never()).getFeedbacks(anyLong(), any());
    }

    @Test
    @DisplayName("没有登录身份 ⇒ 拒绝，fail-closed")
    void noIdentityRejected() {
        assertEquals(403, controller.getMessages(String.valueOf(ME), anonymous()).getCode());
        assertEquals(403, controller.getDashboard(String.valueOf(ME), anonymous()).getCode());

        verify(feedbackService, never()).getFeedbacks(anyLong(), any());
        verify(teacherService, never()).getByUserId(anyLong());
    }

    // ================================================== /enrollments 的两条分支（判据不同）

    @Test
    @DisplayName("★ courseId 分支看的是**课程归属**：别人的课 ⇒ 403（即使 teacherId 是空的）")
    void enrollmentsByCourseIdRequiresCourseOwnership() {
        when(courseService.getById(7L)).thenReturn(courseOwnedBy(OTHER));

        ApiResponse<Object> res = controller.getEnrollments(null, 7L, as(ME));

        assertEquals(403, res.getCode(), "teacherId 没传，闸必须落在'这门课是不是我教的'上");
        verify(classInfoService, never()).getStudentsByClassId(anyLong());
    }

    @Test
    @DisplayName("courseId 分支：本人所授的课 ⇒ 放行（负向对照：别把合法读也拦了）")
    void enrollmentsByOwnCourseIdAllowed() {
        when(courseService.getById(7L)).thenReturn(courseOwnedBy(ME));
        when(scoreRecordService.getScoresByCourseId(7L)).thenReturn(new ArrayList<>());

        assertEquals(200, controller.getEnrollments(null, 7L, as(ME)).getCode());
    }

    @Test
    @DisplayName("courseId 分支：课程不存在 ⇒ 403（不是 NPE 500）")
    void enrollmentsByMissingCourseRejected() {
        when(courseService.getById(7L)).thenReturn(null);

        assertEquals(403, controller.getEnrollments(null, 7L, as(ME)).getCode());
    }

    @Test
    @DisplayName("courseId 分支：管理员旁路 —— 课上挂着别人的名也放行（对照上面那条 403）")
    void enrollmentsByCourseIdAdminBypass() {
        // 与 enrollmentsByCourseIdRequiresCourseOwnership 的唯一差别就是 X-Role: 4，
        // 课还是别人的课 ⇒ 得 200，证明闸确实按角色旁路了，而不是碰巧没走到。
        when(courseService.getById(7L)).thenReturn(courseOwnedBy(OTHER));
        when(scoreRecordService.getScoresByCourseId(7L)).thenReturn(new ArrayList<>());

        assertEquals(200, controller.getEnrollments(null, 7L, asAdmin(1L)).getCode());
    }

    @Test
    @DisplayName("两个参数都不传 ⇒ 两条分支都不走，回空列表（闸不参与，行为不变）")
    void enrollmentsWithNoParamsUnchanged() {
        assertEquals(200, controller.getEnrollments(null, null, as(ME)).getCode());

        verify(courseService, never()).getById(anyLong());
        verify(classInfoService, never()).getClassesByTeacherId(anyLong());
    }

    // ================================================== 第二批（W11 收尾）：抽取器修好后浮出来的 7 条
    //
    // 这几条原先**根本没被扫到** —— 探针的 @RequestParam 抽取器只认带括号的
    // `@RequestParam(required = false)`，光秃秃的 `@RequestParam Long teacherId` 一条都抽不出来。
    // 修掉抽取器后它们立刻现身，且**全部未加闸**：教师 18 打 `?teacherId=29` 拿到对方的
    // 反馈、班级管理申请（带班级名、教师姓名）、班级列表、任课课程。

    /** 教师 18 的档案（users.id 18 ↔ teacher_profile.id 8）——查 `/{teacherId}/courses` 要用。 */
    private static TeacherProfile profileOf(long userId, long profileId) {
        TeacherProfile p = new TeacherProfile();
        p.setUserId(userId);
        p.setId(profileId);
        return p;
    }

    @Test
    @DisplayName("★ /{teacherId}/courses 收的是 **teacher_profile.id**：本人档案(8)放行、拿 users.id(18) 反而不行")
    void teacherCoursesUsesProfileIdSpace() {
        when(teacherService.getByUserId(ME)).thenReturn(profileOf(ME, 8L));
        when(teacherCourseService.getCoursesByTeacherId(8L)).thenReturn(new ArrayList<>());

        assertEquals(200, controller.getTeacherCourses(8L, as(ME)).getCode(), "本人档案 id 8 ⇒ 放行");
        // 负向对照：同一个人拿自己的 **users.id(18)** 去打 —— 18 不是他的档案 id ⇒ 必须拒。
        // 这条是"守卫选错 id 空间"的唯一探针：把 isSelfProfile 换成 isSelf，它仍会绿，
        // 但上面那句会红（18 会通过）—— 所以两句要一起看。
        assertEquals(403, controller.getTeacherCourses(ME, as(ME)).getCode(),
                "users.id(18) 不是档案 id(8) ⇒ 交换空间后必须拒");
    }

    @Test
    @DisplayName("/{teacherId}/courses：别人的档案 ⇒ 403；管理员 ⇒ 放行")
    void teacherCoursesOtherRejected() {
        when(teacherService.getByUserId(ME)).thenReturn(profileOf(ME, 8L));

        assertEquals(403, controller.getTeacherCourses(1L, as(ME)).getCode(), "档案 1 是于凤海(user 29)的");

        when(teacherCourseService.getCoursesByTeacherId(1L)).thenReturn(new ArrayList<>());
        assertEquals(200, controller.getTeacherCourses(1L, asAdmin(1L)).getCode());
    }

    @Test
    @DisplayName("★ /courses?teacher_id= 与 /feedbacks?teacherId= ⇒ 别人的一律 403（users.id 空间）")
    void courseListAndFeedbacksRejected() {
        assertEquals(403, controller.getCourseList(OTHER, as(ME)).getCode());
        assertEquals(403, controller.getFeedbacks(OTHER, null, as(ME)).getCode());

        verify(courseService, never()).getCoursesByTeacherId(anyLong());
        verify(feedbackService, never()).getFeedbacks(anyLong(), any());
    }

    @Test
    @DisplayName("/courses 不传 teacher_id ⇒ 走 courseService.list()（「全部课程」列表），照旧放行")
    void courseListWithoutTeacherIdUnchanged() {
        when(courseService.list()).thenReturn(new ArrayList<>());

        assertEquals(200, controller.getCourseList(null, as(ME)).getCode());
        verify(courseService, never()).getCoursesByTeacherId(anyLong());
    }

    @Test
    @DisplayName("班级管理三兄弟（requests / my-classes / applications）⇒ 别人的 403，本人的放行")
    void classManagementGuards() {
        assertEquals(403, controller.getMyClassManagementRequests(OTHER, as(ME)).getCode());
        assertEquals(403, controller.getMyClasses(OTHER, as(ME)).getCode());
        assertEquals(403, controller.getMyApplications(OTHER, as(ME)).getCode());

        verify(applicationService, never()).getApplicationsByTeacherId(anyLong());
        verify(classInfoService, never()).getClassesByTeacherId(anyLong());

        when(applicationService.getApplicationsByTeacherId(ME)).thenReturn(new ArrayList<>());
        when(classInfoService.getClassesByTeacherId(ME)).thenReturn(new ArrayList<>());

        assertEquals(200, controller.getMyClassManagementRequests(ME, as(ME)).getCode());
        assertEquals(200, controller.getMyClasses(ME, as(ME)).getCode());
        assertEquals(200, controller.getMyApplications(ME, as(ME)).getCode());
    }

    @Test
    @DisplayName("/scores/by-class：带别人的 teacher_id ⇒ 403；不带 ⇒ 按班级名查，放行")
    void scoresByClassGuard() {
        assertEquals(403, controller.getScoresByClass("计科2306班", OTHER, as(ME)).getCode());

        when(scoreRecordService.getScoresByClassName("计科2306班")).thenReturn(new ArrayList<>());
        assertEquals(200, controller.getScoresByClass("计科2306班", null, as(ME)).getCode());
    }
}
