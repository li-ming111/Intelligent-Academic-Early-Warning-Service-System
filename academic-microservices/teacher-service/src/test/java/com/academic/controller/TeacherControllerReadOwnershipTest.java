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
    /** W30：{@code /scores/by-class} 不传 teacher_id 那条分支的班级归属闸要查它。 */
    @Mock private com.academic.mapper.ClassInfoMapper classInfoMapper;
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

        assertEquals(200, controller.getMessages(ME, as(ME)).getCode());
        assertEquals(200, controller.getUnreadCount(ME, as(ME)).getCode());

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

        assertEquals(200, controller.getDashboard(ME, as(ME)).getCode());
        assertEquals(200, controller.getTodos(ME, as(ME)).getCode());

        verify(teacherService).getByUserId(ME);
    }

    @Test
    @DisplayName("管理员（X-Role: 4）读任意教师 ⇒ 旁路放行")
    void adminBypassesReadGuard() {
        when(feedbackService.getFeedbacks(OTHER, null)).thenReturn(new ArrayList<>());

        assertEquals(200, controller.getMessages(OTHER, asAdmin(1L)).getCode());
        verify(feedbackService).getFeedbacks(OTHER, null);
    }

    // ================================================== 拦截面

    @Test
    @DisplayName("★ 读别人的消息 ⇒ 403，且 **feedbackService 一次都没被调用**")
    void otherTeacherMessagesRejected() {
        ApiResponse<Object> res = controller.getMessages(OTHER, as(ME));

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
        assertEquals(403, controller.getDashboard(OTHER, as(ME)).getCode());
        assertEquals(403, controller.getStudents(OTHER, as(ME)).getCode());
        // W39 起多一个 onlyMine 参数；归属闸与它无关，两种取值都必须 403。
        assertEquals(403, controller.getWarnings(OTHER, null, false, as(ME)).getCode());
        assertEquals(403, controller.getWarnings(OTHER, null, true, as(ME)).getCode());
        assertEquals(403, controller.getTodos(OTHER, as(ME)).getCode());
        assertEquals(403, controller.getAuditLogs(OTHER, 1, 20, as(ME)).getCode());
        assertEquals(403, controller.getScoreLogs(OTHER, 1, 20, as(ME)).getCode());
        assertEquals(403, controller.getFeedbackList(OTHER, "", as(ME)).getCode());
        assertEquals(403, controller.getUnreadCount(OTHER, as(ME)).getCode());

        verify(teacherService, never()).getByUserId(anyLong());
        verify(classInfoService, never()).getClassesByTeacherId(anyLong());
        verify(feedbackService, never()).getFeedbacks(anyLong(), any());
    }

    // ---------------------------------------------------------- W39 onlyMine

    @Mock private com.academic.mapper.AcademicWarningMapper academicWarningMapper;
    @Mock private com.academic.mapper.AcademicWarningCourseMapper academicWarningCourseMapper;

    private static com.academic.common.entity.AcademicWarning warning(long id, String studentName) {
        com.academic.common.entity.AcademicWarning w = new com.academic.common.entity.AcademicWarning();
        w.setId(id);
        w.setStudentName(studentName);
        w.setWarningLevel(2);
        w.setStatus(0);
        return w;
    }

    private static java.util.Map<String, Object> courseRow(long warningId, long courseId, long teacherId) {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("warningId", warningId);
        m.put("courseId", courseId);
        m.put("courseName", "课程" + courseId);
        m.put("scoreTotal", 50.0);
        m.put("courseTeacherId", teacherId);
        return m;
    }

    @Test
    @DisplayName("★ onlyMine=true 只留「涉及我教的课」的预警；false 时两条都在")
    @SuppressWarnings("unchecked")
    void onlyMineFiltersToWarningsTouchingMyCourses() {
        // 老师 29 是 1 号班的班主任，班上有两条预警：
        //   501 —— 挂的课全是他自己教的；
        //   502 —— 挂的课是老师 22 教的（他看不到课，只是班是他的）。
        // 活库演示数据里**没有**这个形状（每位有预警的班主任恰好也教那些课），
        // 所以这条分支只能在这里钉住。
        long me = 29L;
        lenient().when(classInfoService.getClassesByTeacherId(me))
                .thenReturn(new ArrayList<>(List.of(java.util.Map.of("id", 1L, "name", "计科2306班"))));
        when(academicWarningMapper.findByClassId(1L))
                .thenReturn(List.of(warning(501L, "司明"), warning(502L, "韩坤")));
        when(academicWarningCourseMapper.selectByWarningIds(anyCollection()))
                .thenReturn(List.of(courseRow(501L, 1L, me), courseRow(502L, 6L, 22L)));
        // 归属过滤直连库：只有 501 的课程里出现了 me。
        when(academicWarningCourseMapper.selectWarningIdsInvolvingTeacher(eq(me), anyCollection()))
                .thenReturn(List.of(501L));

        ApiResponse<Object> all = controller.getWarnings(me, null, false, as(me));
        assertEquals(200, all.getCode());
        List<java.util.Map<String, Object>> allRows = (List<java.util.Map<String, Object>>) all.getData();
        assertEquals(2, allRows.size(), "不筛时必须保留原有班级范围（W39 没有收窄可见面）");
        assertEquals(Boolean.TRUE, allRows.stream()
                .filter(r -> Long.valueOf(501L).equals(r.get("id"))).findFirst().orElseThrow().get("involvesMyCourse"));
        assertEquals(Boolean.FALSE, allRows.stream()
                .filter(r -> Long.valueOf(502L).equals(r.get("id"))).findFirst().orElseThrow().get("involvesMyCourse"));

        ApiResponse<Object> mine = controller.getWarnings(me, null, true, as(me));
        List<java.util.Map<String, Object>> mineRows = (List<java.util.Map<String, Object>>) mine.getData();
        assertEquals(1, mineRows.size(), "onlyMine=true 只剩 501");
        assertEquals(501L, mineRows.get(0).get("id"));
        // 放行面必须断言行数 > 0：否则"参数没对上"和"闸生效"在输出里长得一样。
        assertEquals("课程1", mineRows.get(0).get("courseNames"));
    }

    @Test
    @DisplayName("没有登录身份 ⇒ 拒绝，fail-closed")
    void noIdentityRejected() {
        assertEquals(403, controller.getMessages(ME, anonymous()).getCode());
        assertEquals(403, controller.getDashboard(ME, anonymous()).getCode());

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
    @DisplayName("/scores/by-class：带别人的 teacher_id ⇒ 403，且不查库")
    void scoresByClassOthersIdDenied() {
        assertEquals(403, controller.getScoresByClass("计科2306班", OTHER, null, as(ME)).getCode());

        verify(scoreRecordService, never()).getScoresByClassNameAndTeacherId(anyString(), anyLong());
    }

    // ================================================== W30：不传 teacher_id 那条分支

    /** 演示库里唯一有成绩的班：{@code classes.id=1}，{@code teacher_id=29}（于凤海）。 */
    private static final String MY_CLASS_NAME = "计科2306班";
    private static final String OTHERS_CLASS_NAME = "软件工程2101班";

    @Test
    @DisplayName("★ /scores/by-class：带自己的 teacher_id 但班级不是本人的 ⇒ 403，且不查成绩")
    void scoresByClassOwnIdOthersClassDenied() {
        when(classInfoMapper.countClassesNamedAndOwned(OTHERS_CLASS_NAME, ME)).thenReturn(0);

        assertEquals(403, controller.getScoresByClass(OTHERS_CLASS_NAME, ME, null, as(ME)).getCode());

        verify(scoreRecordService, never()).getScoresByClassNameAndTeacherId(anyString(), anyLong());
    }

    @Test
    @DisplayName("/scores/by-class：带自己的 teacher_id 且班级是本人的 ⇒ 200")
    void scoresByClassOwnIdOwnClassAllowed() {
        when(classInfoMapper.countClassesNamedAndOwned(MY_CLASS_NAME, ME)).thenReturn(1);
        when(scoreRecordService.getScoresByClassNameAndTeacherId(MY_CLASS_NAME, ME)).thenReturn(new ArrayList<>());

        assertEquals(200, controller.getScoresByClass(MY_CLASS_NAME, ME, true, as(ME)).getCode());

        verify(scoreRecordService).getScoresByClassNameAndTeacherId(MY_CLASS_NAME, ME);
    }

    @Test
    @DisplayName("/scores/by-class：带自己的 teacher_id 但只有 only_mine=true 才按本人课程过滤，默认仍是全班")
    void scoresByClassOwnIdWithoutOnlyMineKeepsClassWide() {
        when(classInfoMapper.countClassesNamedAndOwned(MY_CLASS_NAME, ME)).thenReturn(1);
        when(scoreRecordService.getScoresByClassName(MY_CLASS_NAME)).thenReturn(new ArrayList<>());

        assertEquals(200, controller.getScoresByClass(MY_CLASS_NAME, ME, null, as(ME)).getCode());

        verify(scoreRecordService).getScoresByClassName(MY_CLASS_NAME);
        verify(scoreRecordService, never()).getScoresByClassNameAndTeacherId(anyString(), anyLong());
    }

    @Test
    @DisplayName("/scores/by-class：不传 teacher_id 且是本人的班 ⇒ 200（全班成绩视图保住）")
    void scoresByClassUnfilteredOwnClassAllowed() {
        when(classInfoMapper.countClassesNamedAndOwned(MY_CLASS_NAME, ME)).thenReturn(1);
        when(scoreRecordService.getScoresByClassName(MY_CLASS_NAME)).thenReturn(new ArrayList<>());

        assertEquals(200, controller.getScoresByClass(MY_CLASS_NAME, null, null, as(ME)).getCode());

        verify(scoreRecordService).getScoresByClassName(MY_CLASS_NAME);
    }

    @Test
    @DisplayName("⭐ /scores/by-class：不传 teacher_id 且不是本人的班 ⇒ 403（W30 之前是 200）")
    void scoresByClassUnfilteredOthersClassDenied() {
        when(classInfoMapper.countClassesNamedAndOwned(OTHERS_CLASS_NAME, ME)).thenReturn(0);

        assertEquals(403, controller.getScoresByClass(OTHERS_CLASS_NAME, null, null, as(ME)).getCode());

        verify(scoreRecordService, never()).getScoresByClassName(anyString());
    }

    @Test
    @DisplayName("/scores/by-class：不传 teacher_id 且班级不存在 ⇒ 403（fail-closed，不是放行）")
    void scoresByClassUnfilteredUnknownClassDenied() {
        when(classInfoMapper.countClassesNamedAndOwned("查无此班", ME)).thenReturn(0);

        assertEquals(403, controller.getScoresByClass("查无此班", null, null, as(ME)).getCode());

        verify(scoreRecordService, never()).getScoresByClassName(anyString());
    }

    @Test
    @DisplayName("/scores/by-class：不传 teacher_id 且没有身份头 ⇒ 403，且不查库")
    void scoresByClassUnfilteredAnonymousDenied() {
        assertEquals(403, controller.getScoresByClass(MY_CLASS_NAME, null, null, anonymous()).getCode());

        verify(classInfoMapper, never()).countClassesNamedAndOwned(anyString(), anyLong());
        verify(scoreRecordService, never()).getScoresByClassName(anyString());
    }

    @Test
    @DisplayName("/scores/by-class：班级名为空 ⇒ 403，且不查库")
    void scoresByClassUnfilteredBlankNameDenied() {
        assertEquals(403, controller.getScoresByClass("  ", null, null, as(ME)).getCode());

        verify(classInfoMapper, never()).countClassesNamedAndOwned(anyString(), anyLong());
    }

    @Test
    @DisplayName("/scores/by-class：管理员（X-Role: 4）不传 teacher_id ⇒ 放行且绕过归属查询")
    void scoresByClassUnfilteredAdminBypass() {
        when(scoreRecordService.getScoresByClassName(OTHERS_CLASS_NAME)).thenReturn(new ArrayList<>());

        assertEquals(200, controller.getScoresByClass(OTHERS_CLASS_NAME, null, null, asAdmin(4L)).getCode());

        verify(classInfoMapper, never()).countClassesNamedAndOwned(anyString(), anyLong());
    }

    // ======================================================================
    // W38（2026-10-10）：`GET /teachers/{id}` 之前**零闸** —— 任意教师可读他人档案
    // （name/title/collegeId）。路径参数是 `teacher_profile.id`，所以闸必须是
    // isSelfProfile（会先把调用者 users.id 换成本人 profile.id），用 isSelf 直接比
    // 会把教师本人也拦掉（于凤海 users.id=29 / profile.id=1）。
    // 被拒形状：HTTP 200 + body code:403（控制器业务拒绝）。
    // ======================================================================

    /** 本人 profile.id ⇒ 200（这条是"别把教师自己拦了"的钉子）。 */
    @Test
    @DisplayName("GET /teachers/{id}：读本人档案 ⇒ 放行")
    void teacherInfoSelfAllowed() {
        TeacherProfile mine = new TeacherProfile();
        mine.setId(1L);
        mine.setUserId(ME);
        mine.setName("刘教授");
        when(teacherService.getByUserId(ME)).thenReturn(mine);
        when(teacherService.getById(1L)).thenReturn(mine);

        assertEquals(200, controller.getTeacherInfo(1L, as(ME)).getCode());
        verify(teacherService).getById(1L);
    }

    /** 别人的 profile.id ⇒ 403，且**一次都没读到那条档案**。 */
    @Test
    @DisplayName("GET /teachers/{id}：读他人档案 ⇒ 403 且不查库")
    void teacherInfoOtherDenied() {
        TeacherProfile mine = new TeacherProfile();
        mine.setId(1L);
        mine.setUserId(ME);
        when(teacherService.getByUserId(ME)).thenReturn(mine);

        ApiResponse<TeacherProfile> r = controller.getTeacherInfo(2L, as(ME));

        assertEquals(403, r.getCode());
        verify(teacherService, never()).getById(anyLong());
    }

    /** 管理员旁路。 */
    @Test
    @DisplayName("GET /teachers/{id}：管理员 ⇒ 旁路放行")
    void teacherInfoAdminBypass() {
        when(teacherService.getById(2L)).thenReturn(new TeacherProfile());

        assertEquals(200, controller.getTeacherInfo(2L, asAdmin(4L)).getCode());
        verify(teacherService).getById(2L);
        verify(teacherService, never()).getByUserId(anyLong());
    }

    /** 无注入头的裸请求 ⇒ fail-closed 403。 */
    @Test
    @DisplayName("GET /teachers/{id}：匿名 ⇒ 403 且不查库")
    void teacherInfoAnonymousDenied() {
        assertEquals(403, controller.getTeacherInfo(1L, anonymous()).getCode());
        verify(teacherService, never()).getById(anyLong());
    }
}
