package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.common.entity.AcademicWarning;
import com.academic.common.entity.Course;
import com.academic.common.entity.ScoreRecord;
import com.academic.entity.ClassInfo;
import com.academic.entity.Feedback;
import com.academic.service.ClassInfoService;
import com.academic.service.CourseService;
import com.academic.service.FeedbackService;
import com.academic.service.ScoreRecordService;
import com.academic.service.TeacherClassApplicationService;
import com.academic.service.TeacherCourseService;
import com.academic.service.TeacherService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 教师自助面**资源 id 型**端点的归属闸（W12，2026-10-04）。
 *
 * <p><b>与 W11 那一轮的区别</b>：W11 收的是「属主 id 型」参数 —— 路径/查询参数里直接给的就是
 * {@code teacherId}（users.id），比一次就完事。那一轮实测 11 条越权，最狠的是
 * {@code /teachers/enrollments?teacherId=29} 拿到 246 行带姓名的花名册。
 *
 * <p>这一族不一样：路径里给的是一个**东西的 id** —— {@code course_id}、{@code classId}、
 * {@code warningId}、{@code feedbackId}、一批 {@code scoreId}。要知道"它是不是我的"
 * 必须**按 id 查一次库**（{@code courses.teacher_id} / {@code classes.teacher_id} /
 * {@code score_record.teacher_id} 都是 users.id；预警没有 teacher 列，得顺着
 * {@code academic_warning.student_id → student_profile.class_id → classes.teacher_id} 推）。
 *
 * <p><b>修之前是什么样</b>（备用端口直连实测，只读）：
 * {@code GET /teachers/courses/1/anomaly} —— 教师 18 打到**教师 29 的**课程 1，
 * 拿到 24 行「姓名 + 分数 + 不及格评语」；其余 course/class 型读端点同形。
 * 写侧更硬：{@code POST /warnings/7/process} 能把别人班学生的预警置为已处理并写进措施
 * （学生还会收到通知），{@code PUT /feedback/1/reply} 能以别人的名义回复学生反馈。
 *
 * <p><b>判据形状</b>：返回码 + {@code verify(..., never())} —— 被拦的那次**取数/写库压根不该发生**。
 * 另外专钉一条"课程不属于我却传我自己的 teacherId 也不放行"，防止闸门退化成 W11 那种只比属主 id。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TeacherController 资源 id 型端点归属闸（W12）")
class TeacherControllerResourceOwnershipTest {

    @Mock private ClassInfoService classInfoService;
    @Mock private TeacherClassApplicationService applicationService;
    @Mock private CourseService courseService;
    @Mock private TeacherService teacherService;
    @Mock private FeedbackService feedbackService;
    @Mock private ScoreRecordService scoreRecordService;
    @Mock private TeacherCourseService teacherCourseService;
    @Mock private com.academic.service.CommunicationRecordService communicationRecordService;
    @Mock private com.academic.service.ScoreAuditLogService scoreAuditLogService;
    @Mock private com.academic.mapper.ClassInfoMapper classInfoMapper;
    @Mock private com.academic.mapper.AcademicWarningMapper academicWarningMapper;
    @Mock private com.academic.mapper.ScoreRecordMapper scoreRecordMapper;
    @Mock private com.academic.mapper.CollegeMapper collegeMapper;
    @Mock private com.academic.common.notify.StudentNotifier studentNotifier;

    @InjectMocks private TeacherController controller;

    /** 教师 A：users.id=18（teacher001 刘教授）。 */
    private static final long ME = 18L;
    /** 教师 B：users.id=29（10001 于凤海）——"别人"。 */
    private static final long OTHER = 29L;

    /** 活库真值：课程 6 的 teacher_id=18（我的），课程 1/10 的 teacher_id=29（于凤海的）。 */
    private static final long MY_COURSE = 6L;
    private static final long OTHERS_COURSE = 1L;
    /** 活库真值：班级 31/32/33 的 teacher_id=18，班级 1/30 的 teacher_id=29。 */
    private static final long MY_CLASS = 31L;
    private static final long OTHERS_CLASS = 1L;

