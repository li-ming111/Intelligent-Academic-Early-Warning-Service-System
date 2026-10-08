package com.academic.feign;

import com.academic.common.dto.ApiResponse;
import com.academic.common.entity.Student;
import com.academic.entity.StudentProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Student Service Fallback
 * 当 student-service 不可用时的降级处理
 */
@Slf4j
@Component
public class StudentServiceFallback implements StudentServiceClient {

    @Override
    public ApiResponse<List<Student>> getStudentsByCollege(Long collegeId) {
        log.warn("student-service is unavailable, returning fallback empty list for collegeId: {}", collegeId);
        return ApiResponse.success(Collections.emptyList());
    }

    @Override
    public ApiResponse<Student> getStudentById(Long studentId) {
        log.warn("student-service is unavailable, returning fallback for studentId: {}", studentId);
        return ApiResponse.error("学生服务暂时不可用");
    }

    @Override
    public ApiResponse<StudentProfile> getStudentProfileById(Long studentId) {
        log.warn("student-service is unavailable, returning fallback for studentProfileId: {}", studentId);
        return ApiResponse.error("学生服务暂时不可用");
    }
}
