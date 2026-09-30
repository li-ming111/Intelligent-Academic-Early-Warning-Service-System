package com.academic.mapper;

import com.academic.common.constant.ScoreCaliber;
import com.academic.common.entity.ScoreRecord;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

public interface ScoreRecordMapper {
    @Select("SELECT * FROM score_record WHERE id = #{id}")
    ScoreRecord selectById(Long id);

    @Select("SELECT * FROM score_record WHERE course_id = #{courseId} ORDER BY student_name")
    List<ScoreRecord> getScoresByCourseId(Long courseId);

    @Select("SELECT * FROM score_record WHERE course_id = #{courseId} AND teacher_id = #{teacherId} ORDER BY student_name")
    List<ScoreRecord> getScoresByCourseIdAndTeacherId(Long courseId, Long teacherId);
    
    @Select("SELECT * FROM score_record WHERE student_id = #{studentId}")
    List<ScoreRecord> getScoresByStudentId(String studentId);
    
    @Insert("INSERT INTO score_record(course_id, student_id, teacher_id, student_name, class_name, "
          + "score, score_total, score_level, grade, grade_point, remark, semester, year) "
          + "VALUES(#{courseId}, #{studentId}, #{teacherId}, #{studentName}, #{className}, "
          + "#{score}, #{scoreTotal}, #{scoreLevel}, #{grade}, #{gradePoint}, #{remark}, #{semester}, #{year})")
    int insertScore(ScoreRecord score);

    /**
     * 只更新调用方明确提供的字段。
     *
     * <p>score / score_level / remark 是每次保存都会给的（录入界面的三个输入项）。
     * 其余字段一律用 {@code <if>} 守空 —— 早期实现写成 {@code SET regular_score = #{regularScore}}
     * 这种无条件赋值，而 {@code updateScore(Long, Double, String, String)} 那条路径
     * 只构造了一个只含分数和评语的裸对象，于是改一次分就会把该行的
     * regular_score / final_score **置空**；final_score 一空，这一行就会被
     * "期末未录入"的口径整体排除，学生的挂科会从预警里静默消失。
     */
    @Update("<script>UPDATE score_record <set>"
          + "score = #{score}, score_level = #{scoreLevel}, remark = #{remark}"
          + "<if test='scoreTotal != null'>, score_total = #{scoreTotal}</if>"
          + "<if test='grade != null'>, grade = #{grade}</if>"
          + "<if test='gradePoint != null'>, grade_point = #{gradePoint}</if>"
          + "<if test='regularScore != null'>, regular_score = #{regularScore}</if>"
          + "<if test='finalScore != null'>, final_score = #{finalScore}</if>"
          + "<if test='semester != null'>, semester = #{semester}</if>"
          + "<if test='year != null'>, year = #{year}</if>"
          + "</set> WHERE id = #{id}</script>")
    int updateScore(ScoreRecord score);
    
    @Delete("DELETE FROM score_record WHERE id = #{id}")
    int deleteScore(Long id);
    
    @Delete("DELETE FROM score_record WHERE id = #{id}")
    int deleteById(Long id);
    
    @Delete("<script>DELETE FROM score_record WHERE id IN <foreach item='id' collection='ids' open='(' separator=',' close=')'>#{id}</foreach></script>")
    int deleteByIds(List<Long> ids);
    
    @Select("SELECT sr.*, c.name as course_name, sp.student_id as student_no FROM score_record sr LEFT JOIN courses c ON sr.course_id = c.id LEFT JOIN student_profile sp ON sr.student_id = sp.id WHERE sr.class_name = #{className}")
    List<ScoreRecord> getScoresByClassName(String className);

    @Select("SELECT sr.*, c.name as course_name, sp.student_id as student_no FROM score_record sr LEFT JOIN courses c ON sr.course_id = c.id LEFT JOIN student_profile sp ON sr.student_id = sp.id WHERE sr.class_name = #{className} AND sr.teacher_id = #{teacherId}")
    List<ScoreRecord> getScoresByClassNameAndTeacherId(String className, Long teacherId);
    
    @Select("SELECT * FROM score_record WHERE teacher_id = #{teacherId} ORDER BY id DESC")
    List<ScoreRecord> getScoresByTeacherId(Long teacherId);

