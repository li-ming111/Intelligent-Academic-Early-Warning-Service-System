package com.academic.service.impl;

import com.academic.common.entity.Course;
import com.academic.entity.CoursePlan;
import com.academic.mapper.CourseMapper;
import com.academic.mapper.CoursePlanMapper;
import com.academic.service.CoursePlanService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 课程计划服务实现类
 */
@Service
public class CoursePlanServiceImpl extends ServiceImpl<CoursePlanMapper, CoursePlan> implements CoursePlanService {

    private final CourseMapper courseMapper;

    public CoursePlanServiceImpl(CourseMapper courseMapper) {
        this.courseMapper = courseMapper;
    }

    @Override
    public List<CoursePlan> getPlansByCourseId(Long courseId) {
        LambdaQueryWrapper<CoursePlan> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CoursePlan::getCourseId, courseId);
        wrapper.eq(CoursePlan::getStatus, 1);
        return baseMapper.selectList(wrapper);
    }

    @Override
    public List<CoursePlan> getPlansBySemesterAndYear(String semester, Integer year) {
        LambdaQueryWrapper<CoursePlan> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CoursePlan::getSemester, semester);
        wrapper.eq(CoursePlan::getYear, year);
        wrapper.eq(CoursePlan::getStatus, 1);
        return baseMapper.selectList(wrapper);
    }

    @Override
    public boolean createCoursePlan(CoursePlan coursePlan) {
        applyCourseTeacher(coursePlan);
        // 设置创建时间和更新时间
        coursePlan.setCreatedAt(LocalDateTime.now());
        coursePlan.setUpdatedAt(LocalDateTime.now());
        coursePlan.setStatus(1);

        return save(coursePlan);
    }

    @Override
    public boolean updateCoursePlan(CoursePlan coursePlan) {
        CoursePlan existing = coursePlan.getId() == null ? null : getById(coursePlan.getId());
        if (existing == null) {
            return false;
        }
        if (coursePlan.getCourseId() == null) {
            coursePlan.setCourseId(existing.getCourseId());
        }
        applyCourseTeacher(coursePlan);
        // 设置更新时间
        coursePlan.setUpdatedAt(LocalDateTime.now());
        return updateById(coursePlan);
    }

    /** 任课教师只认课程的授课教师，避免排课与课程归属两处各写一份、彼此漂移。 */
    private void applyCourseTeacher(CoursePlan coursePlan) {
        if (coursePlan.getCourseId() == null) {
            throw new IllegalArgumentException("缺少课程");
        }
        Course course = courseMapper.selectById(coursePlan.getCourseId());
        if (course == null) {
            throw new IllegalArgumentException("课程不存在");
        }
        if (course.getTeacherId() == null) {
            throw new IllegalArgumentException("该课程尚未设置授课教师，请先在课程管理中补充");
        }
        coursePlan.setTeacherId(course.getTeacherId());
    }

    @Override
    public boolean deleteCoursePlan(Long id) {
        return removeById(id);
    }
}
