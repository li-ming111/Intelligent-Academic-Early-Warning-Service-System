package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.common.entity.TeacherCourse;
import com.academic.common.util.UpdateBodyGuard;
import com.academic.service.TeacherCourseService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 教师课程关联控制器
 */
@Slf4j
@RestController
@RequestMapping("/teacher-courses")
public class TeacherCourseController {

    private final TeacherCourseService teacherCourseService;

    public TeacherCourseController(TeacherCourseService teacherCourseService) {
        this.teacherCourseService = teacherCourseService;
    }

    /**
     * 获取教师课程关联列表
     */
    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> getTeacherCourses(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) Long courseId) {
        try {
            log.info("Get teacher courses request: page={}, size={}, teacherId={}, courseId={}", page, size, teacherId, courseId);
            QueryWrapper<TeacherCourse> queryWrapper = new QueryWrapper<>();
            if (teacherId != null) {
                queryWrapper.eq("teacher_id", teacherId);
            }
            if (courseId != null) {
                queryWrapper.eq("course_id", courseId);
            }
            queryWrapper.orderByDesc("created_at");
            
            Page<TeacherCourse> teacherCoursePage = new Page<>(page, size);
            teacherCoursePage = teacherCourseService.page(teacherCoursePage, queryWrapper);
            
            Map<String, Object> responseData = Map.of(
                    "data", teacherCoursePage.getRecords(),
                    "total", teacherCoursePage.getTotal(),
                    "page", page,
                    "size", size,
                    "pages", teacherCoursePage.getPages()
            );
            return ApiResponse.success(responseData);
        } catch (Exception e) {
            log.error("Get teacher courses failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取教师课程关联详情
     */
    @GetMapping("/info/{id}")
    public ApiResponse<TeacherCourse> getTeacherCourseInfo(@PathVariable Long id) {
        try {
            log.info("Get teacher course info request: {}", id);
            TeacherCourse teacherCourse = teacherCourseService.getById(id);
            if (teacherCourse == null) {
                return ApiResponse.error(404, "教师课程关联不存在");
            }
            return ApiResponse.success(teacherCourse);
        } catch (Exception e) {
            log.error("Get teacher course info failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 创建教师课程关联
     */
    @PostMapping("/add")
    public ApiResponse<Long> addTeacherCourse(@RequestBody TeacherCourse teacherCourse) {
        try {
            log.info("Add teacher course request: teacherId={}, courseId={}", teacherCourse.getTeacherId(), teacherCourse.getCourseId());
            // 检查是否已存在关联
            TeacherCourse existing = teacherCourseService.getByTeacherIdAndCourseId(teacherCourse.getTeacherId(), teacherCourse.getCourseId());
            if (existing != null) {
                return ApiResponse.error("教师课程关联已存在");
            }
            boolean saved = teacherCourseService.save(teacherCourse);
            if (saved) {
                return ApiResponse.success(teacherCourse.getId());
            } else {
                return ApiResponse.error("创建教师课程关联失败");
            }
        } catch (Exception e) {
            log.error("Add teacher course failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 更新教师课程关联
     */
    @PutMapping("/update/{id}")
    public ApiResponse<String> updateTeacherCourse(@PathVariable Long id, @RequestBody TeacherCourse teacherCourse) {
        try {
            log.info("Update teacher course request: {}", id);
            teacherCourse.setId(id);
            // W8：空请求体 ⇒ 无 SET 子句 ⇒ SQL 语法错；这是客户端的错，回 400（原来是 200+code500）。
            if (!UpdateBodyGuard.hasUpdatableField(teacherCourse)) {
                return ApiResponse.badRequest("请求体里没有任何可更新的字段");
            }
            boolean updated = teacherCourseService.updateById(teacherCourse);
            if (updated) {
                return ApiResponse.success("教师课程关联已更新");
            } else {
                // W8：0 行 = 该关联不存在（原来回 code 500）。
                return ApiResponse.notFound("教师课程关联不存在");
            }
        } catch (Exception e) {
            log.error("Update teacher course failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 删除教师课程关联
     */
    @DeleteMapping("/delete/{id}")
    public ApiResponse<String> deleteTeacherCourse(@PathVariable Long id) {
        try {
            log.info("Delete teacher course request: {}", id);
            boolean deleted = teacherCourseService.removeById(id);
            if (deleted) {
                return ApiResponse.success("教师课程关联已删除");
            } else {
                return ApiResponse.error("删除教师课程关联失败");
            }
        } catch (Exception e) {
            log.error("Delete teacher course failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 根据教师ID获取课程列表
     */
    @GetMapping("/by-teacher/{teacherId}")
    public ApiResponse<java.util.List<TeacherCourse>> getTeacherCoursesByTeacherId(@PathVariable Long teacherId) {
        try {
            log.info("Get teacher courses by teacher id request: {}", teacherId);
            java.util.List<TeacherCourse> teacherCourses = teacherCourseService.getByTeacherId(teacherId);
            return ApiResponse.success(teacherCourses);
        } catch (Exception e) {
            log.error("Get teacher courses by teacher id failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 根据课程ID获取教师列表
     */
    @GetMapping("/by-course/{courseId}")
    public ApiResponse<java.util.List<TeacherCourse>> getTeacherCoursesByCourseId(@PathVariable Long courseId) {
        try {
            log.info("Get teacher courses by course id request: {}", courseId);
            java.util.List<TeacherCourse> teacherCourses = teacherCourseService.getByCourseId(courseId);
            return ApiResponse.success(teacherCourses);
        } catch (Exception e) {
            log.error("Get teacher courses by course id failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 根据教师ID和课程ID获取关联
     */
    @GetMapping("/by-teacher-course/{teacherId}/{courseId}")
    public ApiResponse<TeacherCourse> getTeacherCourseByTeacherIdAndCourseId(@PathVariable Long teacherId, @PathVariable Long courseId) {
        try {
            log.info("Get teacher course by teacher id and course id request: teacherId={}, courseId={}", teacherId, courseId);
            TeacherCourse teacherCourse = teacherCourseService.getByTeacherIdAndCourseId(teacherId, courseId);
            if (teacherCourse == null) {
                return ApiResponse.error(404, "教师课程关联不存在");
            }
            return ApiResponse.success(teacherCourse);
        } catch (Exception e) {
            log.error("Get teacher course by teacher id and course id failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取所有教师课程关联列表（不分页）
     */
    @GetMapping("/all")
    public ApiResponse<java.util.List<TeacherCourse>> getAllTeacherCourses() {
        try {
            log.info("Get all teacher courses request");
            java.util.List<TeacherCourse> teacherCourses = teacherCourseService.list(new QueryWrapper<>());
            return ApiResponse.success(teacherCourses);
        } catch (Exception e) {
            log.error("Get all teacher courses failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取教师课程关联总数
     */
    @GetMapping("/count")
    public ApiResponse<Long> getTeacherCourseCount(@RequestParam(required = false) Long teacherId, @RequestParam(required = false) Long courseId) {
        try {
            log.info("Get teacher course count request: teacherId={}, courseId={}", teacherId, courseId);
            QueryWrapper<TeacherCourse> queryWrapper = new QueryWrapper<>();
            if (teacherId != null) {
                queryWrapper.eq("teacher_id", teacherId);
            }
            if (courseId != null) {
                queryWrapper.eq("course_id", courseId);
            }
            long count = teacherCourseService.count(queryWrapper);
            return ApiResponse.success(count);
        } catch (Exception e) {
            log.error("Get teacher course count failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }
}