    // ================================================== 请求头

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

    // ================================================== 造资源

    private static Course courseOwnedBy(Long teacherId) {
        Course c = new Course();
        c.setId(MY_COURSE);
        c.setTeacherId(teacherId);
        return c;
    }

    private static ClassInfo classOwnedBy(long teacherId) {
        ClassInfo c = new ClassInfo();
        c.setId(MY_CLASS);
        c.setTeacherId(teacherId);
        return c;
    }

    /**
     * 一行成绩：{@code scores.id} + 所属课程 id。
     *
     * <p>W36 起归属判据是**课程**（{@code scores} 表没有 {@code teacher_id}），
     * 所以这里给的是 {@code courseId} 而不是 {@code teacherId}。
     */
    private static ScoreRecord scoreInCourse(long courseId) {
        ScoreRecord s = new ScoreRecord();
        s.setId(277L);
        s.setCourseId(courseId);
        return s;
    }

    private static Feedback feedbackOwnedBy(long teacherId) {
        Feedback f = new Feedback();
        f.setId(1L);
        f.setTeacherId(teacherId);
        return f;
    }

    // ================================================== 课程型读端点

    @Test
    @DisplayName("读本人所授课程的详情 / 分析 / 分布 ⇒ 放行（闸不误伤）")
    void ownCourseReadsAllowed() {
        when(courseService.getById(MY_COURSE)).thenReturn(courseOwnedBy(ME));
        when(scoreRecordService.getScoresByCourseId(anyLong())).thenReturn(List.of());

        assertEquals(200, controller.getCourseDetail(MY_COURSE, as(ME)).getCode());
        assertEquals(200, controller.getCourseScoreDistribution(MY_COURSE, as(ME)).getCode());
        assertEquals(200, controller.getAnalysis(MY_COURSE, as(ME)).getCode());

        verify(courseService, atLeastOnce()).getById(MY_COURSE);
    }

    @Test
    @DisplayName("★ 读**别人**课程的详情 / 分析 / 分布 / 学生 / 异常 / 推荐 ⇒ 一律 403")
    void othersCourseReadsRejected() {
        when(courseService.getById(MY_COURSE)).thenReturn(courseOwnedBy(OTHER));

        assertEquals(403, controller.getCourseDetail(MY_COURSE, as(ME)).getCode());
        assertEquals(403, controller.getAnalysis(MY_COURSE, as(ME)).getCode());
        assertEquals(403, controller.getCourseScoreDistribution(MY_COURSE, as(ME)).getCode());
        assertEquals(403, controller.getCourseStudents(MY_COURSE, 1, 20, as(ME)).getCode());
        assertEquals(403, controller.getCourseAnomalies(MY_COURSE, 60, as(ME)).getCode());
        assertEquals(403, controller.getCourseRecommendations(MY_COURSE, 5, as(ME)).getCode());
        assertEquals(403, controller.getScores(MY_COURSE, null, as(ME)).getCode());
    }

    @Test
    @DisplayName("★ 单单传对了 teacher_id 救不了：课程不是我的 ⇒ 仍 403（闸门认的是课程归属）")
    void ownTeacherIdDoesNotUnlockOthersCourse() {
        when(courseService.getById(OTHERS_COURSE)).thenReturn(courseOwnedBy(OTHER));

        // 拿**自己的** users.id 当 teacher_id 传进去 —— W11 那套"只比属主 id"的闸门会放行
        ApiResponse<Object> res = controller.getScores(OTHERS_COURSE, ME, as(ME));

        assertEquals(403, res.getCode(), "课程 1 属于教师 29，teacher_id=18 是自证清白的空话");
        verify(scoreRecordService, never()).getScoresByCourseId(anyLong());
    }

    @Test
    @DisplayName("★ 课程不存在 ⇒ 也 403（getCourseDetail 例外：那条有 404 契约）")
    void missingCourseRejected() {
        when(courseService.getById(999999L)).thenReturn(null);

        assertEquals(403, controller.getAnalysis(999999L, as(ME)).getCode());
        assertEquals(403, controller.getCourseAnomalies(999999L, 60, as(ME)).getCode());
        // 唯一保留 404 语义的一条：先把「课程不存在」如实说出来
        assertEquals(404, controller.getCourseDetail(999999L, as(ME)).getCode());
    }

