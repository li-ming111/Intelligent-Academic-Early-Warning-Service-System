package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.service.CourseService;
import com.academic.service.TeacherCourseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 「0 行」的说法（W31，2026-10-08）。
 *
 * <p><b>改之前</b>：{@code DELETE /courses/delete/{不存在的id}} 回
 * <b>{@code code:500「删除课程失败」}</b> —— 而同一个控制器里 {@code PUT .../update/{同一个id}}
 * 回的是 <b>{@code code:404「课程不存在」}</b>（W8 当年只改了 update，漏了 delete）。
 * 两句话说的是同一件事（这个 id 没有对应行），但一句暗示"服务端出错了、可以重试"，
 * 一句说"它本来就不存在"。实测对照：
 * <pre>
 *   DELETE /courses/delete/99999         → {"code":500,"message":"删除课程失败"}
 *   PUT    /courses/update/99999         → {"code":404,"message":"课程不存在"}
 *   DELETE /teacher-courses/delete/99999 → {"code":500,"message":"删除教师课程关联失败"}
 * </pre>
 *
 * <p>本组用例把**两个方向都钉住**：0 行必须是 404「不存在」，1 行必须是 200「已删除」——
 * 只钉"0 行回 404"的话，一个恒回 404 的实现也能全绿。
 */
class CourseControllerNotFoundWordingTest {

    // ==================== CourseController ====================

    @Test
    @DisplayName("课程：删不存在的 id ⇒ 404「课程不存在」（不是 500「删除课程失败」）")
    void deleteMissingCourse_404() {
        CourseService courseService = mock(CourseService.class);
        when(courseService.removeById(99999L)).thenReturn(false);

        ApiResponse<String> resp = new CourseController(courseService).deleteCourse(99999L);

        assertEquals(404, resp.getCode());
        assertEquals("课程不存在", resp.getMessage());
        assertFalse(resp.isSuccess());
    }

    @Test
    @DisplayName("课程：删存在的 id ⇒ 200「课程已删除」（正向对照，必须真绿）")
    void deleteExistingCourse_200() {
        CourseService courseService = mock(CourseService.class);
        when(courseService.removeById(7L)).thenReturn(true);

        ApiResponse<String> resp = new CourseController(courseService).deleteCourse(7L);

        assertEquals(200, resp.getCode());
        // ⚠️ `ApiResponse.success(T data)` 是**单参**重载 ⇒ 那句话落在 `data` 里、
        //    `message` 恒为 "success"。断言写 message 会假红（我自己先踩了一次）。
        assertEquals("课程已删除", resp.getData());
        assertTrue(resp.isSuccess());
    }

    // ==================== TeacherCourseController ====================

    @Test
    @DisplayName("教师课程关联：删不存在的 id ⇒ 404「教师课程关联不存在」")
    void deleteMissingTeacherCourse_404() {
        TeacherCourseService svc = mock(TeacherCourseService.class);
        when(svc.removeById(99999L)).thenReturn(false);

        ApiResponse<String> resp = new TeacherCourseController(svc).deleteTeacherCourse(99999L);

        assertEquals(404, resp.getCode());
        assertEquals("教师课程关联不存在", resp.getMessage());
    }

    @Test
    @DisplayName("教师课程关联：删存在的 id ⇒ 200「教师课程关联已删除」（正向对照）")
    void deleteExistingTeacherCourse_200() {
        TeacherCourseService svc = mock(TeacherCourseService.class);
        when(svc.removeById(7L)).thenReturn(true);

        ApiResponse<String> resp = new TeacherCourseController(svc).deleteTeacherCourse(7L);

        assertEquals(200, resp.getCode());
        // 同上一处：`success(T data)` 单参重载，文本在 `data`。
        assertEquals("教师课程关联已删除", resp.getData());
    }
}
