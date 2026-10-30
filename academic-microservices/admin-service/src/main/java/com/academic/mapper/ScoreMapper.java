package com.academic.mapper;

import com.academic.common.entity.Score;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * {@code scores} 表 Mapper。
 *
 * <p>管理端批量导入成绩时，除了写 {@code score_record}，还要同步写这里。
 * 此前导入只写 {@code score_record}，而预警引擎与学生端统计读的是 {@code scores}，
 * 于是"导入的成绩不产生任何预警"——这正是两张成绩表长期分叉的根源。
 */
@Mapper
public interface ScoreMapper extends BaseMapper<Score> {

    /** 按学生+课程+学期查重（不带学期会让重修覆盖历史成绩）。 */
    @Select("SELECT * FROM scores WHERE student_id = #{studentId} "
          + "AND course_id = #{courseId} AND semester = #{semester}")
    Score selectByStudentCourseSemester(@Param("studentId") Long studentId,
                                        @Param("courseId") Long courseId,
                                        @Param("semester") String semester);

    /** 该学生已有的最大学期序号，用作导入时未指定学期的兜底值。 */
    @Select("SELECT MAX(semester) FROM scores WHERE student_id = #{studentId}")
    Integer selectMaxSemester(@Param("studentId") Long studentId);

    /**
     * 该学生**该门课**已有的最近一条学期。
     *
     * <p>不能沿用 {@link #selectMaxSemester}：学生的最大学期可能来自另一门课，
     * 那样"未指定学期"会在本课程下新建一条高学期记录而不是覆盖原有那条。
     * 口径与 teacher-service 的 {@code ScoreMapper.selectLatestSemester} 一致。
     */
    @Select("SELECT semester FROM scores WHERE student_id = #{studentId} AND course_id = #{courseId} "
          + "ORDER BY semester DESC, id DESC LIMIT 1")
    Integer selectLatestSemester(@Param("studentId") Long studentId,
                                 @Param("courseId") Long courseId);
}
