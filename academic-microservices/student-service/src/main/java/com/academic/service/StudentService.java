package com.academic.service;

import com.academic.dto.StudentDashboardResponse;

public interface StudentService {
    
    /** 获取学生仪表盘数据 */
    StudentDashboardResponse getStudentDashboard(Long studentId);
    /** 获取学生成绩 */
    Object getStudentScores(Long studentId);
    /** 获取学生成绩（按学期） */
    Object getStudentScores(Long studentId, String semester);
    /** 获取学生预警信息 */
    Object getStudentWarnings(Long studentId);
    /** 获取学生信息 */
    Object getStudentInfo(Long studentId);
    /** 学生注册 */
    Object registerStudent(Object request);
    /** 获取历史GPA数据 */
    Object getHistoryBenchmark(Long studentId);
    /** 获取班级信息 */
    Object getClassInfo(Long studentId);
    /** 获取帮扶计划 */
    Object getAssistancePlans(Long studentId);
    /**
     * 学生更新自己帮扶计划的进度与完成情况说明。
     * 身份取网关注入的当前用户，body：{@code {progress:0-100, note?}}。
     */
    Object updatePlanProgress(Long planId, Object request);
    /** 获取学业建议 */
    Object getSuggestions(Long studentId);
    /** 提交成绩申诉 */
    Object submitAppeal(Object request);
    /** 获取学生申诉列表 */
    Object getStudentAppeals(Long studentId);

    // ========== 申诉新增方法 ==========
    /** 获取申诉详情 */
    Object getAppealDetail(Long appealId);
    /** 撤销申诉 */
    Object withdrawAppeal(Long appealId, Long studentId);
    /** 获取待处理申诉 */
    Object getPendingAppeals(Long studentId);
    /** 获取申诉统计 */
    Object getAppealStatistics(Long studentId);
    /** 获取申诉成功率 */
    Object getAppealSuccessRate(Long studentId);
    /** 按状态获取申诉 */
    Object getAppealsByStatus(Long studentId, Integer status);
    /** 获取申诉历史（分页） */
    Object getAppealHistory(Long studentId, int page, int pageSize);
    /** 获取申诉原因统计 */
    Object getAppealReasonStatistics(Long studentId);

    // ========== 对标分析新增方法 ==========
    /** 获取最新对标分析 */
    Object getLatestBenchmark(Long studentId);
    /** 获取班级排名 */
    Object getBenchmarkClassRanking(Long studentId, Long classId, String semester);
    /** 获取专业排名 */
    Object getBenchmarkMajorRanking(Long studentId, Long majorId, String semester);
    /** 获取班级对标对比 */
    Object getClassBenchmarkComparison(Long classId, String semester);
    /** 获取专业对标对比 */
    Object getMajorBenchmarkComparison(Long majorId, String semester);

    // ========== 通知中心新增方法 ==========
    /** 获取通知列表（分页） */
    Object getNotificationCenterList(Long userId, int page, int pageSize);
    /** 获取未读通知数量 */
    Object getNotificationUnreadCount(Long userId);
    /** 批量标记通知已读 */
    Object markNotificationsBatchRead(Long userId, java.util.List<Long> notificationIds);
    /** 删除通知 */
    Object deleteNotification(Long notificationId);
    /** 单条通知标记已读（键 notifications.id） */
    Object markNotificationRead(Long notificationId);
    /** 清除已读通知 */
    Object clearReadNotifications(Long userId);
    /** 获取未读通知列表 */
    Object getUnreadNotifications(Long userId);

    // ========== 订阅管理新增方法 ==========
    /** 获取订阅偏好 */
    Object getSubscriptionPreferences(Long studentId);
    /** 更新订阅偏好 */
    Object updateSubscriptionPreferences(Long studentId, Object preferences);
    /** 订阅预警等级 */
    Object subscribeWarningLevel(Long studentId, String level);
    /** 取消订阅预警等级 */
    Object unsubscribeWarningLevel(Long studentId, String level);
    /** 设置推送渠道 */
    Object setPushChannel(Long studentId, String channel, Boolean enabled);

    // ========== 帮扶反馈新增方法 ==========
    /**
     * 提交 / 更新帮扶评价。
     * 提交体认识：planId（必填）、score|rating（必填，1-5 整数）、effectiveness（1-5 整数）、
     * content|feedback、id（带则更新该行）、counselorId（只用于比对，取值以计划为准）、
     * studentId（只用于比对，取值以计划为准）。失败返回 {@code {success:false,message}} —— HTTP 仍是 200。
     */
    Object submitEvaluation(Object evaluation);
    /**
     * 获取学生评价列表。
     * ⚠️ 参数是 **student_profile.id**（不是 users.id）；传错不报错、只会永远返回空数组。
     */
    Object getEvaluationsByStudent(Long studentId);
    /** 获取帮扶计划评价。⚠️ 参数是 **assistance_plans.id**（与上面那套 id 空间不同）。 */
    Object getPlanEvaluation(Long planId);
    /** 获取评价统计。⚠️ 参数是 **student_profile.id**。 */
    Object getEvaluationStatistics(Long studentId);

    // ========== 设置新增方法 ==========
    /** 更新隐私级别 */
    Object updatePrivacyLevel(Long studentId, Integer privacyLevel);

    // ========== 原有方法 ==========
    /** 获取学期对标分析数据 */
    Object getSemesterBenchmark(Long studentId, String semester);
    /** 获取进度报告 */
    Object getProgressReport(Long studentId);
    /** 获取学生设置（键 users.id；真实来源 user_settings + student_profile.privacy_level） */
    Object getStudentSettings(Long studentId);
    /** 保存学生设置（部分更新；未识别的 key 会明确报错而不是静默丢弃） */
    Object updateStudentSettings(Long userId, java.util.Map<String, Object> body);
    /** 获取安全日志 */
    Object getSecurityLogs(Long studentId, int page, int pageSize);
    /** 获取学生消息 */
    Object getStudentMessages(Long studentId);
    /** 获取未读消息数量 */
    Object getUnreadMessageCount(Long studentId);
    /** 导出学生成绩到Excel */
    Object exportScoresToExcel(Long studentId);
    /** 下载成绩 */
    Object downloadScores(Long studentId);
    /** 按学院统计学生数量 */
    long countByCollege(Long collegeId);
    /** 按学院获取学生列表 */
    java.util.List<Object> getStudentsByCollege(Long collegeId);
}
