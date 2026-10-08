package com.academic.feign;

import com.academic.common.dto.ApiResponse;
import com.academic.common.entity.Student;
import com.academic.entity.StudentProfile;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Student Service Feign Client
 * 调用 student-service 的接口
 */
@FeignClient(name = "student-service", fallback = StudentServiceFallback.class)
public interface StudentServiceClient {

    @GetMapping("/students/list")
    ApiResponse<List<Student>> getStudentsByCollege(@RequestParam(required = false) Long collegeId);

    @GetMapping("/students/{studentId}")
    ApiResponse<Student> getStudentById(@PathVariable("studentId") Long studentId);

    @GetMapping("/students/{studentId}/gpa")
    ApiResponse<StudentProfile> getStudentProfileById(@PathVariable("studentId") Long studentId);

    // 2026-10-04（W15）删：`countByCollege` 零调用方（AdminController 的学生数走本库
    // studentProfileService.count()）。下游 `GET /students/count-by-college` 保留。
}
