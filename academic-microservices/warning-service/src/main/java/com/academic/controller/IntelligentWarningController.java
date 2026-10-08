package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.exception.WarningNotFoundException;
import com.academic.service.IntelligentWarningService;
import com.academic.task.WarningScheduledTask;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
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
     * 也方便演示。返回本次的产出。
     *
     * <p><b>权限（W26，2026-10-05）</b>：这是一次**会写库 + 群发通知**的**全库**操作 ——
     * 写 {@code academic_warning} / {@code warning_release_records} / {@code notifications}，
     * 并给每名新预警的学生与其辅导员各发一条通知。所以只放行
     * <b>辅导员(3) / 管理员(4) / 学院管理员(5)</b>。
     *
     * <p>此前这里**没有任何角色判定**。而网关对 {@code /api/warnings/**} 不做前缀把关：内置角色 1/2/3
     * 一路落到 {@code AuthenticationFilter} 末尾的 {@code return true}（网关自己的单测
     * {@code AuthenticationFilterTest} 就写着"学生也能打这条路径"），下游 {@code SecurityConfig}
     * 又只要求 {@code authenticated()} —— 于是**任何已登录用户都能触发一次全校重扫并给全校发通知**。
     * 安全边界落在本方法里，不靠前端藏按钮。
     *
     * @return {@code {generatedCount, releasedCount, skipped[, message]}}；
     *         拒绝时是本仓惯用的 <b>HTTP 200 + body code 403</b>（前端拦截器按 code 判）
     */
    @PostMapping("/scan")
    public ApiResponse<Object> triggerScan(HttpServletRequest request) {
        String role = request == null ? null : request.getHeader("X-Role");
        if (!isScanAllowed(role)) {
            log.warn("拒绝一次预警扫描触发：X-Role={}", role);
            return ApiResponse.error(403, "只有辅导员、管理员或学院管理员可以触发预警扫描");
        }
        try {
            WarningScheduledTask.ScanResult result = warningScheduledTask.triggerNowDetailed();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("generatedCount", result.generated());
            body.put("releasedCount", result.released());
            body.put("skipped", result.skipped());
            if (result.skipped()) {
                body.put("message", "已有一轮预警扫描正在进行中，本次未重复执行");
            }
            return ApiResponse.success(body);
        } catch (Exception e) {
            log.error("Manual warning scan failed: {}", e.getMessage(), e);
            return ApiResponse.error(500, e.getMessage());
        }
    }

    /**
     * 允许触发全校重扫的角色：3=辅导员、4=管理员、5=学院管理员。
     * 其余（含 1=学生、2=教师）一律拒绝 —— 与 teacher/counselor 既有归属闸同一套 {@code X-Role} 口径。
     */
    private static boolean isScanAllowed(String role) {
        return "3".equals(role) || "counselor".equals(role)
                || "4".equals(role) || "admin".equals(role)
                || "5".equals(role) || "college_admin".equals(role);
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
