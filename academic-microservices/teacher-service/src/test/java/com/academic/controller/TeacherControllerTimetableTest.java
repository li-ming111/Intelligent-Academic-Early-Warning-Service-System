package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.entity.Feedback;
import com.academic.mapper.CourseTimetableMapper;
import com.academic.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 教师端「我的课表」（W48，2026-10-17）：{@code GET /teachers/courses/timetable}。
 *
 * <p>数据源 {@code course_plans}。这个面此前**无端点、无入口**：教师看不到自己的
 * 上课时间与教室（后端能力齐备）。
 *
 * <p>与 student-service 那条的关键差别 —— <b>身份只从 {@code X-User-Id} 取，
 * 不收任何请求参数</b>。所以"传别人的 id 看别人课表"这条路在设计上就不存在，
 * 本组用例把它钉成显式断言：喂一个别的 userId 到 header 里，查的必须是那个人**自己**的
 * （端点没有第二个 id 来源可以被顶替）。
 *
 * <p>⚠️ {@code course_plans.teacher_id} 是 {@code users.id}（**不是**
 * {@code teacher_profile.id}）。所以断言里期望的 id 是 18（teacher001 的 users.id），
 * 不是它的档案 id 8 —— 这条 id 空间本轮已第三次踩到。
 */
@ExtendWith(MockitoExtension.class)
class TeacherControllerTimetableTest {

    @Mock private ClassInfoService classInfoService;
    @Mock private TeacherClassApplicationService applicationService;
    @Mock private CourseService courseService;
    @Mock private TeacherService teacherService;
    @Mock private FeedbackService feedbackService;
    @Mock private ScoreRecordService scoreRecordService;
    @Mock private TeacherCourseService teacherCourseService;
    @Mock private com.academic.mapper.ClassInfoMapper classInfoMapper;
    @Mock private CourseTimetableMapper courseTimetableMapper;
    @InjectMocks private TeacherController controller;

    /** 教师 A：users.id=18（teacher001 刘教授）。它的 teacher_profile.id 是 8，别搞混。 */
    private static final long ME = 18L;
    /** 教师 B：users.id=29（10001 于凤海）。 */
    private static final long OTHER = 29L;

    /**
     * ⚠️ {@code courseTimetableMapper} 在控制器上是**字段级 `@Autowired`**，
     * `@InjectMocks` 注入不到它 ⇒ 不显式塞进去就是 null，端点走 catch 回 500。
     * 同 student-service 那条。
     */
    @BeforeEach
    void injectMapper() {
        ReflectionTestUtils.setField(controller, "courseTimetableMapper", courseTimetableMapper);
    }

    private static HttpServletRequest as(long userId) {
        HttpServletRequest r = mock(HttpServletRequest.class);
        lenient().when(r.getHeader("X-User-Id")).thenReturn(String.valueOf(userId));
        return r;
    }

    /** 无任何注入头（未登录 / 直连未过网关）。 */
    private static HttpServletRequest anonymous() {
        return mock(HttpServletRequest.class);
    }

    @Test
    @DisplayName("登录教师 ⇒ 200，且查的是 X-User-Id 本人（users.id，不是档案 id）")
    void ownTimetableAllowed() {
        when(courseTimetableMapper.listForTeacher(ME))
                .thenReturn(List.of(Map.of("planId", 1, "courseName", "数据结构")));

        ApiResponse<Object> res = controller.getMyTimetable(as(ME));

        assertEquals(200, res.getCode());
        // ★ 承重断言：18 = 18 号账号（teacher_profile.id 是 8）。用错 id 空间这里会看到 8。
        verify(courseTimetableMapper).listForTeacher(ME);
    }

    @Test
    @DisplayName("★ 换一个教师的身份 ⇒ 查的是**他自己**的，端点没有第二个 id 来源")
    void identityComesOnlyFromHeader() {
        when(courseTimetableMapper.listForTeacher(OTHER)).thenReturn(List.of());

        ApiResponse<Object> res = controller.getMyTimetable(as(OTHER));

        assertEquals(200, res.getCode());
        // 传 29 就查 29 —— 这不是"越权"，因为没有参数能表达"我要看 18 的课表"。
        verify(courseTimetableMapper).listForTeacher(OTHER);
        verify(courseTimetableMapper, never()).listForTeacher(ME);
    }

    @Test
    @DisplayName("无 X-User-Id 头 ⇒ 403，且 mapper 一次都没被调用")
    void anonymousRejected() {
        ApiResponse<Object> res = controller.getMyTimetable(anonymous());

        assertEquals(403, res.getCode());
        verify(courseTimetableMapper, never()).listForTeacher(anyLong());
    }

    @Test
    @DisplayName("匿名的几种变体都拒：空串 / 非数字 / 超范围，判不出来 ≠ 放行")
    void malformedIdentityRejected() {
        for (String raw : new String[]{"", "abc", "99999999999999999999"}) {
            HttpServletRequest r = mock(HttpServletRequest.class);
            lenient().when(r.getHeader("X-User-Id")).thenReturn(raw);
            ApiResponse<Object> res = controller.getMyTimetable(r);
            assertEquals(403, res.getCode(), "X-User-Id=" + raw + " 应被拒");
        }
        verify(courseTimetableMapper, never()).listForTeacher(anyLong());
    }

    @Test
    @DisplayName("没排课 ⇒ 200 + 空列表（别把空当异常）")
    void emptyTimetableIsSuccess() {
        when(courseTimetableMapper.listForTeacher(ME)).thenReturn(List.of());

        ApiResponse<Object> res = controller.getMyTimetable(as(ME));

        assertEquals(200, res.getCode());
        assertNotNull(res.getData());
    }
}
