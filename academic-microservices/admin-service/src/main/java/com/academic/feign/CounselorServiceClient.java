package com.academic.feign;

import com.academic.common.dto.ApiResponse;
import com.academic.common.entity.CounselorProfile;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Counselor Service Feign Client
 * 调用 counselor-service 的接口
 */
@FeignClient(name = "counselor-service", fallback = CounselorServiceFallback.class)
public interface CounselorServiceClient {

    @GetMapping("/counselors")
    ApiResponse<List<CounselorProfile>> getCounselorsByCollege(@RequestParam(required = false) Long collegeId);

    @GetMapping("/counselors/{counselorId}")
    ApiResponse<CounselorProfile> getCounselorById(@PathVariable("counselorId") Long counselorId);

    // 2026-10-04（W15）删：`countByCollege` 零调用方（AdminController 统计学院人数走的是
    // 本库的 userService.count(...)，不走 Feign）。下游 `GET /counselors/count-by-college`
    // **保留**（仍可达、只是当前无调用方；它的 catch-吞异常本轮已改如实回错误）。
}
