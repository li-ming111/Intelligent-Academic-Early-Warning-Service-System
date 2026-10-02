package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.dto.StudentDashboardResponse;
import com.academic.service.StudentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Student Controller - 学生角色全部功能端点
 */
@Slf4j
@RestController
@RequestMapping("/students")
public class StudentController {

    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    // ==================== Dashboard ====================
    @GetMapping("/dashboard/{studentId}")
    public ApiResponse<StudentDashboardResponse> getStudentDashboard(@PathVariable String studentId) {
        try {
            log.info("Get student dashboard: {}", studentId);
            return ApiResponse.success(studentService.getStudentDashboard(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get student dashboard failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    // ==================== 成绩 ====================
    @GetMapping("/scores/{studentId}")
    public ApiResponse<Object> getStudentScores(@PathVariable String studentId, @RequestParam(required = false) String semester) {
        try {
            log.info("Get student scores: {}, semester: {}", studentId, semester);
            return ApiResponse.success(studentService.getStudentScores(Long.parseLong(studentId), semester));
        } catch (Exception e) {
            log.error("Get student scores failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/export/scores/{studentId}/excel")
    public ApiResponse<Object> exportScoresToExcel(@PathVariable String studentId) {
        try {
            log.info("Export scores to Excel: {}", studentId);
            return ApiResponse.success(studentService.exportScoresToExcel(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Export scores to Excel failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/download/scores/{studentId}")
    public ApiResponse<Object> downloadScores(@PathVariable String studentId) {
        try {
            log.info("Download scores: {}", studentId);
            return ApiResponse.success(studentService.downloadScores(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Download scores failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    // ==================== 预警 ====================
    @GetMapping("/warnings/{studentId}")
    public ApiResponse<Object> getStudentWarnings(@PathVariable String studentId) {
        try {
            log.info("Get student warnings: {}", studentId);
            return ApiResponse.success(studentService.getStudentWarnings(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get student warnings failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    // ==================== 学生信息 ====================
    @GetMapping("/info/{studentId}")
    public ApiResponse<Object> getStudentInfo(@PathVariable String studentId) {
        try {
            log.info("Get student info: {}", studentId);
            return ApiResponse.success(studentService.getStudentInfo(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get student info failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/{studentId}")
    public ApiResponse<Object> getStudentInfoByPath(@PathVariable String studentId) {
        try {
            log.info("Get student info by path: {}", studentId);
            return ApiResponse.success(studentService.getStudentInfo(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get student info by path failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/student-by-user/{userId}")
    public ApiResponse<Object> getStudentByUserId(@PathVariable String userId) {
        try {
            log.info("Get student by user ID: {}", userId);
            return ApiResponse.success(studentService.getStudentInfo(Long.parseLong(userId)));
        } catch (Exception e) {
            log.error("Get student by user ID failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/{studentId}/gpa")
    public ApiResponse<Object> getStudentGpa(@PathVariable String studentId) {
        try {
            log.info("Get student GPA: {}", studentId);
            return ApiResponse.success(studentService.getHistoryBenchmark(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get student GPA failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/{studentId}/class-info")
    public ApiResponse<Object> getClassInfo(@PathVariable String studentId) {
        try {
            log.info("Get class info: {}", studentId);
            return ApiResponse.success(studentService.getClassInfo(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get class info failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    // ==================== 帮扶 ====================
    @GetMapping("/assistance/{studentId}")
    public ApiResponse<Object> getAssistance(@PathVariable String studentId) {
        try {
            log.info("Get assistance: {}", studentId);
            return ApiResponse.success(studentService.getAssistancePlans(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get assistance failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/assistance-plans/{studentId}")
    public ApiResponse<Object> getAssistancePlans(@PathVariable String studentId) {
        try {
            log.info("Get assistance plans: {}", studentId);
            return ApiResponse.success(studentService.getAssistancePlans(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get assistance plans failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 学生更新自己帮扶计划的进度（+ 完成情况说明）。
     *
     * <p>2026-10-02 新增。此前 PC 学生端打的是
     * {@code /counselors/assistance-plans/{id}/progress}，被网关的前缀角色闸挡下
     * （role≠3 一律 403）—— 学生点一次失败一次，且弹窗里那个必填的「完成情况」
     * 前端从来没发出来。端点移到学生自己的前缀下，身份与归属由服务端从网关注入的
     * {@code X-User-Id} 判定（**不信任 body 里的 id**）。
     *
     * <p>⚠️ 这里**刻意不 catch**：参数非法/计划不属于本人由 service 抛
     * {@link IllegalArgumentException}，交给 {@code GlobalExceptionHandler} 回 **HTTP 400**。
     * 若像同文件其它方法那样 catch 后返回 {@code ApiResponse.error}，HTTP 仍是 200、
     * 前端只看状态码就会把失败当成功（本仓反复踩过的那一族）。
     */
    @PostMapping("/assistance/{planId}/progress")
    public ApiResponse<Object> updatePlanProgress(@PathVariable Long planId,
                                                  @RequestBody(required = false) Map<String, Object> request) {
        log.info("Update assistance plan progress: planId={}", planId);
        return ApiResponse.success(studentService.updatePlanProgress(planId, request));
    }

    // ==================== 学业建议 ====================
    @GetMapping("/suggestions/{studentId}")
    public ApiResponse<Object> getSuggestions(@PathVariable String studentId) {
        try {
            log.info("Get suggestions: {}", studentId);
            return ApiResponse.success(studentService.getSuggestions(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get suggestions failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    // ==================== 申诉 (P0新增) ====================
    @PostMapping("/appeals/submit")
    public ApiResponse<Object> submitAppeal(@RequestBody Object request) {
        try {
            log.info("Submit appeal request");
            return ApiResponse.success(studentService.submitAppeal(request));
        } catch (Exception e) {
            log.error("Submit appeal failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /** 兼容旧路径 */
    @PostMapping("/appeal")
    public ApiResponse<Object> submitAppealLegacy(@RequestBody Object request) {
        return submitAppeal(request);
    }

    @GetMapping("/appeals/{studentId}/list")
    public ApiResponse<Object> getStudentAppeals(@PathVariable String studentId) {
        try {
            log.info("Get student appeals: {}", studentId);
            return ApiResponse.success(studentService.getStudentAppeals(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get student appeals failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/appeals/{appealId}/detail")
    public ApiResponse<Object> getAppealDetail(@PathVariable Long appealId) {
        try {
            log.info("Get appeal detail: {}", appealId);
            return ApiResponse.success(studentService.getAppealDetail(appealId));
        } catch (Exception e) {
            log.error("Get appeal detail failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/appeals/{appealId}/withdraw")
    public ApiResponse<Object> withdrawAppeal(@PathVariable Long appealId, @RequestParam Long studentId) {
        try {
            log.info("Withdraw appeal: appealId={}, studentId={}", appealId, studentId);
            return ApiResponse.success(studentService.withdrawAppeal(appealId, studentId));
        } catch (Exception e) {
            log.error("Withdraw appeal failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/appeals/{studentId}/pending")
    public ApiResponse<Object> getPendingAppeals(@PathVariable String studentId) {
        try {
            log.info("Get pending appeals: {}", studentId);
            return ApiResponse.success(studentService.getPendingAppeals(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get pending appeals failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/appeals/{studentId}/statistics")
    public ApiResponse<Object> getAppealStatistics(@PathVariable String studentId) {
        try {
            log.info("Get appeal statistics: {}", studentId);
            return ApiResponse.success(studentService.getAppealStatistics(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get appeal statistics failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/appeals/{studentId}/success-rate")
    public ApiResponse<Object> getAppealSuccessRate(@PathVariable String studentId) {
        try {
            log.info("Get appeal success rate: {}", studentId);
            return ApiResponse.success(studentService.getAppealSuccessRate(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get appeal success rate failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/appeals/{studentId}/by-status")
    public ApiResponse<Object> getAppealsByStatus(@PathVariable String studentId, @RequestParam Integer status) {
        try {
            log.info("Get appeals by status: studentId={}, status={}", studentId, status);
            return ApiResponse.success(studentService.getAppealsByStatus(Long.parseLong(studentId), status));
        } catch (Exception e) {
            log.error("Get appeals by status failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/appeals/{studentId}/history")
    public ApiResponse<Object> getAppealHistory(@PathVariable String studentId,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int pageSize) {
        try {
            log.info("Get appeal history: studentId={}, page={}, pageSize={}", studentId, page, pageSize);
            return ApiResponse.success(studentService.getAppealHistory(Long.parseLong(studentId), page, pageSize));
        } catch (Exception e) {
            log.error("Get appeal history failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/appeals/{studentId}/reason-statistics")
    public ApiResponse<Object> getAppealReasonStatistics(@PathVariable String studentId) {
        try {
            log.info("Get appeal reason statistics: {}", studentId);
            return ApiResponse.success(studentService.getAppealReasonStatistics(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get appeal reason statistics failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    // ==================== 对标分析 (P0新增) ====================
    @GetMapping("/benchmark/{studentId}/latest")
    public ApiResponse<Object> getLatestBenchmark(@PathVariable String studentId) {
        try {
            log.info("Get latest benchmark: {}", studentId);
            return ApiResponse.success(studentService.getLatestBenchmark(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get latest benchmark failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/benchmark/{studentId}/semester")
    public ApiResponse<Object> getSemesterBenchmark(@PathVariable String studentId, @RequestParam String semester) {
        try {
            log.info("Get semester benchmark: {}, semester: {}", studentId, semester);
            return ApiResponse.success(studentService.getSemesterBenchmark(Long.parseLong(studentId), semester));
        } catch (Exception e) {
            log.error("Get semester benchmark failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/benchmark/{studentId}/history")
    public ApiResponse<Object> getHistoryBenchmark(@PathVariable String studentId) {
        try {
            log.info("Get history benchmark: {}", studentId);
            return ApiResponse.success(studentService.getHistoryBenchmark(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get history benchmark failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/benchmark/{studentId}/class-ranking")
    public ApiResponse<Object> getBenchmarkClassRanking(@PathVariable String studentId,
                                                         @RequestParam(required = false) Long classId,
                                                         @RequestParam(required = false) String semester) {
        try {
            log.info("Get benchmark class ranking: studentId={}, classId={}, semester={}", studentId, classId, semester);
            return ApiResponse.success(studentService.getBenchmarkClassRanking(Long.parseLong(studentId), classId, semester));
        } catch (Exception e) {
            log.error("Get benchmark class ranking failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/benchmark/{studentId}/major-ranking")
    public ApiResponse<Object> getBenchmarkMajorRanking(@PathVariable String studentId,
                                                         @RequestParam(required = false) Long majorId,
                                                         @RequestParam(required = false) String semester) {
        try {
            log.info("Get benchmark major ranking: studentId={}, majorId={}, semester={}", studentId, majorId, semester);
            return ApiResponse.success(studentService.getBenchmarkMajorRanking(Long.parseLong(studentId), majorId, semester));
        } catch (Exception e) {
            log.error("Get benchmark major ranking failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/benchmark/class/{classId}/comparison")
    public ApiResponse<Object> getClassBenchmarkComparison(@PathVariable Long classId, @RequestParam(required = false) String semester) {
        try {
            log.info("Get class benchmark comparison: classId={}, semester={}", classId, semester);
            return ApiResponse.success(studentService.getClassBenchmarkComparison(classId, semester));
        } catch (Exception e) {
            log.error("Get class benchmark comparison failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/benchmark/major/{majorId}/comparison")
    public ApiResponse<Object> getMajorBenchmarkComparison(@PathVariable Long majorId, @RequestParam(required = false) String semester) {
        try {
            log.info("Get major benchmark comparison: majorId={}, semester={}", majorId, semester);
            return ApiResponse.success(studentService.getMajorBenchmarkComparison(majorId, semester));
        } catch (Exception e) {
            log.error("Get major benchmark comparison failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/benchmark/{studentId}/progress-report")
    public ApiResponse<Object> getProgressReport(@PathVariable String studentId) {
        try {
            log.info("Get progress report: {}", studentId);
            return ApiResponse.success(studentService.getProgressReport(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get progress report failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    // ==================== 通知中心 (P0新增) ====================
    @GetMapping("/notification-center/{userId}/list")
    public ApiResponse<Object> getNotificationList(@PathVariable String userId,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "10") int pageSize) {
        try {
            log.info("Get notification list: userId={}, page={}, pageSize={}", userId, page, pageSize);
            return ApiResponse.success(studentService.getNotificationCenterList(Long.parseLong(userId), page, pageSize));
        } catch (Exception e) {
            log.error("Get notification list failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/notification-center/{userId}/unread-count")
    public ApiResponse<Object> getNotificationUnreadCount(@PathVariable String userId) {
        try {
            log.info("Get notification unread count: {}", userId);
            return ApiResponse.success(studentService.getNotificationUnreadCount(Long.parseLong(userId)));
        } catch (Exception e) {
            log.error("Get notification unread count failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/notification-center/{userId}/unread")
    public ApiResponse<Object> getUnreadNotifications(@PathVariable String userId) {
        try {
            log.info("Get unread notifications: {}", userId);
            return ApiResponse.success(studentService.getUnreadNotifications(Long.parseLong(userId)));
        } catch (Exception e) {
            log.error("Get unread notifications failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/notification-center/{userId}/mark-batch-read")
    public ApiResponse<Object> markNotificationsBatchRead(@PathVariable String userId, @RequestBody List<Long> notificationIds) {
        try {
            log.info("Mark notifications batch read: userId={}, ids={}", userId, notificationIds);
            return ApiResponse.success(studentService.markNotificationsBatchRead(Long.parseLong(userId), notificationIds));
        } catch (Exception e) {
            log.error("Mark notifications batch read failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 单条通知标记已读。
     *
     * <p>原来这里直接返回 {@code Map.of("success", true, "message", "标记已读成功")} ——
     * 一行库都不写。现在真写（见 {@code StudentServiceImpl.markNotificationRead}），
     * 并如实返回影响行数：id 不存在时 {@code success=false}，不再假装成功。
     * 批量那条 {@code mark-batch-read} 一直是真实现，两条现在口径一致了。
     */
    @PostMapping("/notification-center/{notificationId}/mark-read")
    public ApiResponse<Object> markSingleNotificationRead(@PathVariable Long notificationId) {
        try {
            log.info("Mark single notification read: notificationId={}", notificationId);
            return ApiResponse.success(studentService.markNotificationRead(notificationId));
        } catch (Exception e) {
            log.error("Mark single notification read failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/notification-center/{notificationId}/delete")
    public ApiResponse<Object> deleteNotification(@PathVariable Long notificationId) {
        try {
            log.info("Delete notification: notificationId={}", notificationId);
            return ApiResponse.success(studentService.deleteNotification(notificationId));
        } catch (Exception e) {
            log.error("Delete notification failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/notification-center/{userId}/clear-read")
    public ApiResponse<Object> clearReadNotifications(@PathVariable String userId) {
        try {
            log.info("Clear read notifications: userId={}", userId);
            return ApiResponse.success(studentService.clearReadNotifications(Long.parseLong(userId)));
        } catch (Exception e) {
            log.error("Clear read notifications failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    // 兼容原有路径
    @GetMapping("/notification-center/{studentId}")
    public ApiResponse<Object> getNotificationCenter(@PathVariable String studentId) {
        try {
            log.info("Get notification center (legacy): {}", studentId);
            return ApiResponse.success(studentService.getNotificationCenterList(Long.parseLong(studentId), 1, 10));
        } catch (Exception e) {
            log.error("Get notification center failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PutMapping("/notification-center/{studentId}/read/{notificationId}")
    public ApiResponse<Object> markNotificationAsReadLegacy(@PathVariable String studentId, @PathVariable Long notificationId) {
        try {
            log.info("Mark notification as read (legacy): studentId={}, notificationId={}", studentId, notificationId);
            return ApiResponse.success(studentService.markNotificationsBatchRead(Long.parseLong(studentId), List.of(notificationId)));
        } catch (Exception e) {
            log.error("Mark notification as read failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    // ==================== 订阅管理 (P0新增) ====================
    @GetMapping("/subscription/{studentId}/preferences")
    public ApiResponse<Object> getSubscriptionPreferences(@PathVariable String studentId) {
        try {
            log.info("Get subscription preferences: {}", studentId);
            return ApiResponse.success(studentService.getSubscriptionPreferences(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get subscription preferences failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/subscription/{studentId}/update")
    public ApiResponse<Object> updateSubscriptionPreferences(@PathVariable String studentId, @RequestBody Object preferences) {
        try {
            log.info("Update subscription preferences: {}", studentId);
            return ApiResponse.success(studentService.updateSubscriptionPreferences(Long.parseLong(studentId), preferences));
        } catch (Exception e) {
            log.error("Update subscription preferences failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/subscription/{studentId}/subscribe-level")
    public ApiResponse<Object> subscribeWarningLevel(@PathVariable String studentId, @RequestParam String level) {
        try {
            log.info("Subscribe warning level: studentId={}, level={}", studentId, level);
            return ApiResponse.success(studentService.subscribeWarningLevel(Long.parseLong(studentId), level));
        } catch (Exception e) {
            log.error("Subscribe warning level failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/subscription/{studentId}/unsubscribe-level")
    public ApiResponse<Object> unsubscribeWarningLevel(@PathVariable String studentId, @RequestParam String level) {
        try {
            log.info("Unsubscribe warning level: studentId={}, level={}", studentId, level);
            return ApiResponse.success(studentService.unsubscribeWarningLevel(Long.parseLong(studentId), level));
        } catch (Exception e) {
            log.error("Unsubscribe warning level failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/subscription/{studentId}/set-channel")
    public ApiResponse<Object> setPushChannel(@PathVariable String studentId, @RequestParam String channel, @RequestParam Boolean enabled) {
        try {
            log.info("Set push channel: studentId={}, channel={}, enabled={}", studentId, channel, enabled);
            return ApiResponse.success(studentService.setPushChannel(Long.parseLong(studentId), channel, enabled));
        } catch (Exception e) {
            log.error("Set push channel failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    // 兼容原有路径
    @GetMapping("/subscription/{studentId}")
    public ApiResponse<Object> getSubscriptions(@PathVariable String studentId) {
        return getSubscriptionPreferences(studentId);
    }

    @PutMapping("/subscription/{studentId}")
    public ApiResponse<Object> updateSubscription(@PathVariable String studentId, @RequestBody Object request) {
        return updateSubscriptionPreferences(studentId, request);
    }

    // ==================== 帮扶反馈 (P0新增) ====================
    /**
     * 提交 / 更新帮扶评价。
     *
     * <p>⚠️ 注意这里的**双重信封**：本方法把 service 的返回（可能是
     * {@code {"success":false,"message":"..."}}）再包一层放进 `data`，
     * 而 HTTP 与 `code` 都是 200 —— 判断成败必须看 `data.success`，不是看请求有没有成功。
     */
    @PostMapping("/evaluations/submit")
    public ApiResponse<Object> submitEvaluation(@RequestBody Object evaluation) {
        try {
            log.info("Submit evaluation");
            return ApiResponse.success(studentService.submitEvaluation(evaluation));
        } catch (Exception e) {
            log.error("Submit evaluation failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 某学生的评价列表。
     *
     * <p>⚠️ 路径里的 `{studentId}` 是 **`student_profile.id`**（司明 = 3），
     * 不是 `users.id`（司明 = 27）—— 与 `/students/**` 其它接口的口径**不一样**，
     * 因为底层 SQL 走的是 `assistance_plans.student_id`（存的是 profile id）。
     * 两个 id 空间有重叠值，传错**不报错、只会返回空数组**。
     */
    @GetMapping("/evaluations/{studentId}/list")
    public ApiResponse<Object> getEvaluationsByStudent(@PathVariable String studentId) {
        try {
            log.info("Get evaluations list: {}", studentId);
            return ApiResponse.success(studentService.getEvaluationsByStudent(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get evaluations list failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 某条帮扶计划下的评价。
     *
     * <p>⚠️ 路径里的 `{planId}` 是 **`assistance_plans.id`**，和上面那套 profile id
     * 完全是另一个空间，但两个都是纯数字 —— 调用时别看错路径。没有评价时返回**空数组**（原来回 `{"error":"无评价数据"}`）。
     */
    @GetMapping("/evaluations/{planId}/detail")
    public ApiResponse<Object> getPlanEvaluation(@PathVariable Long planId) {
        try {
            log.info("Get plan evaluation: {}", planId);
            return ApiResponse.success(studentService.getPlanEvaluation(planId));
        } catch (Exception e) {
            log.error("Get plan evaluation failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /** 评价统计。⚠️ `{studentId}` 同样是 **`student_profile.id`**（见上面的 list 接口）。 */
    @GetMapping("/evaluations/{studentId}/statistics")
    public ApiResponse<Object> getEvaluationStatistics(@PathVariable String studentId) {
        try {
            log.info("Get evaluation statistics: {}", studentId);
            return ApiResponse.success(studentService.getEvaluationStatistics(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get evaluation statistics failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    // 兼容原有路径
    @GetMapping("/evaluations/{studentId}")
    public ApiResponse<Object> getEvaluations(@PathVariable String studentId) {
        return getEvaluationsByStudent(studentId);
    }

    @PostMapping("/evaluations/{studentId}")
    public ApiResponse<Object> submitEvaluationLegacy(@PathVariable String studentId, @RequestBody Object request) {
        return submitEvaluation(request);
    }

    // ==================== 设置 (P0新增) ====================
    @GetMapping("/settings/{studentId}")
    public ApiResponse<Object> getStudentSettings(@PathVariable String studentId) {
        try {
            log.info("Get student settings: {}", studentId);
            return ApiResponse.success(studentService.getStudentSettings(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get student settings failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 保存学生设置。
     *
     * <p>⚠️ 原来 {@code return Map.of("success", true)} —— 一个字段都不落库，
     * 表现是"改完提示成功、退出再进全变回去"。现在真写
     * {@code user_settings}（键 {@code users.id}，与 user-service 的
     * {@code /users/{uid}/settings} 同一张表）+ {@code student_profile.privacy_level}。
     *
     * <p>{@code studentId} 路径参数是 {@code users.id} —— 与
     * {@code /users/{uid}/settings} 的键一致，所以这条路由是那个入口的学生侧别名，
     * 不是第二套设置。未识别的 key 会**报错**而不是静默丢弃。
     */
    @PutMapping("/settings/{studentId}")
    public ApiResponse<Object> updateStudentSettings(@PathVariable String studentId,
                                                    @RequestBody(required = false) Map<String, Object> request) {
        try {
            log.info("Update student settings: {}", studentId);
            return ApiResponse.success(studentService.updateStudentSettings(Long.parseLong(studentId), request));
        } catch (Exception e) {
            log.error("Update student settings failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/settings/{studentId}/privacy-level")
    public ApiResponse<Object> updatePrivacyLevel(@PathVariable String studentId, @RequestParam Integer privacyLevel) {
        try {
            log.info("Update privacy level: studentId={}, privacyLevel={}", studentId, privacyLevel);
            return ApiResponse.success(studentService.updatePrivacyLevel(Long.parseLong(studentId), privacyLevel));
        } catch (Exception e) {
            log.error("Update privacy level failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/settings/{studentId}/security-logs")
    public ApiResponse<Object> getSecurityLogs(@PathVariable String studentId, @RequestParam int page, @RequestParam int pageSize) {
        try {
            log.info("Get security logs: {}, page: {}, pageSize: {}", studentId, page, pageSize);
            return ApiResponse.success(studentService.getSecurityLogs(Long.parseLong(studentId), page, pageSize));
        } catch (Exception e) {
            log.error("Get security logs failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    // ==================== 消息 ====================
    @GetMapping("/messages/{studentId}")
    public ApiResponse<Object> getStudentMessages(@PathVariable String studentId) {
        try {
            log.info("Get student messages: {}", studentId);
            return ApiResponse.success(studentService.getStudentMessages(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get student messages failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/messages/{studentId}/unread-count")
    public ApiResponse<Object> getUnreadMessageCount(@PathVariable String studentId) {
        try {
            log.info("Get unread message count: {}", studentId);
            return ApiResponse.success(studentService.getUnreadMessageCount(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get unread message count failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    // ==================== 班级 ====================
    @GetMapping("/class-members/{studentId}")
    public ApiResponse<Object> getClassMembers(@PathVariable String studentId) {
        try {
            log.info("Get class members: {}", studentId);
            // 从班级信息中间接获取，实际可查 student_profile 同班级学生
            return ApiResponse.success(studentService.getClassInfo(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get class members failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/class-ranking/{studentId}")
    public ApiResponse<Object> getClassRanking(@PathVariable String studentId) {
        try {
            log.info("Get class ranking: {}", studentId);
            return ApiResponse.success(studentService.getBenchmarkClassRanking(Long.parseLong(studentId), null, null));
        } catch (Exception e) {
            log.error("Get class ranking failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    // ==================== 注册 ====================
    @PostMapping("/register")
    public ApiResponse<Object> registerStudent(@RequestBody Object request) {
        try {
            log.info("Student register request");
            return ApiResponse.success(studentService.registerStudent(request));
        } catch (Exception e) {
            log.error("Student register failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    // ==================== 学院统计 ====================
    @GetMapping("/count-by-college")
    public ApiResponse<Long> countByCollege(@RequestParam Long collegeId) {
        try {
            log.info("Count students by college: {}", collegeId);
            return ApiResponse.success(studentService.countByCollege(collegeId));
        } catch (Exception e) {
            log.error("Count students by college failed: {}", e.getMessage());
            return ApiResponse.success(0L);
        }
    }

    @GetMapping("/list")
    public ApiResponse<List<Object>> getStudentsByCollege(@RequestParam(required = false) Long collegeId) {
        try {
            log.info("Get students by college: {}", collegeId);
            return ApiResponse.success(studentService.getStudentsByCollege(collegeId));
        } catch (Exception e) {
            log.error("Get students by college failed: {}", e.getMessage());
            return ApiResponse.success(List.of());
        }
    }
}