package com.academic.service.impl;

import com.academic.common.entity.TeacherCourse;
import com.academic.mapper.TeacherCourseMapper;
import com.academic.service.TeacherCourseService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 教师课程关系服务实现
 */
@Service
public class TeacherCourseServiceImpl extends ServiceImpl<TeacherCourseMapper, TeacherCourse> implements TeacherCourseService {

    @Override
    public List<TeacherCourse> getCoursesByTeacherId(Long teacherId) {
        LambdaQueryWrapper<TeacherCourse> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TeacherCourse::getTeacherId, teacherId);
        return baseMapper.selectList(wrapper);
    }

    @Override
    public List<TeacherCourse> getTeachersByCourseId(Long courseId) {
        LambdaQueryWrapper<TeacherCourse> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TeacherCourse::getCourseId, courseId);
        return baseMapper.selectList(wrapper);
    }

    // assignCourse 已删（2026-10-14 W44）：唯一调用方 `POST /teachers/courses/assign` 已删
    // （那个端点无闸且零调用方，实测能以别人的 profile.id 建出任课关系行）。

    @Override
    public boolean updateTeacherCourse(TeacherCourse teacherCourse) {
        // 设置更新时间
        teacherCourse.setUpdatedAt(LocalDateTime.now());
        return updateById(teacherCourse);
    }

    @Override
    public boolean cancelCourseAssignment(Long id) {
        return removeById(id);
    }
}
