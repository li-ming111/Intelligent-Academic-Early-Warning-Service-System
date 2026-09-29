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
     * 已有未处理预警的学生 ID，用于避免重复生成。
     */
    @Select("SELECT DISTINCT student_id FROM academic_warning WHERE status = 0")
    List<Long> findExistingWarningStudentIds();
}
