package com.academic.mapper;

import com.academic.common.entity.AcademicWarning;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface WarningMapper extends BaseMapper<AcademicWarning> {

    @Select("SELECT warning_level, COUNT(*) as count FROM academic_warning " +
            "WHERE student_id IN (SELECT sp.id FROM student_profile sp " +
            "WHERE sp.class_id IN (SELECT id FROM classes WHERE counselor_id = #{counselorId})) " +
            "GROUP BY warning_level")
    List<Map<String, Object>> getWarningDistributionByCounselor(@Param("counselorId") Long counselorId);

    @Select("SELECT DATE_FORMAT(created_at, '%Y-%m') as month, warning_level, COUNT(*) as count " +
            "FROM academic_warning " +
            "WHERE student_id IN (SELECT sp.id FROM student_profile sp " +
            "WHERE sp.class_id IN (SELECT id FROM classes WHERE counselor_id = #{counselorId})) " +
            "AND created_at >= DATE_SUB(NOW(), INTERVAL 6 MONTH) " +
            "GROUP BY DATE_FORMAT(created_at, '%Y-%m'), warning_level " +
            "ORDER BY month ASC")
    List<Map<String, Object>> getWarningTrendByCounselor(@Param("counselorId") Long counselorId);

    @Select("SELECT AVG(TIMESTAMPDIFF(DAY, created_at, handled_at)) as avg_handle_days " +
            "FROM academic_warning " +
            "WHERE student_id IN (SELECT sp.id FROM student_profile sp " +
            "WHERE sp.class_id IN (SELECT id FROM classes WHERE counselor_id = #{counselorId})) " +
            "AND handled_at IS NOT NULL")
    Map<String, Object> getAvgHandleTimeByCounselor(@Param("counselorId") Long counselorId);

    /**
     * 辅导员名下按班级汇总的预警数。
     * 列：{@code classId} / {@code warningCount} / {@code pendingCount}（未处理）/ {@code highRiskCount}（等级 ≥ 2）。
     *
     * <p>{@code academic_warning.student_id} 存的是 {@code student_profile.id}，
     * 因此按 {@code sp.class_id} 关联 —— 直接 JOIN {@code classes} 会因为 ID 空间不同而全部失配。
     * 班级预警对比此前把每班的 {@code warningCount} 写成 0。
     */
    @Select("SELECT sp.class_id AS classId, COUNT(*) AS warningCount, "
          + "       SUM(CASE WHEN aw.status = 0 THEN 1 ELSE 0 END) AS pendingCount, "
          + "       SUM(CASE WHEN aw.warning_level >= 2 THEN 1 ELSE 0 END) AS highRiskCount "
          + "  FROM academic_warning aw "
          + "  JOIN student_profile sp ON sp.id = aw.student_id "
          + " WHERE sp.class_id IN (SELECT cl.id FROM classes cl WHERE cl.counselor_id = #{counselorId}) "
          + " GROUP BY sp.class_id")
    List<Map<String, Object>> getWarningCountByClass(@Param("counselorId") Long counselorId);
}