    @Test
    @DisplayName("★ 导出成绩单：拒绝必须回**真 4xx + JSON 体**（前端 responseType:'blob'，200 会被存成文件）")
    void exportDeniedUsesRealHttpStatus() throws Exception {
        when(courseService.getById(OTHERS_COURSE)).thenReturn(courseOwnedBy(OTHER));
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        controller.exportScores(OTHERS_COURSE, as(ME), response);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(response).setContentType("application/json;charset=UTF-8");
        assertTrue(sw.toString().contains("只能导出本人所授课程的成绩单"), "实际写出：" + sw);
    }

    // ================================================== 班级型端点

    @Test
    @DisplayName("读本人所带班级的分析 / 学生名单 ⇒ 放行")
    void ownClassReadsAllowed() {
        ClassInfo mine = classOwnedBy(ME);
        mine.setName("软件工程2301");
        when(classInfoMapper.selectById(MY_CLASS)).thenReturn(mine);
        when(classInfoService.getClassById(MY_CLASS)).thenReturn(mine);
        when(classInfoService.getStudentsByClassId(MY_CLASS)).thenReturn(List.of());
        when(scoreRecordService.getScoresByClassName(anyString())).thenReturn(List.of());

        assertEquals(200, controller.getClassScoreAnalysis(MY_CLASS, as(ME)).getCode());
        assertEquals(200, controller.getClassStudents(MY_CLASS, as(ME)).getCode());
    }

    @Test
    @DisplayName("★ 读**别人**班级的分析 / 学生名单 ⇒ 403，且不触库")
    void othersClassReadsRejected() {
        when(classInfoMapper.selectById(OTHERS_CLASS)).thenReturn(classOwnedBy(OTHER));

        assertEquals(403, controller.getClassScoreAnalysis(OTHERS_CLASS, as(ME)).getCode());
        assertEquals(403, controller.getClassStudents(OTHERS_CLASS, as(ME)).getCode());
        verify(scoreRecordService, never()).getScoresByClassName(anyString());
    }

    // ================================================== 成绩写入面

    @Test
    @DisplayName("★ 往别人的课程里录分 ⇒ 403 且一行都不写（courseId 由客户端说了算，这是绕过路径）")
    void saveScoresToOthersCourseRejected() {
        when(courseService.getById(OTHERS_COURSE)).thenReturn(courseOwnedBy(OTHER));
        Map<String, Object> body = Map.of(
                "teacherId", ME,
                "scores", List.of(Map.of("studentId", 3, "courseId", OTHERS_COURSE, "score", 88)));

        assertEquals(403, controller.saveScores(body, as(ME)).getCode());

        verify(scoreRecordService, never()).saveScores(any(), any());
    }

    // ---- W19（2026-10-04）：`/scores/import` 从 JSON 换成 multipart，闸也随之换了一条 ----
    // 它不再共用 denyUnlessScoreWriteAllowed（那份请求体里没有 teacherId/scores 了），
    // 改为对请求参数 courseId 走 denyUnlessMyCourse。下面四条把新契约的两个方向都钉住。

    @Test
    @DisplayName("★ 向**别人**的课程导入成绩 ⇒ 403，且闸在读文件之前（传 null 文件也拦得住）")
    void importScoresToOthersCourseRejected() {
        when(courseService.getById(OTHERS_COURSE)).thenReturn(courseOwnedBy(OTHER));

        ApiResponse<Map<String, Object>> res = controller.importScores(null, OTHERS_COURSE, null, null, as(ME));

        assertEquals(403, res.getCode());
        verify(scoreRecordService, never()).saveScores(any(), any());
    }

    @Test
    @DisplayName("★ 无身份头导入成绩 ⇒ 403，且不写库")
    void importScoresAnonymousDenied() {
        assertEquals(403, controller.importScores(null, MY_COURSE, null, null, anonymous()).getCode());
        verify(scoreRecordService, never()).saveScores(any(), any());
    }

