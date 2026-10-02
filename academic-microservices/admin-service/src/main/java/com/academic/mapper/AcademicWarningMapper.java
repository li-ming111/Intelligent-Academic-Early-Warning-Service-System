package com.academic.mapper;

import com.academic.common.entity.AcademicWarning;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface AcademicWarningMapper extends BaseMapper<AcademicWarning> {

    /**
     * 本学院预警汇总。列：{@code warning_count} / {@code pending_count}（未处理）/ {@code high_risk_count}（等级 ≥ 2）。
     *
     * <p>{@code academic_warning.student_id} 存的是 {@code student_profile.id}，
     * 因此经 {@code student_profile.class_id} 关联到学院，而不是直接比 {@code users.id}。
     */
    @Select("SELECT COUNT(*) AS warning_count, "
          + "       SUM(CASE WHEN aw.status = 0 THEN 1 ELSE 0 END) AS pending_count, "
          + "       SUM(CASE WHEN aw.warning_level >= 2 THEN 1 ELSE 0 END) AS high_risk_count "
          + "  FROM academic_warning aw "
          + "  JOIN student_profile sp ON sp.id = aw.student_id "
          + "  JOIN classes cl ON cl.id = sp.class_id "
          + " WHERE cl.college_id = #{collegeId}")
    Map<String, Object> selectCollegeWarningStats(@Param("collegeId") Long collegeId);

    /**
     * 本学院预警明细，带学生姓名与班级。列：{@code id} / {@code student_id} /
     * {@code student_name} / {@code class_name} / {@code description} /
     * {@code warning_level} / {@code status} / {@code created_at}。
     *
     * <p>原实现把整张预警表查回来，再对每条记录 {@code selectById} 查一次学生来
     * 判断是否属于本学院 —— 预警数 × 一次单表查询。改为一次 JOIN。
     * 高等级、新发生的排前面。
     */
    @Select("SELECT aw.id, aw.student_id, sp.name AS student_name, sp.class_name AS class_name, "
          + "       aw.description, aw.warning_level, aw.status, aw.created_at "
          + "  FROM academic_warning aw "
          + "  JOIN student_profile sp ON sp.id = aw.student_id "
          + "  JOIN classes cl ON cl.id = sp.class_id "
          + " WHERE cl.college_id = #{collegeId} "
          + " ORDER BY aw.warning_level DESC, aw.created_at DESC, aw.id DESC")
    List<Map<String, Object>> selectWarningsByCollege(@Param("collegeId") Long collegeId);

    /**
     * 该预警是否属于这个学院。{@code > 0} 即属于。
     *
     * <p>{@code academic_warning.student_id} 是 {@code student_profile.id}，
     * 所以走 {@code student_profile.class_id → classes.college_id} 这条链，
     * 与 {@link #selectWarningsByCollege} 的口径一字不差。
     *
     * <p>用于学院管理员的**写操作**（改状态 / 删行）归属校验：这两个接口原先
     * 连操作人是谁都不收，任何已认证用户拿着任意 warningId 就能改能删。
     */
    @Select("SELECT COUNT(*) FROM academic_warning aw "
          + "  JOIN student_profile sp ON sp.id = aw.student_id "
          + "  JOIN classes cl ON cl.id = sp.class_id "
          + " WHERE aw.id = #{warningId} AND cl.college_id = #{collegeId}")
    int countWarningInCollege(@Param("warningId") Long warningId, @Param("collegeId") Long collegeId);
}
