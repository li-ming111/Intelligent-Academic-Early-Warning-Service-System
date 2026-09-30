package com.academic.mapper;

import com.academic.entity.AssistanceEvaluation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AssistanceEvaluationMapper extends BaseMapper<AssistanceEvaluation> {

    @Select("SELECT * FROM assistance_evaluations WHERE plan_id = #{planId} ORDER BY created_at DESC")
    List<AssistanceEvaluation> selectByPlanId(@Param("planId") Long planId);

    @Select("SELECT ae.* FROM assistance_evaluations ae " +
            "INNER JOIN assistance_plans ap ON ae.plan_id = ap.id " +
            "WHERE ap.student_id = #{studentId} ORDER BY ae.created_at DESC")
    List<AssistanceEvaluation> selectByStudentId(@Param("studentId") Long studentId);

    @Select("SELECT AVG(score) FROM assistance_evaluations ae " +
            "INNER JOIN assistance_plans ap ON ae.plan_id = ap.id " +
            "WHERE ap.student_id = #{studentId}")
    Double avgScoreByStudentId(@Param("studentId") Long studentId);

    /**
     * 有效性评分的平均值。列由
     * {@code db_backups/evaluation_effectiveness_20260930_apply.sql} 添加，
     * 加列前的历史行为 NULL —— AVG 会跳过 NULL，所以"有评价但都没填有效性"时这里返回 null，
     * 而不是 0（0 会被页面当成"有效性 0 分"，与"没人打过分"是两回事）。
     */
    @Select("SELECT AVG(effectiveness) FROM assistance_evaluations ae " +
            "INNER JOIN assistance_plans ap ON ae.plan_id = ap.id " +
            "WHERE ap.student_id = #{studentId}")
    Double avgEffectivenessByStudentId(@Param("studentId") Long studentId);

    @Select("SELECT COUNT(*) FROM assistance_evaluations ae " +
            "INNER JOIN assistance_plans ap ON ae.plan_id = ap.id " +
            "WHERE ap.student_id = #{studentId}")
    int countByStudentId(@Param("studentId") Long studentId);

    /**
     * 该学生所有**有评分**的评价分数，用来在 Java 里算 1..5 的分布。
     *
     * <p>不在 SQL 里 {@code GROUP BY score}，原因有二：① 分布与平均分必须读同一批行，
     * 否则两个数字会互相打架；② {@code COUNT(*) AS cnt} 这种别名经 JDBC 回来时
     * 列名大小写/类型都不确定，取 Map 容易踩空。
     *
     * <p>参数是 {@code student_profile.id}（{@code assistance_plans.student_id} 存的就是它），
     * 不是 {@code users.id} —— 两个 id 空间有重叠值，传错了不会报错，只会静默返回空。
     */
    @Select("SELECT ae.score FROM assistance_evaluations ae " +
            "INNER JOIN assistance_plans ap ON ae.plan_id = ap.id " +
            "WHERE ap.student_id = #{studentId} AND ae.score IS NOT NULL")
    List<Integer> scoresByStudentId(@Param("studentId") Long studentId);
}