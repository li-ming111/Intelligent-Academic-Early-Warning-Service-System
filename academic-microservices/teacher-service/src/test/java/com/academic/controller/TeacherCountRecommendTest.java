package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.common.entity.Course;
import com.academic.common.entity.ScoreRecord;
import com.academic.service.ClassInfoService;
import com.academic.service.CourseService;
import com.academic.service.FeedbackService;
import com.academic.service.ScoreRecordService;
import com.academic.service.TeacherClassApplicationService;
import com.academic.service.TeacherCourseService;
import com.academic.service.TeacherService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * W15（2026-10-04）教师侧两条「参数/失败说了谎」的收口。
 *
 * <p><b>一、{@code GET /teachers/count-by-college} 的 catch 吞异常</b>：原来
 * {@code catch (Exception e) { return ApiResponse.success(0L); }} —— 查库炸了与
 * "这个学院没有教师"回**同一个 200 + 0**，调用方会把一次故障显示成一个可信的 0
 * （本仓反复记过的「恒 0 的 total」那一族；W14 已在 {@code StudentController} 修过同形的）。
 * 现在如实回错误。判据：{@code isSuccess()==false} 且 {@code data==null}
 * —— 这一条在修前必红（修前是 success 且 data=0）。
 *
 * <p><b>二、{@code GET /courses/{courseId}/recommendations} 的 {@code limit} 是摆设</b>：
 * 声明了 {@code Integer limit}（默认 5）却从不生效。现在它真的是返回条数的上限。
 * 用"低分建议 + 优秀建议共 2 条"做靶：{@code limit=1} 必须只回 1 条。
 *
 * <p><b>三、W21：两条建议都必须由数据触发。</b>此前「增加实践环节」是**无条件**拼上去的
 * （与课程数据无关，全班 90+ 或课上与编程毫不相干时也照给），所以这个端点**恒回 2 条**。
 * 现在它能回 0 / 1 / 2 条，完全取决于成绩分布 —— 判据是三条"该不出就不出"的负例。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TeacherController —— countByCollege 失败不伪装成 0 + 推荐 limit 真生效（W15）")
class TeacherCountRecommendTest {

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

    /** 活库真值：课程 6 的 teacher_id=18（teacher001 刘教授）。 */
    private static final long ME = 18L;
    private static final long MY_COURSE = 6L;

    private static HttpServletRequest as(long userId) {
        HttpServletRequest r = mock(HttpServletRequest.class);
        lenient().when(r.getHeader("X-User-Id")).thenReturn(String.valueOf(userId));
        return r;
    }

    // ============================ 一、countByCollege ============================

    @Test
    @DisplayName("★ 统计炸了 ⇒ 如实回错误，不再伪装成 success(0)（修前必红）")
    void countByCollegeFailureIsNotZero() {
        when(teacherService.countByCollege(2L)).thenThrow(new RuntimeException("db down"));

        ApiResponse<Long> resp = controller.countByCollege(2L);

        assertFalse(resp.isSuccess(), "查库失败不许回成功: code=" + resp.getCode());
        assertNotEquals(200, resp.getCode());
        assertNull(resp.getData(), "不许回 0 冒充「这个学院没有教师」");
    }

    @Test
    @DisplayName("统计成功 ⇒ 正常返回条数（不误伤）")
    void countByCollegeOk() {
        when(teacherService.countByCollege(2L)).thenReturn(7L);

        ApiResponse<Long> resp = controller.countByCollege(2L);

        assertTrue(resp.isSuccess());
        assertEquals(7L, resp.getData());
    }

    // ==================== 二、getCourseRecommendations 的 limit ====================

    /** 我的课程 + 一份成绩单（每人一行）。{@code (学生id, 分数)} 变长参数，null 表示未录入。 */
    private void stubMyCourse(Object... idScorePairs) {
        Course c = new Course();
        c.setId(MY_COURSE);
        c.setTeacherId(ME);
        when(courseService.getById(MY_COURSE)).thenReturn(c);

        List<ScoreRecord> rows = new java.util.ArrayList<>();
        for (int i = 0; i < idScorePairs.length; i += 2) {
            ScoreRecord r = new ScoreRecord();
            r.setId((long) (i / 2 + 1));
            r.setCourseId(MY_COURSE);
            r.setStudentId(((Number) idScorePairs[i]).longValue());
            r.setScore((Double) idScorePairs[i + 1]); // 可空 ⇒ 走"未录入"分支
            rows.add(r);
        }
        when(scoreRecordService.getScoresByCourseId(MY_COURSE)).thenReturn(rows);
    }

