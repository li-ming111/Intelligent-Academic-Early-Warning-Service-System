package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.common.entity.TeacherProfile;
import com.academic.mapper.CollegeMapper;
import com.academic.service.ClassInfoService;
import com.academic.service.CourseService;
import com.academic.service.FeedbackService;
import com.academic.service.TeacherClassApplicationService;
import com.academic.service.TeacherService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * {@code GET /teachers/dashboard/{userId}} 带出「学院」（2026-10-03 W8.2）。
 *
 * <p>背景：教师端「个人设置 → 工作信息 → 学院」原先是一个 <b>disabled 的恒空框</b>，
 * 因为**没有任何接口返回学院** —— dashboard 只回
 * {@code {pendingFeedbacks,totalClasses,teacherName,pendingRequests,totalStudents,totalCourses}}。
 * 而 {@code teacher_profile.college_id} 是有值的（活库 6 行分别指向学院 1/2），
 * 所以按本仓口径"表里真有那一列就补字段"，由 dashboard 带出学院名。
 *
 * <p>两例分别覆盖"档案有学院"与"档案没填学院"：后者必须回**空串**而不是 null
 * —— 前端那格是 {@code <el-input disabled>}，绑 null 会显示成 "null" 字样。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TeacherController.getDashboard —— 带出教师所属学院（W8.2）")
class TeacherDashboardCollegeTest {

    @Mock private ClassInfoService classInfoService;
    @Mock private TeacherClassApplicationService applicationService;
    @Mock private CourseService courseService;
    @Mock private TeacherService teacherService;
    @Mock private FeedbackService feedbackService;
    @Mock private CollegeMapper collegeMapper;

    @InjectMocks private TeacherController controller;

    /** 把 dashboard 里那几处"降级成 0/空"的依赖都喂成空列表。 */
    private void stubEmptyDeps(Long uid) {
        when(classInfoService.getClassesByTeacherId(uid)).thenReturn(new ArrayList<>());
        when(applicationService.getApplicationsByTeacherId(uid)).thenReturn(new ArrayList<>());
        when(courseService.getCoursesByTeacherId(uid)).thenReturn(Collections.emptyList());
        when(feedbackService.getFeedbacks(uid, null)).thenReturn(new ArrayList<>());
    }

    private Map<?, ?> dataOf(ApiResponse<Object> resp) {
        assertTrue(resp.isSuccess(), "接口应成功，实际: " + resp.getMessage());
        return (Map<?, ?>) resp.getData();
    }

    @Test
    @DisplayName("档案填了学院 ⇒ college 是查出来的学院名（不是硬编码）")
    void dashboard_returnsCollegeName() {
        stubEmptyDeps(55L);
        TeacherProfile profile = new TeacherProfile();
        profile.setUserId(55L);
        profile.setName("陈志强");
        profile.setCollegeId(2L);
        when(teacherService.getByUserId(55L)).thenReturn(profile);
        when(collegeMapper.getNameById(2L)).thenReturn("电子工程学院");

        Map<?, ?> data = dataOf(controller.getDashboard("55"));

        assertEquals("电子工程学院", data.get("college"));
        // 顺带确认没把原来那几个字段弄丢
        assertEquals("陈志强", data.get("teacherName"));
    }

    @Test
    @DisplayName("档案没填学院 ⇒ college 是空串而不是 null（前端那格是 disabled input）")
    void dashboard_noCollege_returnsEmptyString() {
        stubEmptyDeps(19L);
        TeacherProfile profile = new TeacherProfile();
        profile.setUserId(19L);
        profile.setName("刘教授");
        profile.setCollegeId(null);          // 没填
        when(teacherService.getByUserId(19L)).thenReturn(profile);

        Map<?, ?> data = dataOf(controller.getDashboard("19"));

        assertEquals("", data.get("college"));
    }
}
