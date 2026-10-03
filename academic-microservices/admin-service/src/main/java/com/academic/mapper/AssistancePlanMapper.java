package com.academic.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 帮扶计划的学院维度只读查询（admin-service 用）。
 *
 * <p>2026-10-02（W5.3）新增。此前**没有任何接口按学院维度查帮扶计划**：
 * 学院管理员端那个「帮扶情况」页（小程序 `pages/college-admin/assistance.vue`）
 * 调的是 {@code /college-admin/assistance/{userId}}，而那个实现是
 * {@code academicWarningMapper.selectWarningsByCollege()} 换个字段名原样返回
 * （{@code warning_level} 改名成 {@code level}）—— 页面标题写着「帮扶情况」，
 * 列的却是**预警记录**。辅导员端有完整的帮扶计划增删改查，学院管理员端一条都拿不到。
 *
 * <p>⚠️ 这里**故意不建实体**：{@code assistance_plans} 的实体在
 * {@code academic-common}（{@code com.academic.common.entity.AssistancePlan}），
 * 但本模块只需要读几个字段，直查 + 别名更省事，也不必为一个只读视图引入依赖。
 * 与 {@link ScoreAppealMapper} 同一写法。
 */
@Mapper
public interface AssistancePlanMapper {

    /**
     * 本学院学生名下的帮扶计划。
     *
     * <p>归属链路与 {@code AcademicWarningMapper.selectWarningsByCollege} 一字不差：
     * {@code assistance_plans.student_id} 是 {@code student_profile.id}
     * ⇒ {@code student_profile.class_id} ⇒ {@code classes.college_id}。
     * **不是** {@code users.id}（本工程第 N 次踩这个坑）。
     *
     * <p>辅导员姓名走 {@code users}：实测活库 4 行 {@code assistance_plans.counselor_id}
     * 全是 {@code 53}，而 {@code counselor_profiles} 只有 {@code id=1, user_id=53}
     * —— 也就是说这一列存的是 **{@code users.id}**，与 {@code classes.counselor_id} 同源。
     * 若哪天有行按 {@code counselor_profiles.id} 写，这里会如实显示 null 而不是
     * 撞上某个无关用户的名字（宁可空，不可错）。
     */
    @Select("SELECT ap.id, ap.student_id AS studentId, sp.name AS studentName, sp.student_id AS studentNo, "
          + "       cl.name AS className, cu.name AS counselorName, "
          + "       ap.content, ap.status, ap.progress, ap.progress_note AS progressNote, "
          + "       ap.created_at AS createdAt, ap.updated_at AS updatedAt "
          + "  FROM assistance_plans ap "
          + "  JOIN student_profile sp ON sp.id = ap.student_id "
          + "  LEFT JOIN classes cl ON cl.id = sp.class_id "
          + "  LEFT JOIN users cu ON cu.id = ap.counselor_id "
          + " WHERE cl.college_id = #{collegeId} "
          + " ORDER BY ap.created_at DESC, ap.id DESC")
    List<Map<String, Object>> selectPlansByCollege(@Param("collegeId") Long collegeId);
}
