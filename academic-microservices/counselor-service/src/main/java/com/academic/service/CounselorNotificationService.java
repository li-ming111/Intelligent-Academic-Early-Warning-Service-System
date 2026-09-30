package com.academic.service;

import java.util.Map;

/**
 * 辅导员通知服务。
 *
 * <p>发一次通知要落两张表 —— {@code communication_logs}（辅导员侧的发送记录，带发送人）
 * 和 {@code notifications}（学生侧真正会读到的通知）。两张表在同一个库，所以这件事必须
 * 在一个事务里做：以前「写了 communication_logs 却没写 notifications」会是一个
 * 学生永远收不到的"已发送"。
 */
public interface CounselorNotificationService {

    /**
     * 发送通知。接收 {@code POST /counselors/students/notify} 的原始请求体，
     * 兼容两种调用形状：
     * <ul>
     *   <li>通知中心页：{@code {counselorId, title, content, type, recipients:['all'|'warning-students'|id]}}</li>
     *   <li>学生名册页：{@code {student_ids:[id], message}}（无 counselorId，由学生所在班级反推）</li>
     * </ul>
     *
     * @return 真实计数：解析出的接收人数、真正投递到通知中心的行数、写进沟通记录的行数、
     *         以及被跳过的人（如不在该辅导员名下）—— 一个都不静默吞掉
     * @throws IllegalArgumentException 内容为空、接收人一个都解析不出来、counselor_id 缺失等
     */
    Map<String, Object> send(Map<String, Object> data);

    /**
     * 通知历史。数据源是 {@code communication_logs} 里 category 属于
     * {@link #NOTIFICATION_CATEGORIES} 的行，按「同一次发送」归并。
     */
    Map<String, Object> history(Long counselorId, int page, int size);

    /**
     * 每周通知数量：本周 / 上周 / 累计。都来自 {@code communication_logs} 的真实行数。
     */
    Map<String, Object> weeklyCount(Long counselorId);

    /** 「通知」类沟通记录的 category 取值。人工填的「预警约谈/帮扶跟进/日常关怀」不算通知。 */
    java.util.List<String> NOTIFICATION_CATEGORIES = java.util.Arrays.asList("warning", "important", "normal");
}