    @Test
    @DisplayName("本人课程 + 空文件 ⇒ 过闸但明确报错（不是 403、也不是静默成功）")
    void importScoresOwnCourseEmptyFileRejected() {
        when(courseService.getById(MY_COURSE)).thenReturn(courseOwnedBy(ME));

        ApiResponse<Map<String, Object>> res = controller.importScores(null, MY_COURSE, null, null, as(ME));

        assertNotEquals(403, res.getCode(), "自己的课程不该被闸拦住");
        assertTrue(String.valueOf(res.getMessage()).contains("文件为空"), "实际：" + res.getMessage());
        verify(scoreRecordService, never()).saveScores(any(), any());
    }

    @Test
    @DisplayName("★ 学期不是数字 ⇒ 拒绝，不写库（scores.semester 是 int 列，塞 \"2025-2026-1\" 会炸）")
    void importScoresNonNumericSemesterRejected() {
        when(courseService.getById(MY_COURSE)).thenReturn(courseOwnedBy(ME));

        ApiResponse<Map<String, Object>> res =
                controller.importScores(null, MY_COURSE, null, "2025-2026-1", as(ME));

        assertTrue(String.valueOf(res.getMessage()).contains("学期必须是数字"), "实际：" + res.getMessage());
        verify(scoreRecordService, never()).saveScores(any(), any());
    }

    @Test
    @DisplayName("★ 以别人的名义录分（teacherId 改成别人的）⇒ 403 且不写库")
    void saveScoresAsSomeoneElseRejected() {
        Map<String, Object> body = Map.of(
                "teacherId", OTHER,
                "scores", List.of(Map.of("studentId", 3, "courseId", MY_COURSE, "score", 88)));

        assertEquals(403, controller.saveScores(body, as(ME)).getCode());
        verify(scoreRecordService, never()).saveScores(any(), any());
    }

    @Test
    @DisplayName("★ 形状畸形（scores 不是列表 / 某行缺 courseId）⇒ 一律 403 而不是跳过（畸形必须是死路）")
    void malformedScoreBodyRejected() {
        // scores 是 Map 不是 List
        assertEquals(403, controller.saveScores(
                Map.of("teacherId", ME, "scores", Map.of("a", 1)), as(ME)).getCode());
        // 某行没有 courseId
        assertEquals(403, controller.saveScores(
                Map.of("teacherId", ME, "scores", List.of(Map.of("studentId", 3))), as(ME)).getCode());
        // 连 body 里都没有 teacherId
        assertEquals(403, controller.saveScores(
                Map.of("scores", List.of()), as(ME)).getCode());

        verify(scoreRecordService, never()).saveScores(any(), any());
    }

    @Test
    @DisplayName("★ 批量删分：只要有一条不是本人课上的，整批拒（不做「部分成功」）")
    void batchDeleteRequiresEveryRowMine() {
        when(scoreRecordService.getById(277L)).thenReturn(scoreInCourse(MY_COURSE));
        when(scoreRecordService.getById(278L)).thenReturn(scoreInCourse(OTHERS_COURSE));
        when(courseService.getById(MY_COURSE)).thenReturn(courseOwnedBy(ME));
        when(courseService.getById(OTHERS_COURSE)).thenReturn(courseOwnedBy(OTHER));

        assertEquals(403, controller.batchDeleteScores(List.of(277L, 278L), as(ME)).getCode());
        // 查不到的行也算"不是本人的"
        assertEquals(403, controller.batchDeleteScores(List.of(999999L), as(ME)).getCode());
        // 空列表 / null 不算通过
        assertEquals(403, controller.batchDeleteScores(List.of(), as(ME)).getCode());
        assertEquals(403, controller.batchDeleteScores(null, as(ME)).getCode());

        verify(scoreRecordService, never()).removeByIds(any());
    }

    // ================================================== 预警 / 反馈

