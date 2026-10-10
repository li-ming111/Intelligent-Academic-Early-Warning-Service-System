package com.academic.mapper;

import com.academic.common.constant.ScoreCaliber;
import com.academic.common.entity.ScoreRecord;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

/**
 * 教师端成绩读投影（数据源 = 权威表 {@code scores}）。
 *
 * <p>2026-10-10（W36）：本接口过去叫 {@code ScoreRecordMapper}，读的是 {@code score_record}
 * （教师录入暂存表），与辅导员 / 学生 / 预警读的 {@code scores} 分叉。现在读端一律改走
 * {@link ScoreCaliber#LATEST_VALID_SCORE}（每门课每生取最新一次有效成绩），
 * 返回类型仍是 {@link ScoreRecord} 以便控制器消费面不变。
 *
 * <p>名字里的 "Record" 是历史残留（对应的实体叫 {@code ScoreRecord}），
 * 数据源已经不是 {@code score_record} 了。
 */
public interface ScoreRecordMapper {

    /**
     * 权威投影：来源是 {@code scores}，每门课每生取「最新一次有效成绩」
     * （{@code final_score IS NOT NULL}），并补齐姓名 / 学号 / 班级 / 课程名 / 五级档。
     *
     * <p>{@code semester} 是 {@code scores} 的 int 列，映射到 {@code ScoreRecord.semester}（String）
     * 由 MyBatis 按属性类型转换；{@code scoreLevel} 由 {@link ScoreCaliber#SCORE_LEVEL_CASE} 按总评
     * 派生（{@code scores} 表不存这一列）；{@code score} 与 {@code scoreTotal} 同值，兼容两个消费面。
     *
     * <p><b>刻意不查 {@code credits}/{@code type}</b>：早先这两列是给"选课花名册"那类消费方用的，
     * 但它们只被 {@link #getScoresByClassId} 与控制器里另起 JOIN 的两处消费，而
     * {@code ScoreRecord} 实体**没有** {@code credits}/{@code type} 属性 —— {@code courses.credits}
     * （decimal）映射到实体的 {@code Double} 字段会在 MyBatis 里抛
     * {@code UnsupportedOperationException: cannot convert BigDecimal to Double}。
     * 与其在两处各写一遍类型处理器，不如让这条公共投影只取实体真正有的列。
     */
    String LATEST_VALID_SELECT =
            "SELECT v.id AS id, v.student_id AS studentId, sp.student_id AS studentNo, "
          + "       sp.name AS studentName, sp.class_name AS className, "
          + "       v.course_id AS courseId, c.name AS courseName, "
          + "       v.regular_score AS regularScore, v.final_score AS finalScore, "
          + "       v.score_total AS scoreTotal, v.score_total AS score, "
          + "       v.grade AS grade, v.grade_point AS gradePoint, "
          + "       v.semester AS semester, v.year AS year, v.remark AS remark, "
          +          ScoreCaliber.SCORE_LEVEL_CASE + " AS scoreLevel "
          + "  FROM " + ScoreCaliber.LATEST_VALID_SCORE + " v "
          + "  JOIN student_profile sp ON sp.id = v.student_id "
          + "  LEFT JOIN courses c ON c.id = v.course_id ";

    /** 某门课的权威成绩单（每生一行、最新一次有效成绩）。 */
    @Select(LATEST_VALID_SELECT + "WHERE v.course_id = #{courseId} ORDER BY sp.name")
    List<ScoreRecord> listLatestValidByCourse(@Param("courseId") Long courseId);

    /** 某班级（按 {@code student_profile.class_id}）的权威成绩单。 */
    @Select(LATEST_VALID_SELECT + "WHERE sp.class_id = #{classId} ORDER BY sp.name")
    List<ScoreRecord> listLatestValidByClassId(@Param("classId") Long classId);

    /**
     * 某班级（按**班级名**）的权威成绩单。
     *
     * <p>经 {@code classes.name → classes.id → student_profile.class_id}，而不是用
     * {@code student_profile.class_name} 这个冗余列 —— 后者与 {@code classes.name}
     * 可能漂移，班级归属的唯一真源是 {@code class_id}。
     *
     * <p>{@code classes.name} 没有唯一约束，同名班会同时命中（数据模型上限，见
     * {@code ClassInfoMapper.countClassesNamedAndOwned}）。
     */
    @Select(LATEST_VALID_SELECT + "WHERE sp.class_id IN (SELECT id FROM classes WHERE name = #{className}) ORDER BY sp.name")
    List<ScoreRecord> listLatestValidByClassName(@Param("className") String className);

