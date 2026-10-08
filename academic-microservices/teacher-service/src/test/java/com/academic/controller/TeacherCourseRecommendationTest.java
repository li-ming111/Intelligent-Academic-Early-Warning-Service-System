package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.common.entity.Course;
import com.academic.common.entity.CourseRecommendation;
import com.academic.entity.ClassInfo;
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
import org.mockito.ArgumentCaptor;
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
 * 教师「推荐选课」真实现（W14，2026-10-04）。
 *
 * <p><b>修之前是什么样</b>：{@code POST /teachers/courses/recommend} 是个**纯回声桩** ——
 * 把入参原样打包回来、一行都不落库，却回一句「课程推荐成功，学生将收到通知」；
 * 而前端 {@code TeacherCourses.vue} 只发 {@code {courseId, reason}}，
 * 后端第一句就是 {@code data.get("teacherId").toString()} ⇒ **NPE**，正常操作必然报错。
 * 库里也没有任何推荐表，学生侧没有任何出口。
 *
 * <p><b>这一轮钉四件事</b>：
 * <br>① 真落库 —— {@code insert} 的实体里 {@code teacherId/courseId/reason/status} 都对；
 * <br>② **身份取头不取体** —— 请求体里塞 {@code teacherId: 18} 也改不了"记录里是谁发的"，
 *     否则等于"可以以别人的名义推荐"（本仓 W13 在辅导员端刚栽过同形的坑）；
 * <br>③ 归属 —— 只能推荐**本人所授课程**、只能推荐给**本人所带的班**；
 * <br>④ 400/403/404 三种"没做成"要分得开（缺参数 / 不是你的 / 不存在），
 *     且**任何一种都不许落库**。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TeacherController 推荐选课（W14）")
class TeacherCourseRecommendationTest {

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
    @Mock private com.academic.mapper.CourseRecommendationMapper courseRecommendationMapper;
    @Mock private com.academic.common.notify.StudentNotifier studentNotifier;

    @InjectMocks private TeacherController controller;

    /** 于凤海：users.id=29，教课程 1，带班级 1（活库真值）。 */
    private static final long ME = 29L;
    /** 刘教授：users.id=18，教课程 6，带班级 31。 */
    private static final long OTHER = 18L;
    private static final long MY_COURSE = 1L;
    private static final long OTHERS_COURSE = 6L;
    private static final long MY_CLASS = 1L;
    private static final long OTHERS_CLASS = 31L;

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

    private static Course courseOf(long courseId, Long teacherId) {
        Course c = new Course();
        c.setId(courseId);
        c.setName("课程" + courseId);
        c.setTeacherId(teacherId);
        return c;
    }

    private static ClassInfo classOf(long classId, Long teacherId) {
        ClassInfo c = new ClassInfo();
        c.setId(classId);
        c.setTeacherId(teacherId);
        return c;
    }