    /**
     * 该生该门课已有的最近一条录入记录，用于继承学期。
     *
     * <p>{@code LIMIT 1} 与排序都不能省：实测 238 个 (student_id, course_id) 组里有 144 组
     * 有多于一行，方法返回单个对象时 MyBatis 走的是 {@code selectOne}，
     * 多行会直接抛 {@code TooManyResultsException} —— 也就是 60% 的组一保存成绩就报错。
     *
     * <p>排序先按"学期已知"优先（{@code semester IS NULL} 为 0 排在前），
     * 再按学期数值倒序。学期列是 varchar，直接 {@code ORDER BY semester DESC} 是字典序
     * （"10" &lt; "2"），必须 CAST 成数值。
     */
    @Select("SELECT * FROM score_record WHERE student_id = #{studentId} AND course_id = #{courseId} "
          + "ORDER BY (semester IS NULL), CAST(semester AS UNSIGNED) DESC, id DESC LIMIT 1")
    ScoreRecord selectLatestByStudentAndCourse(@Param("studentId") Long studentId,
                                               @Param("courseId") Long courseId);

    /**
     * 按 (student_id, course_id, semester) 三元组查重 —— 重修路径用。
     *
     * <p>调用方显式指定了学期时走这条：重修是同一个学生同一门课的**另一个学期**，
     * 应该新增一行而不是覆盖旧学期成绩；不带学期查重会让重修覆盖历史成绩，
     * "按最新一次成绩判定挂科"的口径就退化成"只有一次成绩"，重修抵扣无从谈起。
     *
     * <p>{@code LIMIT 1} 是防御性的：历史数据里同一三元组可能已有多行，
     * 不能让一次保存因为脏数据而抛异常。
     */
    @Select("SELECT * FROM score_record WHERE student_id = #{studentId} AND course_id = #{courseId} "
          + "AND semester = #{semester} ORDER BY id DESC LIMIT 1")
    ScoreRecord selectByStudentCourseSemester(@Param("studentId") Long studentId,
                                              @Param("courseId") Long courseId,
                                              @Param("semester") String semester);

    @Select("SELECT sr.student_id, sr.student_name, sr.course_id, c.name AS course_name, c.credits, c.type, sr.score AS score_total, sr.score_level FROM score_record sr LEFT JOIN courses c ON sr.course_id = c.id WHERE sr.class_name = (SELECT name FROM classes WHERE id = #{classId})")
    List<Map<String, Object>> getScoresByClassId(Long classId);

    /**
     * 取学号，用于推导入学年（{@code 学年 = 学号前四位 + (学期-1) DIV 2}）。
     *
     * <p>注意 {@code student_profile.student_id} 是**学号字符串**，
     * 不是主键 —— 主键是 {@code id}，这里按主键查。传入 users.id 会静默查不到。
     */
    @Select("SELECT student_id FROM student_profile WHERE id = #{studentProfileId}")
    String selectStudentNo(@Param("studentProfileId") Long studentProfileId);

    /** 取姓名（按 student_profile.id）。查不到返回 null，不编造。 */
    @Select("SELECT name FROM student_profile WHERE id = #{studentProfileId}")
    String selectStudentName(@Param("studentProfileId") Long studentProfileId);

    /**
     * 单名学生的学分/挂科汇总。
     *
     * <p>直接复用 {@link com.academic.common.constant.ScoreCaliber#ONE_STUDENT_STATS} ——
     * 学生端的"已修学分"也用这一段，两边共用一份口径，不可能各算各的。
     * 列（camelCase）：totalCourses / failedCourses / completedCredits / gpa / gradedCredits。
     *
     * <p>参数是 {@code student_profile.id}（= {@code scores.student_id}），
     * <b>不是</b> {@code users.id}：传 users.id 会静默返回一行全 0，不报错。
     */
    @Select(ScoreCaliber.ONE_STUDENT_STATS)
    Map<String, Object> selectStudentStats(@Param("studentId") Long studentId);

    /**
     * 单名学生的挂科门数（每门课取期末已录入的最新一次、按课程去重，
     * 与预警引擎同一口径）。参数是 {@code student_profile.id}。
     */
    @Select(ScoreCaliber.FAIL_COUNT_BY_ONE_STUDENT)
    Integer selectFailedCourseCount(@Param("studentId") Long studentId);
}