    /**
     * 按 {@code scores.id} 取一行原始成绩（**含**待录入行、含 {@code course_id}）。
     * 供编辑 / 删除定位与归属判定用 —— 必须能拿到 {@code course_id} 才能按课程归属把关。
     */
    @Select("SELECT s.id, s.student_id AS studentId, s.course_id AS courseId, s.semester, s.year, "
          + "       s.regular_score AS regularScore, s.final_score AS finalScore, "
          + "       s.score_total AS scoreTotal, s.grade, s.grade_point AS gradePoint, "
          + "       s.remark, s.reason_for_change AS reasonForChange, s.modified_by AS modifiedBy "
          + "  FROM scores s WHERE s.id = #{id}")
    ScoreRecord selectScoreById(@Param("id") Long id);

    /** 某门课的授课教师（{@code courses.teacher_id} = {@code users.id}）。查不到返回 null。 */
    @Select("SELECT teacher_id FROM courses WHERE id = #{courseId}")
    Long selectCourseTeacherId(@Param("courseId") Long courseId);

    /**
     * 某班级全部学生的权威成绩行（每生每课程一行、最新一次有效成绩），
     * 键名保持控制器消费的下划线形态（{@code student_id}/{@code course_name}/...）。
     *
     * <p>W36：来源由 {@code score_record}（按 {@code class_name} 字符串）改为 {@code scores}，
     * 班级归属改经 {@code student_profile.class_id} —— 后者是与预警、辅导员同源的唯一真源。
     */
    @Select("SELECT v.student_id AS student_id, sp.name AS student_name, "
          + "       v.course_id AS course_id, c.name AS course_name, "
          + "       c.credits AS credits, c.type AS type, "
          + "       v.score_total AS score_total, " + ScoreCaliber.SCORE_LEVEL_CASE + " AS score_level "
          + "  FROM " + ScoreCaliber.LATEST_VALID_SCORE + " v "
          + "  JOIN student_profile sp ON sp.id = v.student_id "
          + "  LEFT JOIN courses c ON c.id = v.course_id "
          + " WHERE sp.class_id = #{classId} ORDER BY sp.name, c.name")
    List<Map<String, Object>> getScoresByClassId(@Param("classId") Long classId);

    /**
     * 取学号，用于推导入学年（{@code 学年 = 学号前四位 + (学期-1) DIV 2}）。
     *
     * <p>注意 {@code student_profile.student_id} 是**学号字符串**，不是主键 —— 主键是 {@code id}，
     * 这里按主键查。传入 {@code users.id} 会静默查不到。
     */
    @Select("SELECT student_id FROM student_profile WHERE id = #{studentProfileId}")
    String selectStudentNo(@Param("studentProfileId") Long studentProfileId);

    /** 取姓名（按 {@code student_profile.id}）。查不到返回 null，不编造。 */
    @Select("SELECT name FROM student_profile WHERE id = #{studentProfileId}")
    String selectStudentName(@Param("studentProfileId") Long studentProfileId);

    /**
     * 单名学生的学分/挂科汇总。
     *
     * <p>直接复用 {@link ScoreCaliber#ONE_STUDENT_STATS} —— 学生端的"已修学分"也用这一段，
     * 两边共用一份口径。列（camelCase）：totalCourses / failedCourses / completedCredits / gpa / gradedCredits。
     *
     * <p>参数是 {@code student_profile.id}（= {@code scores.student_id}），**不是** {@code users.id}：
     * 传 {@code users.id} 会静默返回一行全 0，不报错。
     */
    @Select(ScoreCaliber.ONE_STUDENT_STATS)
    Map<String, Object> selectStudentStats(@Param("studentId") Long studentId);

    /**
     * 单名学生的挂科门数（每门课取期末已录入的最新一次、按课程去重，与预警引擎同一口径）。
     * 参数是 {@code student_profile.id}。
     */
    @Select(ScoreCaliber.FAIL_COUNT_BY_ONE_STUDENT)
    Integer selectFailedCourseCount(@Param("studentId") Long studentId);
}
