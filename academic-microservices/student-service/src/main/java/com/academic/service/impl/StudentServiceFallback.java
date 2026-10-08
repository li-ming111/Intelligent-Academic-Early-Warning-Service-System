package com.academic.service.impl;

import com.academic.common.dto.ApiResponse;
import com.academic.dto.StudentDashboardResponse;
import com.academic.service.StudentService;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 学生服务降级处理类
 *
 * <p><b>⚠️ 2026-10-01 第十七轮查证：本类是个死 bean，永远不会被调用。</b>
 * 它只是个普通的 {@code @Component}，本仓<b>没有任何</b> {@code fallbackClass} /
 * {@code fallback =} 指向它（全仓 grep {@code fallbackClass|blockHandlerClass} 零命中），
 * 而 {@link StudentServiceImpl} 标了 {@code @Primary} —— 容器里每个 {@code StudentService}
 * 注入点拿到的都是 impl。
 *
 * <p>本仓真正生效的降级是<b>另一套</b>：{@code @SentinelResource(fallback = "方法名")}
 * （注意是<b>方法名</b>，不是类），以及各调用点自己的 try/catch。
 * 详见《答辩准备-启动与演示手册》§6.17。
 */
@Component
public class StudentServiceFallback implements StudentService {

    @Override public StudentDashboardResponse getStudentDashboard(Long studentId) { return new StudentDashboardResponse(); }
    @Override public Object getStudentScores(Long studentId) { return ApiResponse.error("获取成绩服务暂时不可用"); }
    @Override public Object getStudentScores(Long studentId, String semester) { return ApiResponse.error("获取成绩服务暂时不可用"); }
    @Override public Object getStudentWarnings(Long studentId) { return ApiResponse.error("获取预警信息服务暂时不可用"); }
    @Override public Object getStudentInfo(Long studentId) { return ApiResponse.error("获取学生信息服务暂时不可用"); }
    @Override public Object getHistoryBenchmark(Long studentId) { return ApiResponse.error("获取历史对标数据服务暂时不可用"); }
    @Override public Object getClassInfo(Long studentId) { return ApiResponse.error("获取班级信息服务暂时不可用"); }
    @Override public Object getAssistancePlans(Long studentId) { return ApiResponse.error("获取帮扶计划服务暂时不可用"); }
    @Override public Object updatePlanProgress(Long planId, Object request) { return ApiResponse.error("更新帮扶进度服务暂时不可用"); }
    @Override public Object getSuggestions(Long studentId) { return ApiResponse.error("获取学业建议服务暂时不可用"); }
    @Override public Object submitAppeal(Object request) { return ApiResponse.error("提交申诉服务暂时不可用"); }
    @Override public Object getStudentAppeals(Long studentId) { return ApiResponse.error("获取申诉列表服务暂时不可用"); }

    // 申诉新增
    @Override public Object getAppealDetail(Long appealId) { return ApiResponse.error("获取申诉详情服务暂时不可用"); }
    @Override public Object withdrawAppeal(Long appealId, Long studentId) { return ApiResponse.error("撤销申诉服务暂时不可用"); }
    @Override public Object getPendingAppeals(Long studentId) { return ApiResponse.error("获取待处理申诉服务暂时不可用"); }
    @Override public Object getAppealStatistics(Long studentId) { return ApiResponse.error("获取申诉统计服务暂时不可用"); }
    @Override public Object getAppealSuccessRate(Long studentId) { return ApiResponse.error("获取申诉成功率服务暂时不可用"); }
    @Override public Object getAppealsByStatus(Long studentId, Integer status) { return ApiResponse.error("获取申诉列表服务暂时不可用"); }
    @Override public Object getAppealHistory(Long studentId, int page, int pageSize) { return ApiResponse.error("获取申诉历史服务暂时不可用"); }
    @Override public Object getAppealReasonStatistics(Long studentId) { return ApiResponse.error("获取申诉原因统计服务暂时不可用"); }

    // 对标分析新增
    @Override public Object getLatestBenchmark(Long studentId) { return ApiResponse.error("获取最新对标分析服务暂时不可用"); }
    @Override public Object getBenchmarkClassRanking(Long studentId, Long classId, String semester) { return ApiResponse.error("获取班级排名服务暂时不可用"); }
    @Override public Object getBenchmarkMajorRanking(Long studentId, Long majorId, String semester) { return ApiResponse.error("获取专业排名服务暂时不可用"); }
    @Override public Object getClassBenchmarkComparison(Long classId, String semester) { return ApiResponse.error("获取班级对标对比服务暂时不可用"); }
    @Override public Object getMajorBenchmarkComparison(Long majorId, String semester) { return ApiResponse.error("获取专业对标对比服务暂时不可用"); }