    @Test
    @DisplayName("★ 处理别人班学生的预警 ⇒ 403 且不写措施、不发通知")
    void processOthersWarningRejected() {
        when(academicWarningMapper.findByIdOwnedByTeacher(7L, ME)).thenReturn(null);

        assertEquals(403, controller.processWarning(7L, Map.of("measures", "已谈话"), as(ME)).getCode());

        verify(academicWarningMapper, never()).selectById(anyLong());
    }

    @Test
    @DisplayName("★ 以别人的名义回复学生反馈 ⇒ 403 且不写库")
    void replyOthersFeedbackRejected() {
        when(feedbackService.getFeedbackById(1L)).thenReturn(feedbackOwnedBy(OTHER));

        assertEquals(403, controller.replyFeedback(1L, Map.of("reply", "已处理"), as(ME)).getCode());
        verify(feedbackService, never()).replyFeedback(anyLong(), any());
    }

    @Test
    @DisplayName("回复本人的反馈 ⇒ 放行（闸不误伤）")
    void replyOwnFeedbackAllowed() {
        when(feedbackService.getFeedbackById(1L)).thenReturn(feedbackOwnedBy(ME));

        assertEquals(200, controller.replyFeedback(1L, Map.of("reply", "已处理"), as(ME)).getCode());
        verify(feedbackService).replyFeedback(1L, "已处理");
    }

    // ================================================== 课程创建 / 修改

    @Test
    @DisplayName("★ 建课时把 teacherId 写成别人 ⇒ 403 且不落库")
    void createCourseAsOthersRejected() {
        Course c = new Course();
        c.setName("蹭课");
        c.setTeacherId(OTHER);

        assertEquals(403, controller.createCourse(c, as(ME)).getCode());
        verify(courseService, never()).createCourse(any());
    }

    @Test
    @DisplayName("★ 改**别人已认领**的课程 ⇒ 403（PC「关联课程」不再能抢占别人的课）")
    void updateOthersOwnedCourseRejected() {
        Course patch = new Course();
        patch.setName("改个名");
        when(courseService.getById(MY_COURSE)).thenReturn(courseOwnedBy(OTHER));

        assertEquals(403, controller.updateCourse(MY_COURSE, patch, as(ME)).getCode());
        verify(courseService, never()).updateCourse(any());
    }

    @Test
    @DisplayName("改**没人认领**的课程 ⇒ 放行（课程目录里 teacher_id 为 NULL 的那些还能被关联）")
    void updateUnownedCourseAllowed() {
        Course patch = new Course();
        patch.setName("改个名");
        when(courseService.getById(MY_COURSE)).thenReturn(courseOwnedBy(null));
        when(courseService.updateCourse(any())).thenReturn(true);

        assertEquals(200, controller.updateCourse(MY_COURSE, patch, as(ME)).getCode());
        verify(courseService).updateCourse(any());
    }

    @Test
    @DisplayName("★ 改自己的课时把 body 里的 teacherId 写成别人 ⇒ 403 且不落库（不能借改名把课过户）")
    void updateCourseTransferToOtherRejected() {
        Course patch = new Course();
        patch.setName("改个名");
        patch.setTeacherId(OTHER);
        when(courseService.getById(MY_COURSE)).thenReturn(courseOwnedBy(ME));

        assertEquals(403, controller.updateCourse(MY_COURSE, patch, as(ME)).getCode());
        verify(courseService, never()).updateCourse(any());
    }

    @Test
    @DisplayName("认领没人认领的课程：body 里的 teacherId 写自己 ⇒ 放行")
    void updateUnownedCourseClaimSelfAllowed() {
        Course patch = new Course();
        patch.setName("改名并认领");
        patch.setTeacherId(ME);
        when(courseService.getById(MY_COURSE)).thenReturn(courseOwnedBy(null));
        when(courseService.updateCourse(any())).thenReturn(true);

        assertEquals(200, controller.updateCourse(MY_COURSE, patch, as(ME)).getCode());
        verify(courseService).updateCourse(any());
    }

    // ================================================== 身份缺陷与旁路

