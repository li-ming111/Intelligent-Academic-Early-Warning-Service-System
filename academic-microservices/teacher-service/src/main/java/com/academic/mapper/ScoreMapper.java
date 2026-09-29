package com.academic.mapper;

import com.academic.common.entity.Score;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * scores 表 Mapper（同步写入用）
 */
@Mapper
public interface ScoreMapper extends BaseMapper<Score> {

    /**
     * 按学生+课程+学期查重。
     *
     * <p>查重键必须带上 semester：同一门课重修时会再录一次成绩，那一行属于新的学期，
     * 应该新增而不是覆盖旧学期的记录。不带 semester 的话重修会覆盖历史成绩，
     * "按最新一次成绩判定挂科"的口径就退化成"只有一次成绩"，重修抵扣也就无从谈起。
     */
    @Select("SELECT * FROM scores WHERE student_id = #{studentId} "
          + "AND course_id = #{courseId} AND semester = #{semester}")
    Score selectByStudentCourseSemester(@Param("studentId") Long studentId,
                                        @Param("courseId") Long courseId,
                                        @Param("semester") String semester);

    /**
     * 该学生已有的最大学期序号，用作录入时未指定学期的兜底值。
     * {@code scores.semester} 是全局学期序号（int，如 2~6），不是"学年内的第几学期"。
     */
    @Select("SELECT MAX(semester) FROM scores WHERE student_id = #{studentId}")
    Integer selectMaxSemester(@Param("studentId") Long studentId);
}
