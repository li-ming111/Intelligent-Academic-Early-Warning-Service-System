package com.academic.mapper;

import com.academic.common.constant.ScoreCaliber;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 成绩只读 Mapper（供预警定时任务使用）。
 *
 * <p>数据源是 {@code scores} —— 项目的权威成绩表。此前本职责由
 * {@code ScoreRecordReadMapper} 承担，它读的是 {@code score_record}（教师录入暂存表），
 * 且 JOIN 条件写成 {@code sr.student_id = sp.user_id}，而该表的 student_id 存的其实是
 * {@code student_profile.id}，两个 ID 空间不相交，导致查询结果为空、
 * 预警任务从未真正生成过任何预警。
 *
 * <p>统计口径统一由 {@link ScoreCaliber} 提供，SQL 片段是编译期常量，直接拼进注解。
 */
@Mapper
public interface ScoreReadMapper {

    /**
     * 每名学生的挂科门数（已按课程去重、取最新一次有效成绩、排除期末未录入）。
     * 列：studentId / studentName / failCount / avgScore。
     */
    @Select(ScoreCaliber.FAIL_COUNT_BY_STUDENT)
    List<Map<String, Object>> findFailCountByStudent();

    /**
     * 某名学生挂科明细，按分数升序，首条即最低分。
     * 列：courseId / courseName / scoreTotal / semester。
     */
    @Select(ScoreCaliber.FAIL_DETAIL_BY_STUDENT)
    List<Map<String, Object>> findFailDetailByStudent(@Param("studentId") Long studentId);

    /**
     * 已有的预警判重键，用于避免重复生成。
     * 列：{@code studentId} / {@code failCount}（可能为 NULL）/ {@code status}。
     *
     * <p><b>2026-09-30 改</b>：从前是 {@code SELECT DISTINCT student_id ... WHERE status = 0}，
     * 于是"把一条预警处理掉"就等于"给这名学生开了绿灯"—— 下一轮扫描只要他挂科门数仍 &gt; 0
     * 就再插一条新的（新 id、status=0），页面上同一个人同时挂着"已处理"和"待处理"。
     *
     * <p>现在**不看 status**、看的是「学生 + 挂科门数」这对键：同一个问题只报一次，
     * 挂科门数真的变了（变严重/变轻）才会再生成一条。
     */
    @Select("SELECT student_id AS studentId, fail_count AS failCount, status "
          + "  FROM academic_warning")
    List<Map<String, Object>> findExistingWarningKeys();
}
