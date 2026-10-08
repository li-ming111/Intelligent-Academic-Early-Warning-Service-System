package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.common.entity.Course;
import com.academic.common.util.UpdateBodyGuard;
import com.academic.dto.CourseDTO;
import com.academic.service.CourseService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 课程控制器
 */
@Slf4j
@RestController
@RequestMapping("/courses")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    /**
     * 获取课程列表
     */
    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> getCourses(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String type) {
        try {
            log.info("Get courses request: page={}, size={}, type={}", page, size, type);
            QueryWrapper<Course> queryWrapper = new QueryWrapper<>();
            if (type != null) {
                queryWrapper.eq("type", type);
            }
            queryWrapper.orderByDesc("created_at");
            
            Page<Course> coursePage = new Page<>(page, size);
            coursePage = courseService.page(coursePage, queryWrapper);
            
            List<CourseDTO> courseDTOs = coursePage.getRecords().stream()
                    .map(course -> {
                        CourseDTO dto = new CourseDTO();
                        dto.setId(course.getId());
                        dto.setCode(course.getCode());
                        dto.setName(course.getName());
                        dto.setCredits(course.getCredits());
                        dto.setType(course.getType());
                        dto.setModuleId(course.getModuleId());
                        dto.setScoreTemplate(course.getScoreTemplate());
                        dto.setTeacherId(course.getTeacherId());
                        dto.setCreatedAt(course.getCreatedAt());
                        dto.setUpdatedAt(course.getUpdatedAt());
                        return dto;
                    })
                    .collect(Collectors.toList());
            
            Map<String, Object> responseData = Map.of(
                    "data", courseDTOs,
                    "total", coursePage.getTotal(),
                    "page", page,
                    "size", size,
                    "pages", coursePage.getPages()
            );
            return ApiResponse.success(responseData);
        } catch (Exception e) {
            log.error("Get courses failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取课程详情
     */
    @GetMapping("/info/{courseId}")
    public ApiResponse<CourseDTO> getCourseInfo(@PathVariable Long courseId) {
        try {
            log.info("Get course info request: {}", courseId);
            Course course = courseService.getById(courseId);
            if (course == null) {
                return ApiResponse.error(404, "课程不存在");
            }
            
            CourseDTO dto = new CourseDTO();
            dto.setId(course.getId());
            dto.setCode(course.getCode());
            dto.setName(course.getName());
            dto.setCredits(course.getCredits());
            dto.setType(course.getType());
            dto.setModuleId(course.getModuleId());
            dto.setScoreTemplate(course.getScoreTemplate());
            dto.setTeacherId(course.getTeacherId());
            dto.setCreatedAt(course.getCreatedAt());
            dto.setUpdatedAt(course.getUpdatedAt());
            
            return ApiResponse.success(dto);
        } catch (Exception e) {
            log.error("Get course info failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 创建课程
     */
    @PostMapping("/add")
    public ApiResponse<Long> addCourse(@RequestBody Course course) {
        try {
            log.info("Add course request: {}", course.getName());
            boolean saved = courseService.save(course);
            if (saved) {
                return ApiResponse.success(course.getId());
            } else {
                return ApiResponse.error("创建课程失败");
            }
        } catch (Exception e) {
            log.error("Add course failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 更新课程
     */
    @PutMapping("/update/{courseId}")
    public ApiResponse<String> updateCourse(@PathVariable Long courseId, @RequestBody Course course) {
        try {
            log.info("Update course request: {}", courseId);
            course.setId(courseId);
            // W8：空请求体 ⇒ 无 SET 子句 ⇒ SQL 语法错；这是客户端的错，回 400（原来是 200+code500）。
            if (!UpdateBodyGuard.hasUpdatableField(course)) {
                return ApiResponse.badRequest("请求体里没有任何可更新的字段");
            }
            boolean updated = courseService.updateById(course);
            if (updated) {
                return ApiResponse.success("课程已更新");
            } else {
                // W8：0 行 = 该课程不存在（原来回 code 500）。
                return ApiResponse.notFound("课程不存在");
            }
        } catch (Exception e) {
            log.error("Update course failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 删除课程
     *
     * <p>2026-10-08（W31）：0 行（该课程不存在）**原来回 {@code code:500「删除课程失败」}**
     * —— 与同文件 {@code updateCourse} 的 {@code notFound} 说法不一致（W8 只改了 update）。
     * 事实是"这门课不存在"，不是"删除操作失败"：前者修不了，后者值得重试。
     * 实测对照：{@code DELETE /courses/delete/99999} → 500"删除课程失败"，
     * 而 {@code PUT /courses/update/99999} → 404"课程不存在"。
     */
    @DeleteMapping("/delete/{courseId}")
    public ApiResponse<String> deleteCourse(@PathVariable Long courseId) {
        try {
            log.info("Delete course request: {}", courseId);
            boolean deleted = courseService.removeById(courseId);
            if (deleted) {
                return ApiResponse.success("课程已删除");
            } else {
                // 0 行 = 该课程不存在（与 updateCourse 同一口径）。
                return ApiResponse.notFound("课程不存在");
            }
        } catch (Exception e) {
            log.error("Delete course failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 根据代码获取课程信息
     */
    @GetMapping("/by-code/{code}")
    public ApiResponse<Course> getCourseByCode(@PathVariable String code) {
        try {
            log.info("Get course by code request: {}", code);
            Course course = courseService.getByCode(code);
            if (course == null) {
                return ApiResponse.error(404, "课程不存在");
            }
            return ApiResponse.success(course);
        } catch (Exception e) {
            log.error("Get course by code failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 根据课程类型获取课程列表
     */
    @GetMapping("/by-type/{type}")
    public ApiResponse<java.util.List<Course>> getCoursesByType(@PathVariable String type) {
        try {
            log.info("Get courses by type request: {}", type);
            QueryWrapper<Course> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("type", type);
            java.util.List<Course> courses = courseService.list(queryWrapper);
            return ApiResponse.success(courses);
        } catch (Exception e) {
            log.error("Get courses by type failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取所有课程列表（不分页）
     */
    @GetMapping("/all")
    public ApiResponse<java.util.List<Course>> getAllCourses() {
        try {
            log.info("Get all courses request");
            java.util.List<Course> courses = courseService.list(new QueryWrapper<>());
            return ApiResponse.success(courses);
        } catch (Exception e) {
            log.error("Get all courses failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取课程总数
     */
    @GetMapping("/count")
    public ApiResponse<Long> getCourseCount(@RequestParam(required = false) String type) {
        try {
            log.info("Get course count request: type={}", type);
            QueryWrapper<Course> queryWrapper = new QueryWrapper<>();
            if (type != null) {
                queryWrapper.eq("type", type);
            }
            long count = courseService.count(queryWrapper);
            return ApiResponse.success(count);
        } catch (Exception e) {
            log.error("Get course count failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }
}
