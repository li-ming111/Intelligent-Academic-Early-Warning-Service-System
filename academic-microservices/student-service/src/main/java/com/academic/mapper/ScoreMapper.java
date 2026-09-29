package com.academic.mapper;

import com.academic.common.constant.ScoreCaliber;
import com.academic.common.entity.Score;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface ScoreMapper extends BaseMapper<Score> {

    /**
     * 每门课程取最新一次有效成绩（统一口径，见 {@link ScoreCaliber}）。
     * 用于仪表盘的统计口径：课程去重、排除期末未录入、重修通过抵扣历史挂科。
     *
     * <p>注意与 {@link #selectByStudentId} 的区别：那个方法返回成绩单明细
     * （含全部学期与重修记录），不能用来统计门数与挂科数 —— 直接数行会把
     * 重修记录和未录入的课一起算进去。
     */
    @Select(ScoreCaliber.STUDENT_LATEST_SCORES)
    List<Map<String, Object>> selectLatestScoresByCourse(@Param("studentId") Long profileId);

    /** 挂科门数（统一口径）。参数是 student_profile.id，不是 users.id。 */
    @Select(ScoreCaliber.FAIL_COUNT_BY_ONE_STUDENT)
    Integer countFailCourses(@Param("studentId") Long profileId);

    @Select("SELECT s.id, s.course_id, s.student_id, s.semester, s.year, "
          + "s.regular_score, s.final_score, s.score_total, s.grade, s.grade_point, "
          + "s.created_at, s.updated_at, "
          + "c.name as course_name, c.credits as credits "
          + "FROM scores s LEFT JOIN courses c ON s.course_id = c.id "
          + "WHERE s.student_id = #{studentId} AND s.year IS NOT NULL AND s.semester IS NOT NULL "
          + "ORDER BY s.created_at DESC")
    @Results({@Result(column = "course_name", property = "courseName"), @Result(column = "credits", property = "credits")})
    List<Score> selectByStudentId(String studentId);

    /**
     * 某学生某学期的成绩。{@code semester} 是 {@code scores.semester} 的**全局学期序号**。
     *
     * <p>不再接受 {@code year} 参数：该列与 {@code semester} 不自洽（同一序号在不同行
     * 挂着不同年份），按它等值过滤会把该学期的行排除掉，是"学期下拉永远查不到成绩"的成因。
     * 学期身份只由 {@code semester} 一个字段决定。
     */
    @Select("SELECT s.id, s.course_id, s.student_id, s.semester, s.year, "
          + "s.regular_score, s.final_score, s.score_total, s.grade, s.grade_point, "
          + "s.created_at, s.updated_at, "
          + "c.name as course_name, c.credits as credits "
          + "FROM scores s LEFT JOIN courses c ON s.course_id = c.id "
          + "WHERE s.student_id = #{studentId} AND s.semester = #{semester} "
          + "ORDER BY s.created_at DESC")
    @Results({@Result(column = "course_name", property = "courseName"), @Result(column = "credits", property = "credits")})
    List<Score> selectByStudentIdAndSemester(@org.apache.ibatis.annotations.Param("studentId") String studentId,
                                               @org.apache.ibatis.annotations.Param("semester") Integer semester);

    /** 单名学生的成绩汇总（门数/挂科数/已修学分/GPA），仪表盘用。参数是 student_profile.id。 */
    @Select(ScoreCaliber.ONE_STUDENT_STATS)
    Map<String, Object> selectOneStudentStats(@Param("studentId") Long profileId);

    /**
     * 同班同学的平均 GPA。
     *
     * <p>改用 {@link ScoreCaliber#STUDENT_GPA}：先按学生算出各自的学分加权 GPA，再对
     * 这些 GPA 取平均。原实现是 "<b>所有成绩行</b>的加权总分 /20" —— 那是全院所有课程
     * 混在一起的平均分，不是任何一名学生的 GPA 的平均，学生数多的班级会被成绩门数多的
     * 学生主导。而且它读的是 {@code score_record}。
     */
    @Select("SELECT AVG(g.gpa) FROM " + ScoreCaliber.STUDENT_GPA + " g "
          + " JOIN student_profile sp ON sp.id = g.student_id "
          + " WHERE sp.class_id = #{classId} AND g.gpa IS NOT NULL")
    Double calcClassAvgGpa(@Param("classId") Long classId);

    /** 同专业同学的平均 GPA。口径同 {@link #calcClassAvgGpa}。 */
    @Select("SELECT AVG(g.gpa) FROM " + ScoreCaliber.STUDENT_GPA + " g "
          + " JOIN student_profile sp ON sp.id = g.student_id "
          + " WHERE sp.major_id = #{majorId} AND g.gpa IS NOT NULL")
    Double calcMajorAvgGpa(@Param("majorId") Long majorId);

    /**
     * 班级排名：GPA 高于该生的同班同学数 + 1。并列时两人名次相同。
     * 参数 {@code profileId} 是 student_profile.id，不是 users.id。
     */
    @Select("SELECT COUNT(*) + 1 FROM " + ScoreCaliber.STUDENT_GPA + " g "
          + " JOIN student_profile sp ON sp.id = g.student_id "
          + " WHERE sp.class_id = #{classId} AND g.gpa IS NOT NULL "
          + "   AND g.gpa > (SELECT g2.gpa FROM " + ScoreCaliber.STUDENT_GPA + " g2 "
          + "                WHERE g2.student_id = #{profileId})")
    Integer calcClassRank(@Param("profileId") Long profileId, @Param("classId") Long classId);

    /** 班级参加排名的总人数（有 GPA 的学生数）。分母必须与 {@link #calcClassRank} 一致。 */
    @Select("SELECT COUNT(*) FROM " + ScoreCaliber.STUDENT_GPA + " g "
          + " JOIN student_profile sp ON sp.id = g.student_id "
          + " WHERE sp.class_id = #{classId} AND g.gpa IS NOT NULL")
    Integer countClassStudents(@Param("classId") Long classId);

    /** 同专业参加排名的总人数。 */
    @Select("SELECT COUNT(*) FROM " + ScoreCaliber.STUDENT_GPA + " g "
          + " JOIN student_profile sp ON sp.id = g.student_id "
          + " WHERE sp.major_id = #{majorId} AND g.gpa IS NOT NULL")
    Integer countMajorStudents(@Param("majorId") Long majorId);

    /**
     * 班级 GPA 排行榜（实算）。
     *
     * <p>取代 {@code benchmark_analysis} 快照：那张表不会被任何代码更新，
     * 且 {@code class_avg_gpa}、{@code class_total} 是种子数据里写死的常量。
     * 列：profileId / studentNo / studentName / className / gpa / gradedCredits。
     */
    @Select("SELECT g.student_id AS profileId, sp.student_id AS studentNo, sp.name AS studentName, "
          + "       sp.class_name AS className, g.gpa AS gpa, g.graded_credits AS gradedCredits "
          + "  FROM " + ScoreCaliber.STUDENT_GPA + " g "
          + "  JOIN student_profile sp ON sp.id = g.student_id "
          + " WHERE sp.class_id = #{classId} AND g.gpa IS NOT NULL "
          + " ORDER BY g.gpa DESC, sp.id")
    List<Map<String, Object>> selectClassGpaRanking(@Param("classId") Long classId);

    /** 专业 GPA 排行榜（实算）。字段同 {@link #selectClassGpaRanking}。 */
    @Select("SELECT g.student_id AS profileId, sp.student_id AS studentNo, sp.name AS studentName, "
          + "       sp.class_name AS className, g.gpa AS gpa, g.graded_credits AS gradedCredits "
          + "  FROM " + ScoreCaliber.STUDENT_GPA + " g "
          + "  JOIN student_profile sp ON sp.id = g.student_id "
          + " WHERE sp.major_id = #{majorId} AND g.gpa IS NOT NULL "
          + " ORDER BY g.gpa DESC, sp.id")
    List<Map<String, Object>> selectMajorGpaRanking(@Param("majorId") Long majorId);

    /** 专业排名：GPA 高于该生的同专业同学数 + 1。 */
    @Select("SELECT COUNT(*) + 1 FROM " + ScoreCaliber.STUDENT_GPA + " g "
          + " JOIN student_profile sp ON sp.id = g.student_id "
          + " WHERE sp.major_id = #{majorId} AND g.gpa IS NOT NULL "
          + "   AND g.gpa > (SELECT g2.gpa FROM " + ScoreCaliber.STUDENT_GPA + " g2 "
          + "                WHERE g2.student_id = #{profileId})")
    Integer calcMajorRank(@Param("profileId") Long profileId, @Param("majorId") Long majorId);

    /**
     * 某学生某一学期的学分加权 GPA（趋势图用）。
     * 参数是 student_profile.id。
     */
    @Select("SELECT " + ScoreCaliber.GPA_EXPR + " "
          + " FROM scores v LEFT JOIN courses c ON c.id = v.course_id "
          + " WHERE v.student_id = #{studentId} AND v.semester = #{semester} "
          + "   AND v.final_score IS NOT NULL")
    Double calcSemesterGpa(@Param("studentId") Long profileId, @Param("semester") Integer semester);

    /** 成绩单上出现的所有学期序号，倒序。趋势图与学期下拉都用它。 */
    @Select("SELECT DISTINCT s.semester FROM scores s "
          + " WHERE s.student_id = #{studentId} AND s.final_score IS NOT NULL "
          + " ORDER BY s.semester DESC")
    List<Integer> selectSemesters(@Param("studentId") Long profileId);

    /**
     * 某课程在本班（同班已修该课的学生）的平均分，供单科对比。
     * 参数是 student_profile.id，班级由该生档案决定，避免调用方再传一次 classId 造成不一致。
     */
    @Select("SELECT AVG(v.score_total) FROM " + ScoreCaliber.LATEST_VALID_SCORE + " v "
          + " JOIN student_profile sp ON sp.id = v.student_id "
          + " WHERE sp.class_id = (SELECT sp2.class_id FROM student_profile sp2 WHERE sp2.id = #{studentId}) "
          + "   AND v.course_id = #{courseId}")
    Double calcClassAvgScoreOfCourse(@Param("studentId") Long profileId, @Param("courseId") Long courseId);

    /** 某课程在全专业的平均分。 */
    @Select("SELECT AVG(v.score_total) FROM " + ScoreCaliber.LATEST_VALID_SCORE + " v "
          + " JOIN student_profile sp ON sp.id = v.student_id "
          + " WHERE sp.major_id = (SELECT sp2.major_id FROM student_profile sp2 WHERE sp2.id = #{studentId}) "
          + "   AND v.course_id = #{courseId}")
    Double calcMajorAvgScoreOfCourse(@Param("studentId") Long profileId, @Param("courseId") Long courseId);
}
