package com.academic.controller;

import com.academic.entity.CoursePlan;
import com.academic.service.CoursePlanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 课程计划控制器
 */
@RestController
@RequestMapping("/course-plans")
public class CoursePlanController {

    @Autowired
    private CoursePlanService coursePlanService;

    /**
     * 获取所有课程计划
     * @return 课程计划列表
     */
    @GetMapping("/all")
    public ResponseEntity<List<CoursePlan>> getAllCoursePlans() {
        List<CoursePlan> coursePlans = coursePlanService.list();
        return new ResponseEntity<>(coursePlans, HttpStatus.OK);
    }

    /**
     * 根据ID获取课程计划
     * @param id 课程计划ID
     * @return 课程计划信息
     */
    @GetMapping("/{id}")
    public ResponseEntity<CoursePlan> getCoursePlanById(@PathVariable Long id) {
        CoursePlan coursePlan = coursePlanService.getById(id);
        if (coursePlan != null) {
            return new ResponseEntity<>(coursePlan, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    /**
     * 根据课程ID获取课程计划列表
     * @param courseId 课程ID
     * @return 课程计划列表
     */
    @GetMapping("/course/{courseId}")
    public ResponseEntity<List<CoursePlan>> getCoursePlansByCourseId(@PathVariable Long courseId) {
        List<CoursePlan> coursePlans = coursePlanService.getPlansByCourseId(courseId);
        return new ResponseEntity<>(coursePlans, HttpStatus.OK);
    }

    /**
     * 根据学期和年份获取课程计划列表
     * @param semester 学期
     * @param year 年份
     * @return 课程计划列表
     */
    @GetMapping("/semester")
    public ResponseEntity<List<CoursePlan>> getCoursePlansBySemesterAndYear(
            @RequestParam String semester, @RequestParam Integer year) {
        List<CoursePlan> coursePlans = coursePlanService.getPlansBySemesterAndYear(semester, year);
        return new ResponseEntity<>(coursePlans, HttpStatus.OK);
    }

    /**
     * 创建课程计划
     * @param coursePlan 课程计划信息
     * @return 创建结果
     */
    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createCoursePlan(@RequestBody CoursePlan coursePlan) {
        boolean success;
        try {
            success = coursePlanService.createCoursePlan(coursePlan);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(Map.of("success", false, "message", e.getMessage()), HttpStatus.BAD_REQUEST);
        }
        if (success) {
            return new ResponseEntity<>(Map.of("success", true, "message", "课程计划创建成功"), HttpStatus.CREATED);
        } else {
            return new ResponseEntity<>(Map.of("success", false, "message", "课程计划创建失败"), HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 更新课程计划
     * @param id 课程计划ID
     * @param coursePlan 课程计划信息
     * @return 更新结果
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateCoursePlan(@PathVariable Long id, @RequestBody CoursePlan coursePlan) {
        coursePlan.setId(id);
        boolean success;
        try {
            success = coursePlanService.updateCoursePlan(coursePlan);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(Map.of("success", false, "message", e.getMessage()), HttpStatus.BAD_REQUEST);
        }
        if (success) {
            return new ResponseEntity<>(Map.of("success", true, "message", "课程计划更新成功"), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(Map.of("success", false, "message", "课程计划更新失败"), HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 删除课程计划
     * @param id 课程计划ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteCoursePlan(@PathVariable Long id) {
        boolean success = coursePlanService.deleteCoursePlan(id);
        if (success) {
            return new ResponseEntity<>(Map.of("success", true, "message", "课程计划删除成功"), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(Map.of("success", false, "message", "课程计划删除失败"), HttpStatus.BAD_REQUEST);
        }
    }
}