    @Test
    @DisplayName("没有登录身份 ⇒ 课程/班级/成绩/预警四条闸全 fail-closed")
    void anonymousRejected() {
        when(courseService.getById(MY_COURSE)).thenReturn(courseOwnedBy(ME));
        when(classInfoMapper.selectById(MY_CLASS)).thenReturn(classOwnedBy(ME));

        assertEquals(403, controller.getAnalysis(MY_COURSE, anonymous()).getCode());
        assertEquals(403, controller.getClassStudents(MY_CLASS, anonymous()).getCode());
        assertEquals(403, controller.batchDeleteScores(List.of(277L), anonymous()).getCode());
        assertEquals(403, controller.processWarning(7L, Map.of(), anonymous()).getCode());
    }

    @Test
    @DisplayName("管理员（X-Role: 4）⇒ 旁路放行（后台代查/代管不断）")
    void adminBypassesResourceGuard() {
        when(courseService.getById(MY_COURSE)).thenReturn(courseOwnedBy(OTHER));
        when(scoreRecordService.getScoresByCourseId(MY_COURSE)).thenReturn(List.of());

        assertEquals(200, controller.getCourseDetail(MY_COURSE, asAdmin(1L)).getCode());
        assertEquals(200, controller.getAnalysis(MY_COURSE, asAdmin(1L)).getCode());
    }

    @Test
    @DisplayName("闸门在取数**之前**：被拒时课程/班级/成绩的取数方法一次都没碰")
    void guardRunsBeforeFetch() {
        when(courseService.getById(MY_COURSE)).thenReturn(courseOwnedBy(OTHER));
        when(classInfoMapper.selectById(MY_CLASS)).thenReturn(classOwnedBy(OTHER));

        controller.getCourseScoreDistribution(MY_COURSE, as(ME));
        controller.getClassScoreAnalysis(MY_CLASS, as(ME));

        // 归属查询本身各一次（那是必须的），真正的取数一次都不该有
        verify(courseService, times(1)).getById(MY_COURSE);
        verify(scoreRecordService, never()).getScoresByCourseId(anyLong());
        verify(scoreRecordService, never()).getScoresByClassName(anyString());
        verify(classInfoService, never()).getStudentsByClassId(anyLong());
    }

    // ================================================== W18：两条「跨学生」越权读（2026-10-04）

    /**
     * 活库真值（写在用例里，防止有人把它"顺手改成"一个恰好能过的数）：
     * 司明的 {@code student_profile.id} = 3，落在 class 1（计科2306班，班主任 users.id=29 于凤海）。
     * 所以：本人是 29（OTHER 常量）就能看，本人是 18（ME 常量）不能看。
     */
    private static final long STUDENT = 3L;

    /** 司明在课程 1（高等数学，teacher_id=29）有成绩；课程 6（操作系统）是 18 的。 */
    private static ScoreRecord studentRowInCourse(long studentId, double score) {
        ScoreRecord r = new ScoreRecord();
        r.setStudentId(studentId);
        r.setScore(score);
        r.setStudentName("司明");
        return r;
    }

    @Test
    @DisplayName("★ W18 学分预测：读**别人班**的学生 ⇒ 403，且统计查询一次都没发生")
    void creditPrediction_otherClassStudent_denied() {
        when(classInfoMapper.findClassIdOfProfile(STUDENT)).thenReturn(OTHERS_CLASS);
        when(classInfoMapper.selectById(OTHERS_CLASS)).thenReturn(classOwnedBy(OTHER));

        assertEquals(403, controller.getCreditPrediction(String.valueOf(STUDENT), as(ME)).getCode(),
                "教师 18 读于凤海班里的司明，必须被拒");
        verify(scoreRecordMapper, never()).selectStudentStats(anyLong());
        verify(scoreRecordMapper, never()).selectStudentName(anyLong());
    }

