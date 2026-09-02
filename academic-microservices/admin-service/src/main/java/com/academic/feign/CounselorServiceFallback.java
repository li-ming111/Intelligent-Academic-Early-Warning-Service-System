package com.academic.feign;

import com.academic.common.dto.ApiResponse;
import com.academic.common.entity.CounselorProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Counselor Service Fallback
 * 当 counselor-service 不可用时的降级处理
 */
@Slf4j
@Component
public class CounselorServiceFallback implements CounselorServiceClient {

    @Override
    public ApiResponse<List<CounselorProfile>> getCounselorsByCollege(Long collegeId) {
        log.warn("counselor-service is unavailable, returning fallback empty list for collegeId: {}", collegeId);
        return ApiResponse.success(Collections.emptyList());
    }

    @Override
    public ApiResponse<CounselorProfile> getCounselorById(Long counselorId) {
        log.warn("counselor-service is unavailable, returning fallback for counselorId: {}", counselorId);
        return ApiResponse.error("辅导员服务暂时不可用");
    }

    @Override
    public ApiResponse<Long> countByCollege(Long collegeId) {
        log.warn("counselor-service is unavailable, returning fallback count 0 for collegeId: {}", collegeId);
        return ApiResponse.success(0L);
    }
}
