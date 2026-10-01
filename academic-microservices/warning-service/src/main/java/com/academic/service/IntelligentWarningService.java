package com.academic.service;

import com.academic.exception.WarningNotFoundException;

import java.util.Map;

/**
 * 智能预警的读写服务。
 *
 * <p><b>失败一律抛异常，不写进返回值（2026-09-30）。</b>原来的写法是把
 * {@code {status:"error", message:"预警不存在"}} 当成**正常返回值**返回，
 * 而 controller 无条件把它包进 {@code ApiResponse.success(...)}，于是失败在协议上
 * 变成了 HTTP 200 + {@code code:200} 的成功响应 —— 前端拦截器只认 {@code code === 200}，
 * 会把这段错误当数据交给调用方。现在：
 * <ul>
 *   <li>{@link IllegalArgumentException} —— 缺参 / 参数不是数字 → controller 翻成 400；</li>
 *   <li>{@link WarningNotFoundException} —— 预警不存在 → controller 翻成 404；</li>
 *   <li>其它异常 → 500。</li>
 * </ul>
 */
public interface IntelligentWarningService {
    /**
     * 生成一条预警（手工触发，与定时扫描是两条独立路径）。
     *
     * <p>会把 {@code type / title / description} 一并写全 —— 这三列有下游读者
     * （辅导员预警列表的「预警原因」列、Excel 导出），只写 {@code warning_message}
     * 会让这些地方显示空白。
     *
     * @throws IllegalArgumentException 缺 {@code studentId}，或 {@code ruleId} 指向的规则不存在
     */
    Object generateWarning(Map<String, Object> request);

    /**
     * 处理一条预警（status=1 + 处理人 + 处理意见 + 处理时间）。
     *
     * @throws IllegalArgumentException 缺 {@code warningId}
     * @throws WarningNotFoundException 预警不存在
     */
    Object handleWarning(Map<String, Object> request);

    /**
     * 获取预警列表
     */
    Object getWarningList(Map<String, Object> params);

    /**
     * 获取预警详情
     *
     * @throws WarningNotFoundException 预警不存在
     * @throws IllegalArgumentException {@code warningId} 不是数字
     */
    Object getWarningDetail(String warningId);

    /**
     * 更新预警状态。
     *
     * @throws IllegalArgumentException 缺 {@code status} 字段
     * @throws WarningNotFoundException 预警不存在
     */
    Object updateWarningStatus(String warningId, Map<String, Object> request);

    /**
     * 根据学生ID获取预警列表
     */
    Object getWarningsByStudentId(Long studentId);

    /**
     * 根据预警等级获取预警列表
     */
    Object getWarningsByLevel(Integer level);
}