    // 通知中心新增
    @Override public Object getNotificationCenterList(Long userId, int page, int pageSize) { return ApiResponse.error("获取通知列表服务暂时不可用"); }
    @Override public Object getNotificationUnreadCount(Long userId) { return ApiResponse.error("获取未读通知数量服务暂时不可用"); }
    @Override public Object markNotificationsBatchRead(Long userId, List<Long> notificationIds) { return ApiResponse.error("标记通知已读服务暂时不可用"); }
    @Override public Object deleteNotification(Long notificationId) { return ApiResponse.error("删除通知服务暂时不可用"); }
    @Override public Object markNotificationRead(Long notificationId) { return ApiResponse.error("标记通知已读服务暂时不可用"); }
    @Override public Object clearReadNotifications(Long userId) { return ApiResponse.error("清除已读通知服务暂时不可用"); }
    @Override public Object getUnreadNotifications(Long userId) { return ApiResponse.error("获取未读通知服务暂时不可用"); }

    // 资源归属闸用（W12，2026-10-04）
    /**
     * 降级时返回 {@code null} ⇒ 调用方判为"查不到这条资源的主人"⇒ **拒绝**（fail-closed）。
     * 这里绝不能返回 {@code 0} 或登录人自己的 id —— 那会把"下游挂了"变成"资源是你的"。
     */
    @Override public Long getAppealOwnerProfileId(Long appealId) { return null; }
    @Override public Long getPlanOwnerProfileId(Long planId) { return null; }
    @Override public Long getNotificationOwnerUserId(Long notificationId) { return null; }

    // 订阅管理新增
    /** 降级时返回 {@code null} ⇒ 归属闸判为"认不出身份"⇒ **拒绝**（fail-closed），不会误放行。 */
    @Override public Long getProfileIdByUserId(Long userId) { return null; }
    @Override public Object getSubscriptionPreferences(Long studentId) { return ApiResponse.error("获取订阅偏好服务暂时不可用"); }
    @Override public Object updateSubscriptionPreferences(Long studentId, Object preferences) { return ApiResponse.error("更新订阅偏好服务暂时不可用"); }
    @Override public Object subscribeWarningLevel(Long studentId, String level) { return ApiResponse.error("订阅预警等级服务暂时不可用"); }
    @Override public Object unsubscribeWarningLevel(Long studentId, String level) { return ApiResponse.error("取消订阅预警等级服务暂时不可用"); }
    @Override public Object setPushChannel(Long studentId, String channel, Boolean enabled) { return ApiResponse.error("设置推送渠道服务暂时不可用"); }

    // 帮扶反馈新增
    @Override public Object submitEvaluation(Object evaluation) { return ApiResponse.error("提交评价服务暂时不可用"); }
    @Override public Object getEvaluationsByStudent(Long studentId) { return ApiResponse.error("获取评价列表服务暂时不可用"); }
    @Override public Object getPlanEvaluation(Long planId) { return ApiResponse.error("获取评价详情服务暂时不可用"); }
    @Override public Object getEvaluationStatistics(Long studentId) { return ApiResponse.error("获取评价统计服务暂时不可用"); }

    // 设置新增
    @Override public Object updatePrivacyLevel(Long studentId, Integer privacyLevel) { return ApiResponse.error("更新隐私级别服务暂时不可用"); }

    // 原有
    @Override public Object getSemesterBenchmark(Long studentId, String semester) { return ApiResponse.error("获取学期对标分析服务暂时不可用"); }
    @Override public Object getProgressReport(Long studentId) { return ApiResponse.error("获取进度报告服务暂时不可用"); }
    @Override public Object getStudentSettings(Long studentId) { return ApiResponse.error("获取学生设置服务暂时不可用"); }
    @Override public Object updateStudentSettings(Long userId, java.util.Map<String, Object> body) { return ApiResponse.error("保存学生设置服务暂时不可用"); }
    // 2026-10-08（W29）删掉 getSecurityLogs 的降级实现：接口方法已随端点一并删除。
    @Override public Object getStudentMessages(Long studentId) { return ApiResponse.error("获取学生消息服务暂时不可用"); }
    @Override public Object getUnreadMessageCount(Long studentId) { return ApiResponse.error("获取未读消息数量服务暂时不可用"); }
    @Override public byte[] exportScoresWorkbook(Long userId) { return null; }
    @Override public long countByCollege(Long collegeId) { return 0L; }
    @Override public java.util.List<Object> getStudentsByCollege(Long collegeId) { return Collections.emptyList(); }
}