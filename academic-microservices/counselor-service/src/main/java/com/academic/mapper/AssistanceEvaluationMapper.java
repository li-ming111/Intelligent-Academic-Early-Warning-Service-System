package com.academic.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 帮扶评价读取 Mapper（辅导员侧）。
 *
 * <p><b>只读，不建实体</b>：{@code assistance_evaluations} 在仓库里已经有两个同名实体
 * （{@code academic-common} 与 {@code student-service} 各一个，后者是活的），
 * 再建第三个只会让"改哪份"更难判断。这里沿用 {@code admin-service.ScoreAppealMapper}
 * 的写法：只挂 {@code @Select}，直接回 {@code Map}。
 *
 * <h3>为什么辅导员侧只有读、没有写</h3>
 * 这张表存的是<b>学生对帮扶计划的评价</b>（{@code content} 反馈内容 / {@code score} 评分 /
 * {@code effectiveness} 有效性），写入方唯一，在
 * {@code student-service.StudentServiceImpl#submitEvaluation}。辅导员侧的 {@code counselor_id}
 * 由该计划带出来，不是"谁写的"。
 *
 * <p>{@code FEATURES.md} 曾把 {@code POST /counselors/assistance-plans/{id}/evaluation}
 * 写成"辅导员提交帮扶评估"，但本表没有任何"评价人角色"列：如果辅导员也往同一
 * {@code plan_id} 写一行，学生侧的 upsert（取 {@code selectByPlanId().get(0)}）就会
 * 更新到辅导员那一行，学生的评价被静默覆盖。与其加一个会互相踩的写入方，
 * 不如让辅导员**读**学生的评价 —— 这正是辅导员真正需要的信息。
 */
@Mapper
public interface AssistanceEvaluationMapper {

    /**
     * 某条帮扶计划的全部评价（学生提交的）。
     *
     * <p>参数是 {@code assistance_plans.id}。
     */
    @Select("SELECT ae.id AS id, ae.plan_id AS planId, ae.counselor_id AS counselorId, "
          + "       ae.content AS content, ae.score AS score, ae.effectiveness AS effectiveness, "
          + "       ae.created_at AS createdAt, ae.updated_at AS updatedAt, "
          + "       sp.name AS studentName, sp.student_id AS studentNo, "
          + "       ap.content AS planContent "
          + "  FROM assistance_evaluations ae "
          + "  JOIN assistance_plans ap ON ap.id = ae.plan_id "
          + "  LEFT JOIN student_profile sp ON sp.id = ap.student_id "
          + " WHERE ae.plan_id = #{planId} "
          + " ORDER BY ae.created_at DESC")
    List<Map<String, Object>> selectByPlanId(@Param("planId") Long planId);

    /**
     * 某辅导员名下所有帮扶计划收到的评价（按计划归集，供辅导员页汇总展示）。
     *
     * <p>参数是 {@code assistance_plans.counselor_id}（即 {@code users.id}）。
     */
    @Select("SELECT ae.id AS id, ae.plan_id AS planId, ae.content AS content, "
          + "       ae.score AS score, ae.effectiveness AS effectiveness, "
          + "       ae.created_at AS createdAt, "
          + "       sp.name AS studentName, sp.student_id AS studentNo, "
          + "       ap.content AS planContent "
          + "  FROM assistance_evaluations ae "
          + "  JOIN assistance_plans ap ON ap.id = ae.plan_id "
          + "  LEFT JOIN student_profile sp ON sp.id = ap.student_id "
          + " WHERE ap.counselor_id = #{counselorId} "
          + " ORDER BY ae.created_at DESC")
    List<Map<String, Object>> selectByCounselorId(@Param("counselorId") Long counselorId);
}