    private static Map<String, Object> body(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    private CourseRecommendation captureInsert() {
        ArgumentCaptor<CourseRecommendation> cap = ArgumentCaptor.forClass(CourseRecommendation.class);
        verify(courseRecommendationMapper).insert(cap.capture());
        return cap.getValue();
    }

    // ============================================ 1. 真落库

    @Test
    @DisplayName("★ 推荐本人所授课程 ⇒ 200，且真写进 course_recommendation（teacherId 取自 X-User-Id）")
    void recommendOwnCoursePersists() {
        when(courseService.getById(MY_COURSE)).thenReturn(courseOf(MY_COURSE, ME));
        when(courseRecommendationMapper.countRecipients(ME, null)).thenReturn(24);

        ApiResponse<Object> res = controller.recommendCourse(
                body("courseId", MY_COURSE, "reason", "这门课对你们的专业方向很有用"), as(ME));

        assertEquals(200, res.getCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) res.getData();
        assertEquals(24, ((Number) data.get("recipients")).intValue(),
                "回给前端的条数必须是真查出来的，不是编的");
        assertEquals("课程1", data.get("courseName"));

        CourseRecommendation saved = captureInsert();
        assertEquals(ME, saved.getTeacherId());
        assertEquals(MY_COURSE, saved.getCourseId());
        assertEquals("这门课对你们的专业方向很有用", saved.getReason());
        assertEquals(1, saved.getStatus());
        assertNull(saved.getTargetClassId(), "没指定班级 = 发给本人名下全部班级");
    }

    @Test
    @DisplayName("★ 身份取头不取体：请求体里塞别人的 teacherId 也改不了落库的推荐人")
    void identityComesFromHeaderNotBody() {
        when(courseService.getById(MY_COURSE)).thenReturn(courseOf(MY_COURSE, ME));
        when(courseRecommendationMapper.countRecipients(ME, null)).thenReturn(1);

        // body 里声称自己是 18（刘教授），头里是 29
        controller.recommendCourse(
                body("courseId", MY_COURSE, "reason", "r", "teacherId", OTHER), as(ME));

        assertEquals(ME, captureInsert().getTeacherId(),
                "推荐人必须来自网关注入的 X-User-Id —— 否则可以冒名推荐");
    }

    @Test
    @DisplayName("推荐给**本人所带**的班 ⇒ 200")
    void ownTargetClassAllowed() {
        when(courseService.getById(MY_COURSE)).thenReturn(courseOf(MY_COURSE, ME));
        when(classInfoMapper.selectById(MY_CLASS)).thenReturn(classOf(MY_CLASS, ME));
        when(courseRecommendationMapper.countRecipients(ME, MY_CLASS)).thenReturn(24);

        ApiResponse<Object> res = controller.recommendCourse(
                body("courseId", MY_COURSE, "reason", "r", "targetClassId", MY_CLASS), as(ME));

        assertEquals(200, res.getCode());
        assertEquals(MY_CLASS, captureInsert().getTargetClassId());
    }

    // ============================================ 2. 归属

    @Test
    @DisplayName("★ 推荐**别人教的**课 ⇒ 403，一行都不写")
    void recommendForeignCourseRejected() {
        // 课程 1 是 29 的；调用方是 18
        when(courseService.getById(MY_COURSE)).thenReturn(courseOf(MY_COURSE, ME));

        ApiResponse<Object> res = controller.recommendCourse(
                body("courseId", MY_COURSE, "reason", "r"), as(OTHER));

        assertEquals(403, res.getCode());
        verify(courseRecommendationMapper, never()).insert(any());
    }

    @Test
    @DisplayName("★ 推荐给**别人带的**班 ⇒ 403，一行都不写")
    void recommendToForeignClassRejected() {
        when(courseService.getById(MY_COURSE)).thenReturn(courseOf(MY_COURSE, ME));
        // 班级 31 是 18 的
        when(classInfoMapper.selectById(OTHERS_CLASS)).thenReturn(classOf(OTHERS_CLASS, OTHER));

        ApiResponse<Object> res = controller.recommendCourse(
                body("courseId", MY_COURSE, "reason", "r", "targetClassId", OTHERS_CLASS), as(ME));

        assertEquals(403, res.getCode());
        verify(courseRecommendationMapper, never()).insert(any());
    }

    @Test
    @DisplayName("管理员可以代推荐（旁路本身也要是活的）")
    void adminBypassesOwnership() {
        when(courseService.getById(OTHERS_COURSE)).thenReturn(courseOf(OTHERS_COURSE, OTHER));
        when(courseRecommendationMapper.countRecipients(1L, null)).thenReturn(0);

        ApiResponse<Object> res = controller.recommendCourse(
                body("courseId", OTHERS_COURSE, "reason", "r"), asAdmin(1L));

        assertEquals(200, res.getCode());
        assertEquals(1L, captureInsert().getTeacherId(), "管理员发的也记管理员自己的 id");
    }

    // ============================================ 3. 三种"没做成"分得开，且都不落库

    @Test
    @DisplayName("★ 缺 courseId ⇒ 400（不是 500、不是 404），且不落库")
    void missingCourseIdIs400() {
        ApiResponse<Object> res = controller.recommendCourse(body("reason", "r"), as(ME));

        assertEquals(400, res.getCode());
        verify(courseRecommendationMapper, never()).insert(any());
    }

    @Test
    @DisplayName("★ 缺推荐理由 ⇒ 400，且不落库（理由为空的老行为是静默存空串）")
    void missingReasonIs400() {
        ApiResponse<Object> res = controller.recommendCourse(body("courseId", MY_COURSE), as(ME));

        assertEquals(400, res.getCode());
        verify(courseRecommendationMapper, never()).insert(any());
        // 连查课都不该发生 —— 参数没齐就不该往下走
        verify(courseService, never()).getById(anyLong());
    }

    @Test
    @DisplayName("★ 课程不存在 ⇒ 404（语义与「不是你的」403 分开），且不落库")
    void unknownCourseIs404() {
        when(courseService.getById(999L)).thenReturn(null);

        ApiResponse<Object> res = controller.recommendCourse(
                body("courseId", 999L, "reason", "r"), as(ME));

        assertEquals(404, res.getCode());
        verify(courseRecommendationMapper, never()).insert(any());
    }

    @Test
    @DisplayName("★ 没有登录身份（X-User-Id 缺失）⇒ 403，且不落库")
    void anonymousIsRejected() {
        ApiResponse<Object> res = controller.recommendCourse(
                body("courseId", MY_COURSE, "reason", "r"), anonymous());

        assertEquals(403, res.getCode());
        verify(courseRecommendationMapper, never()).insert(any());
    }

    @Test
    @DisplayName("连 body 都没有 ⇒ 400，不是 500（原来会 NPE）")
    void nullBodyIs400NotNpe() {
        ApiResponse<Object> res = controller.recommendCourse(null, as(ME));

        assertEquals(400, res.getCode());
        verify(courseRecommendationMapper, never()).insert(any());
    }

    // ============================================ 4. 边界

    @Test
    @DisplayName("理由超长 ⇒ 截到 500（列是 varchar(500)，严格模式会 DataTooLong）")
    void overlongReasonTruncated() {
        when(courseService.getById(MY_COURSE)).thenReturn(courseOf(MY_COURSE, ME));
        when(courseRecommendationMapper.countRecipients(ME, null)).thenReturn(0);
        String longReason = "推".repeat(600);

        assertEquals(200, controller.recommendCourse(
                body("courseId", MY_COURSE, "reason", longReason), as(ME)).getCode());

        assertEquals(500, captureInsert().getReason().length(),
                "截断比抛 DataTooLong（对用户表现为「推荐失败」）合理");
    }

    @Test
    @DisplayName("理由只有空白 ⇒ 400（空白不等于写了理由）")
    void blankReasonIs400() {
        assertEquals(400, controller.recommendCourse(
                body("courseId", MY_COURSE, "reason", "   "), as(ME)).getCode());
        verify(courseRecommendationMapper, never()).insert(any());
    }

    // ============================================ 5. 回看

    @Test
    @DisplayName("回看「我发过的推荐」 ⇒ 只查本人（id 取自头）")
    void listMineUsesCallerId() {
        when(courseRecommendationMapper.listByTeacher(ME)).thenReturn(List.of());

        assertEquals(200, controller.listMyRecommendations(as(ME)).getCode());
        verify(courseRecommendationMapper).listByTeacher(ME);
        verify(courseRecommendationMapper, never()).listByTeacher(OTHER);
    }

    @Test
    @DisplayName("回看没有身份 ⇒ 403")
    void listMineAnonymousIs403() {
        assertEquals(403, controller.listMyRecommendations(anonymous()).getCode());
        verify(courseRecommendationMapper, never()).listByTeacher(anyLong());
    }
}
