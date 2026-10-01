package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.exception.WarningNotFoundException;
import com.academic.service.IntelligentWarningService;
import com.academic.task.WarningScheduledTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Intelligent Warning Controller
 */
@Slf4j
@RestController
@RequestMapping("/warnings")
public class IntelligentWarningController {

    private final IntelligentWarningService intelligentWarningService;
    private final WarningScheduledTask warningScheduledTask;

    public IntelligentWarningController(IntelligentWarningService intelligentWarningService,
                                        WarningScheduledTask warningScheduledTask) {
        this.intelligentWarningService = intelligentWarningService;
        this.warningScheduledTask = warningScheduledTask;
    }

    /**
     * 手动触发一次全量预警扫描。
     *
     * <p>定时任务在凌晨 2:00 才跑，这个接口让录入成绩后可以立刻重算，
     * 也方便演示。返回本次新生成的预警条数。
     */
    @PostMapping("/scan")
    public ApiResponse<Object> triggerScan() {
        try {
            int generated = warningScheduledTask.triggerNow();
            return ApiResponse.success(Map.of("generatedCount", generated));
        } catch (Exception e) {
            log.error("Manual warning scan failed: {}", e.getMessage(), e);
            return ApiResponse.error(500, e.getMessage());
        }
    }

    /**
     * 生成智能预警
     */
    @PostMapping("/generate")
    public ApiResponse<Object> generateWarning(@RequestBody Map<String, Object> request) {
        try {
            log.info("Generate warning request: {}", request);
            Object result = intelligentWarningService.generateWarning(request);
            return ApiResponse.success(result);
        } catch (IllegalArgumentException e) {
            log.warn("Generate warning 参数错误: {}", e.getMessage());
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("Generate warning failed: {}", e.getMessage(), e);
            return ApiResponse.error(500, e.getMessage());
        }
    }

    /**
     * 处理预警
     */
    @PostMapping("/handle")
    public ApiResponse<Object> handleWarning(@RequestBody Map<String, Object> request) {
        try {
            log.info("Handle warning request: {}", request);
            Object result = intelligentWarningService.handleWarning(request);
            return ApiResponse.success(result);
        } catch (WarningNotFoundException e) {
            log.warn("Handle warning 目标不存在: {}", e.getMessage());
            return ApiResponse.notFound(e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("Handle warning 参数错误: {}", e.getMessage());
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("Handle warning failed: {}", e.getMessage(), e);
            return ApiResponse.error(500, e.getMessage());
        }
    }

    /**
     * 获取预警列表
     */
    @GetMapping("/list")
    public ApiResponse<Object> getWarningList(@RequestParam(required = false) Map<String, Object> params) {
        try {
            log.info("Get warning list request: {}", params);
            Object result = intelligentWarningService.getWarningList(params);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Get warning list failed: {}", e.getMessage(), e);
            return ApiResponse.error(500, e.getMessage());
        }
    }

    /**
     * 根据学生ID获取预警列表
     */
    @GetMapping("/student")
    public ApiResponse<Object> getWarningsByStudentId(@RequestParam("studentId") Long studentId) {
        try {
            log.info("Get warnings by studentId: {}", studentId);
            Object result = intelligentWarningService.getWarningsByStudentId(studentId);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Get warnings by studentId failed: {}", e.getMessage(), e);
            return ApiResponse.error(500, e.getMessage());
        }
    }

    /**
     * 根据预警等级获取预警列表
     */
    @GetMapping("/level")
    public ApiResponse<Object> getWarningsByLevel(@RequestParam("level") Integer level) {
        try {
            log.info("Get warnings by level: {}", level);
            Object result = intelligentWarningService.getWarningsByLevel(level);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Get warnings by level failed: {}", e.getMessage(), e);
            return ApiResponse.error(500, e.getMessage());
        }
    }

    /**
     * 获取预警详情
     */
    @GetMapping("/detail/{warningId}")
    public ApiResponse<Object> getWarningDetail(@PathVariable String warningId) {
        try {
            log.info("Get warning detail request: {}", warningId);
            Object result = intelligentWarningService.getWarningDetail(warningId);
            return ApiResponse.success(result);
        } catch (WarningNotFoundException e) {
            log.warn("Get warning detail 目标不存在: {}", e.getMessage());
            return ApiResponse.notFound(e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("Get warning detail 参数错误: {}", e.getMessage());
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("Get warning detail failed: {}", e.getMessage(), e);
            return ApiResponse.error(500, e.getMessage());
        }
    }

    /**
     * 更新预警状态
     */
    @PutMapping("/status/{warningId}")
    public ApiResponse<Object> updateWarningStatus(@PathVariable String warningId, @RequestBody Map<String, Object> request) {
        try {
            log.info("Update warning status request: {} {}", warningId, request);
            Object result = intelligentWarningService.updateWarningStatus(warningId, request);
            return ApiResponse.success(result);
        } catch (WarningNotFoundException e) {
            log.warn("Update warning status 目标不存在: {}", e.getMessage());
            return ApiResponse.notFound(e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("Update warning status 参数错误: {}", e.getMessage());
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("Update warning status failed: {}", e.getMessage(), e);
            return ApiResponse.error(500, e.getMessage());
        }
    }
}