    @Test
    @DisplayName("★ W18 学分预测：读**本人班**的学生 ⇒ 放行（防过度拦截）")
    void creditPrediction_ownClassStudent_allowed() {
        when(classInfoMapper.findClassIdOfProfile(STUDENT)).thenReturn(MY_CLASS);
        when(classInfoMapper.selectById(MY_CLASS)).thenReturn(classOwnedBy(ME));
        when(scoreRecordMapper.selectStudentStats(STUDENT))
                .thenReturn(Map.of("completedCredits", 10, "totalCourses", 3, "failedCourses", 1));

        assertEquals(200, controller.getCreditPrediction(String.valueOf(STUDENT), as(ME)).getCode(),
                "本人班里的学生必须能读");
    }

    @Test
    @DisplayName("★ W18 学分预测：学生没有班级（class_id 为 NULL）⇒ 拒绝，不因查不到班而放行")
    void creditPrediction_studentWithoutClass_denied() {
        when(classInfoMapper.findClassIdOfProfile(99L)).thenReturn(null);

        assertEquals(403, controller.getCreditPrediction("99", as(ME)).getCode());
        verify(scoreRecordMapper, never()).selectStudentStats(anyLong());
    }

    @Test
    @DisplayName("W18 学分预测：无身份 ⇒ fail-closed（连班级都不查）")
    void creditPrediction_anonymousDenied() {
        assertEquals(403, controller.getCreditPrediction(String.valueOf(STUDENT), anonymous()).getCode());
        verify(classInfoMapper, never()).findClassIdOfProfile(anyLong());
    }

    @Test
    @DisplayName("W18 学分预测：管理员（X-Role: 4）⇒ 旁路放行")
    void creditPrediction_adminAllowed() {
        when(scoreRecordMapper.selectStudentStats(STUDENT))
                .thenReturn(Map.of("completedCredits", 1, "totalCourses", 1, "failedCourses", 0));

        assertEquals(200, controller.getCreditPrediction(String.valueOf(STUDENT), asAdmin(1L)).getCode(),
                "管理员代查必须放行");
        verify(classInfoMapper, never()).findClassIdOfProfile(anyLong());
    }

    @Test
    @DisplayName("★ W18 单课分析：course_id 是**别人**的课 ⇒ 403，且该课成绩一次都没查")
    void analyze_othersCourse_denied() {
        when(courseService.getById(OTHERS_COURSE)).thenReturn(courseOwnedBy(OTHER));

        assertEquals(403, controller.analyzeStudentScore(STUDENT, OTHERS_COURSE, as(ME)).getCode(),
                "教师 18 拿于凤海的课程 1 分析学生，必须被拒");
        verify(scoreRecordService, never()).getScoresByCourseId(anyLong());
    }

    @Test
    @DisplayName("★ W18 单课分析：course_id 是本人的课 ⇒ 放行（防过度拦截）")
    void analyze_ownCourse_allowed() {
        when(courseService.getById(MY_COURSE)).thenReturn(courseOwnedBy(ME));
        when(scoreRecordService.getScoresByCourseId(MY_COURSE))
                .thenReturn(List.of(studentRowInCourse(STUDENT, 56.5)));
        when(scoreRecordMapper.selectFailedCourseCount(STUDENT)).thenReturn(1);

        assertEquals(200, controller.analyzeStudentScore(STUDENT, MY_COURSE, as(ME)).getCode());
    }

    @Test
    @DisplayName("W18 单课分析：无身份 ⇒ fail-closed")
    void analyze_anonymousDenied() {
        assertEquals(403, controller.analyzeStudentScore(STUDENT, MY_COURSE, anonymous()).getCode());
    }

    // ================================================== W44：body 里带 teacherId 的写端点

    /** 沟通记录请求体：`teacherId` 由客户端说了算。 */
    private static Map<String, Object> communicationBody(long teacherId) {
        return Map.of("teacherId", String.valueOf(teacherId), "studentId", "3",
                "content", "W44", "type", "notify_counselor", "studentName", "司明");
    }

    /** 班级申请请求体：同上。 */
    private static Map<String, Object> applyBody(long teacherId, long classId) {
        return Map.of("teacherId", String.valueOf(teacherId), "classId", String.valueOf(classId),
                "reason", "W44", "className", "X");
    }

