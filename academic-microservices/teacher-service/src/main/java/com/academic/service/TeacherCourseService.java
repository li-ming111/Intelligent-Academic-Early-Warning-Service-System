package com.academic.service;

import com.academic.common.entity.TeacherCourse;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 教师课程关系服务接口
 */
public interface TeacherCourseService extends IService<TeacherCourse> {
    /**
     * 根据教师ID获取课程列表
     * @param teacherId 教师ID
     * @return 课程列表
     */
    List<TeacherCourse> getCoursesByTeacherId(Long teacherId);

    /**
     * 根据课程ID获取教师列表
     * @param courseId 课程ID
     * @return 教师列表
     */
    List<TeacherCourse> getTeachersByCourseId(Long courseId);

    // assignCourse 已删（2026-10-14 W44）：唯一调用方 `POST /teachers/courses/assign` 已删。

    /**
     * 更新教师课程关系
     * @param teacherCourse 教师课程关系
     * @return 是否更新成功
     */
    boolean updateTeacherCourse(TeacherCourse teacherCourse);

    /**
     * 取消教师课程分配
     * @param id 教师课程关系ID
     * @return 是否取消成功
     */
    boolean cancelCourseAssignment(Long id);
}
