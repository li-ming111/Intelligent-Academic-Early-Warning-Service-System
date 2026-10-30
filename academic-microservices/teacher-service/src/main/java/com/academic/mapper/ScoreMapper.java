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
     * 该学生**该门课**已有的最近一条学期，用作录入时未指定学期的兜底值。
     *
     * <p>此前取的是 {@code MAX(semester) WHERE student_id = ?}（跨所有课程）。学生的
     * 最大学期可能来自另一门课，于是"沿用最近学期"会在这门课下**新建**一条高学期记录，
     * 而不是覆盖原有那条 —— 同一门课留下两行、学期标签还是错的。改为按课程取最近一条，
     * 与小程序「沿用最近学期 = 该生该课最近一条，覆盖」的契约一致。
     *
     * <p>{@code scores.semester} 是全局学期序号（int，如 2~6），不是"学年内的第几学期"。
     */
    @Select("SELECT semester FROM scores WHERE student_id = #{studentId} AND course_id = #{courseId} "
          + "ORDER BY semester DESC, id DESC LIMIT 1")
    Integer selectLatestSemester(@Param("studentId") Long studentId,
                                 @Param("courseId") Long courseId);
}