    /** 我的课程 + 一条不及格(50) + 一条优秀(95) ⇒ 两条建议**都**由数据触发，共 2 条。 */
    private void stubMyCourseMixed() {
        stubMyCourse(101L, 50.0, 102L, 95.0);
    }

    @Test
    @DisplayName("★ limit 真生效：共 2 条建议，limit=1 只回 1 条（修前必红）")
    void recommendationLimitCutsRows() {
        stubMyCourseMixed();

        ApiResponse<Object> one = controller.getCourseRecommendations(MY_COURSE, 1, as(ME));

        assertTrue(one.isSuccess());
        assertEquals(1, ((List<?>) one.getData()).size(), "limit=1 必须只回 1 条");
    }

    @Test
    @DisplayName("不传 limit（默认 5）⇒ 两条都回（不误伤）")
    void recommendationDefaultKeepsAll() {
        stubMyCourseMixed();

        ApiResponse<Object> all = controller.getCourseRecommendations(MY_COURSE, 5, as(ME));

        assertTrue(all.isSuccess());
        assertEquals(2, ((List<?>) all.getData()).size());
    }

    // ==================== 三、W21：两条建议都必须由数据触发 ====================

    /** 取回的建议标题集合。 */
    @SuppressWarnings("unchecked")
    private static List<String> titlesof(ApiResponse<Object> resp) {
        return ((List<Map<String, Object>>) resp.getData()).stream()
                .map(r -> String.valueOf(r.get("title")))
                .collect(java.util.stream.Collectors.toList());
    }

    @Test
    @DisplayName("★ 没有优秀学生 ⇒ 不出「增加实践环节」（修前必红：修前它恒在）")
    void practiceAdviceNeedsExcellentStudent() {
        // 只有不及格、没有优秀 ⇒ 只该出"加强基础"，**不该**出现"增加实践环节"
        stubMyCourse(101L, 50.0, 102L, 58.0);

        ApiResponse<Object> resp = controller.getCourseRecommendations(MY_COURSE, 5, as(ME));

        assertTrue(resp.isSuccess());
        List<String> titles = titlesof(resp);
        assertEquals(List.of("加强基础知识点教学"), titles,
                "全班无一人达到优秀线，不该建议「增加实践环节」（修前无条件返回 2 条）");
    }

    @Test
    @DisplayName("没有不及格学生 ⇒ 不出「加强基础知识点教学」（确认两条对称，都看数据）")
    void basicAdviceNeedsLowScoreStudent() {
        stubMyCourse(101L, 95.0, 102L, 88.0);

        ApiResponse<Object> resp = controller.getCourseRecommendations(MY_COURSE, 5, as(ME));

        assertTrue(resp.isSuccess());
        assertEquals(List.of("增加实践环节"), titlesof(resp));
    }

    @Test
    @DisplayName("既无不及格也无优秀(全在 60~89) ⇒ 返回空数组，不是「恒有的两条」")
    void noAdviceWhenNothingNoteworthy() {
        stubMyCourse(101L, 75.0, 102L, 80.0);

        ApiResponse<Object> resp = controller.getCourseRecommendations(MY_COURSE, 5, as(ME));

        assertTrue(resp.isSuccess());
        assertTrue(((List<?>) resp.getData()).isEmpty(),
                "既无挂科也无优秀 ⇒ 不该编出建议来（修前必回 2 条）");
    }

    @Test
    @DisplayName("未录入(null)不计入任何一类：不算挂科、也不算优秀")
    void nullScoreCountsNeitherWay() {
        stubMyCourse(101L, null, 102L, null);

        ApiResponse<Object> resp = controller.getCourseRecommendations(MY_COURSE, 5, as(ME));

        assertTrue(resp.isSuccess());
        assertTrue(((List<?>) resp.getData()).isEmpty(), "未录入≠0 分，不该产出任何建议");
    }

    @Test
    @DisplayName("文案里的学生人数是真算出来的（不是写死的）")
    void adviceTextCarriesRealCounts() {
        stubMyCourse(101L, 50.0, 102L, 55.0, 103L, 90.0, 104L, 99.0, 105L, 70.0);

        ApiResponse<Object> resp = controller.getCourseRecommendations(MY_COURSE, 5, as(ME));

        List<String> descs = ((List<Map<String, Object>>) resp.getData()).stream()
                .map(r -> String.valueOf(r.get("description")))
                .collect(java.util.stream.Collectors.toList());
        assertTrue(descs.stream().anyMatch(d -> d.contains("2名学生成绩不及格")), "低分人数应为 2：" + descs);
        assertTrue(descs.stream().anyMatch(d -> d.contains("2名学生成绩优秀")), "优秀人数应为 2：" + descs);
    }
}