    @Test
    @DisplayName("★ W44 沟通记录：以**别人**的名义写（body 里 teacherId=别人）⇒ 403 且一行都不写")
    void saveCommunication_othersName_denied() {
        assertEquals(403, controller.saveCommunication(communicationBody(OTHER), as(ME)).getCode(),
                "教师 18 用 teacherId=29 写沟通记录，必须被拒 —— 此前照单全收，实测真写进 teacher_id=18");
        verify(communicationRecordService, never()).saveCommunication(anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("W44 沟通记录：以自己的名义、学生也在本人班上 ⇒ 放行（闸不误伤）")
    void saveCommunication_ownName_allowed() {
        when(classInfoMapper.findClassIdOfProfile(3L)).thenReturn(MY_CLASS);
        when(classInfoMapper.selectById(MY_CLASS)).thenReturn(classOwnedBy(ME));

        assertEquals(200, controller.saveCommunication(communicationBody(ME), as(ME)).getCode());
        verify(communicationRecordService).saveCommunication(eq(ME), eq(3L), eq("W44"), eq("notify_counselor"), eq("司明"));
    }

    @Test
    @DisplayName("★ W44 沟通记录：学生不在本人班上（studentId=别班学生）⇒ 403 且不写库")
    void saveCommunication_othersStudent_denied() {
        when(classInfoMapper.findClassIdOfProfile(3L)).thenReturn(OTHERS_CLASS);
        when(classInfoMapper.selectById(OTHERS_CLASS)).thenReturn(classOwnedBy(OTHER));

        assertEquals(403, controller.saveCommunication(communicationBody(ME), as(ME)).getCode(),
                "teacherId 是自己的只证明「以自己的名义」，不证明「这个学生归你」");
        verify(communicationRecordService, never()).saveCommunication(anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("W44 沟通记录：无身份头 ⇒ fail-closed，不写库")
    void saveCommunication_anonymousDenied() {
        assertEquals(403, controller.saveCommunication(communicationBody(ME), anonymous()).getCode());
        verify(communicationRecordService, never()).saveCommunication(anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("★ W44 班级申请：以**别人**的名义提交（body 里 teacherId=别人）⇒ 403 且不写库")
    void submitClassApplication_othersName_denied() {
        assertEquals(403, controller.submitClassManagementRequest(applyBody(OTHER, MY_CLASS), as(ME)).getCode(),
                "教师 18 用 teacherId=29 提交申请，必须被拒 —— 批准后班就记在他名下了");
        verify(applicationService, never()).submitApplication(anyLong(), anyLong(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("★ W44 解除班级管理：以**别人**的身份把班置空 ⇒ 403 且不写库（三条里最重的一条）")
    void cancelClassManagement_othersName_denied() {
        assertEquals(403, controller.cancelClassManagement(applyBody(OTHER, MY_CLASS), as(ME)).getCode(),
                "教师 18 用 teacherId=29 解除班级管理，必须被拒 —— 这是当场写库的操作");
        verify(applicationService, never()).cancelClassManagement(anyLong(), anyLong());
    }

    @Test
    @DisplayName("W44 解除班级管理：以自己身份 ⇒ 放行（服务层那道闸才判得下去）")
    void cancelClassManagement_ownName_allowed() {
        when(applicationService.cancelClassManagement(ME, MY_CLASS)).thenReturn(null);

        assertEquals(200, controller.cancelClassManagement(applyBody(ME, MY_CLASS), as(ME)).getCode());
        verify(applicationService).cancelClassManagement(ME, MY_CLASS);
    }

    @Test
    @DisplayName("W44 三条写端点：管理员（X-Role: 4）⇒ 旁路放行")
    void bodyTeacherIdWrites_adminBypass() {
        when(applicationService.cancelClassManagement(OTHER, MY_CLASS)).thenReturn(null);

        assertEquals(200, controller.saveCommunication(communicationBody(OTHER), asAdmin(1L)).getCode());
        assertEquals(200, controller.cancelClassManagement(applyBody(OTHER, MY_CLASS), asAdmin(1L)).getCode());
    }
}
