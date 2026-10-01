package com.academic.mapper;

import com.academic.common.constant.ScoreCaliber;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 学院管理员端的成绩聚合统计。
 *
 * <p>此前 {@code CollegeAdminController} 的 11 处统计都是同一种写法：
 * 把 {@code score_record} <b>整表</b>查回来，在 Java 里逐行
 * {@code studentProfileMapper.selectById(...)} 判断班级，然后用手写的
 * {@code total / count / 20.0} 当 GPA。三个后果：
 * <ol>
 *   <li>{@code 总分/门数/20} 不是 GPA。绩点是按"课程绩点 × 学分"加权的，
 *       而且每门课的成绩只应按<b>最新一次</b>算，不能被重修记录重复计入。</li>
 *   <li>N+1 查询。{@code classComparison} 更严重：整表查询写在 for 循环<b>内部</b>，
 *       班级数 × 成绩表行数次全表扫描。</li>
 *   <li>数据源是 {@code score_record}（教师录入暂存表），与预警引擎、学生端读的
 *       {@code scores} 已经分叉 —— 同一名学生在两端看到的挂科数不同。</li>
 * </ol>
 *
 * <p>这里把统计下沉成 SQL 聚合，统一走 {@link ScoreCaliber#LATEST_VALID_SCORE}
 * （每门课取最新一次且期末已录入的成绩），控制器只做字段映射。
 *
 * <p><b>输出字段名保持不变</b>：{@code CollegeAdmin*.vue} 按 {@code totalScore} /
 * {@code finalScore} / {@code regularScore} 等取值，改数据源不应带着改前端。
 *
 * <p>学院范围一律用 {@code sp.class_id IN (SELECT id FROM classes WHERE college_id = ?)}
 * 而不是 {@code sp.college_id}：与控制器原有语义一致 —— 没有班级归属的学生不计入学院统计。
 */
@Mapper
public interface ScoreStatMapper {

    /** 学院范围子查询，供各方法拼接。 */
    String COLLEGE_CLASSES = "(SELECT cl.id FROM classes cl WHERE cl.college_id = #{collegeId})";

    /**
     * 每个班级的汇总：学生数、有成绩的学生数、平均 GPA、预警数。
     * 列：{@code class_id} / {@code student_count} / {@code graded_student_count} /
     * {@code avg_gpa} / {@code warning_count}。
     *
     * <p>{@code avg_gpa} 是对"每名学生的学分加权 GPA"再取平均，不是把全班成绩混在一起算。
     * {@code graded_student_count} 只数有成绩的学生，无成绩的新生不拉低班级均分。
     */
    @Select("SELECT cl.id AS class_id, "
          + "       COUNT(DISTINCT sp.id) AS student_count, "
          + "       COUNT(DISTINCT g.student_id) AS graded_student_count, "
          + "       AVG(g.gpa) AS avg_gpa, "
          + "       (SELECT COUNT(*) FROM academic_warning aw JOIN student_profile sp3 ON sp3.id = aw.student_id "
          + "         WHERE sp3.class_id = cl.id) AS warning_count "
          + "  FROM classes cl "
          + "  LEFT JOIN student_profile sp ON sp.class_id = cl.id "
          + "  LEFT JOIN " + ScoreCaliber.STUDENT_GPA + " g ON g.student_id = sp.id AND g.gpa IS NOT NULL "
          + " WHERE cl.college_id = #{collegeId} "
          + " GROUP BY cl.id")
    List<Map<String, Object>> selectClassStats(@Param("collegeId") Long collegeId);

    /**
     * 学院内每名学生的成绩汇总。列：{@code profile_id} / {@code total_courses} /
     * {@code passed_courses} / {@code failed_courses} / {@code gpa} / {@code warning_count}。
     *
     * <p>一次查出全院，供学生列表与班级学生列表在内存里按 profile_id 取用，
     * 取代"每个学生查一次成绩表"。没有成绩记录的学生不会出现在结果里，
     * 调用方取不到时按"暂无成绩"处理（GPA 记 null 而非 0）。
     */
    @Select("SELECT v.student_id AS profile_id, "
          + "       COUNT(*) AS total_courses, "
          + "       SUM(CASE WHEN v.score_total >= " + ScoreCaliber.PASS_LINE
          + "                THEN 1 ELSE 0 END) AS passed_courses, "
          + "       SUM(CASE WHEN v.score_total < " + ScoreCaliber.PASS_LINE
          + "                THEN 1 ELSE 0 END) AS failed_courses, "
          + "       " + ScoreCaliber.GPA_EXPR + " AS gpa, "
          + "       (SELECT COUNT(*) FROM academic_warning aw WHERE aw.student_id = v.student_id) AS warning_count "
          + "  FROM " + ScoreCaliber.LATEST_VALID_SCORE + " v "
          + "  LEFT JOIN courses c ON c.id = v.course_id "
          + "  JOIN student_profile sp ON sp.id = v.student_id "
          + " WHERE sp.class_id IN " + COLLEGE_CLASSES + " "
          + " GROUP BY v.student_id")
    List<Map<String, Object>> selectStudentScoreStats(@Param("collegeId") Long collegeId);

    /** 单名学生的成绩汇总，字段同 {@link #selectStudentScoreStats}。 */
    @Select("SELECT v.student_id AS profile_id, "
          + "       COUNT(*) AS total_courses, "
          + "       SUM(CASE WHEN v.score_total >= " + ScoreCaliber.PASS_LINE
          + "                THEN 1 ELSE 0 END) AS passed_courses, "
          + "       SUM(CASE WHEN v.score_total < " + ScoreCaliber.PASS_LINE
          + "                THEN 1 ELSE 0 END) AS failed_courses, "
          + "       " + ScoreCaliber.GPA_EXPR + " AS gpa "
          + "  FROM " + ScoreCaliber.LATEST_VALID_SCORE + " v "
          + "  LEFT JOIN courses c ON c.id = v.course_id "
          + " WHERE v.student_id = #{profileId}")
    Map<String, Object> selectOneStudentScoreStats(@Param("profileId") Long profileId);

    /**
     * 某学生的成绩明细（每门课最新一次有效成绩），带课程名。
     * 列：{@code id} / {@code course_id} / {@code course_name} / {@code regular_score} /
     * {@code final_score} / {@code score_total} / {@code grade_point} / {@code grade} /
     * {@code semester} / {@code credits}。
     *
     * <p>课程名由 JOIN 取得，取代原实现"每行再查一次 courses 表"。
     * {@code finalScore} 为 NULL 表示期末未录入，前端显示"待录入"。
     */
    @Select("SELECT v.id AS id, v.course_id AS course_id, c.name AS course_name, "
          + "       v.regular_score AS regular_score, v.final_score AS final_score, "
          + "       v.score_total AS score_total, v.grade_point AS grade_point, "
          + "       v.grade AS grade, v.semester AS semester, c.credits AS credits "
          + "  FROM " + ScoreCaliber.LATEST_VALID_SCORE + " v "
          + "  LEFT JOIN courses c ON c.id = v.course_id "
          + " WHERE v.student_id = #{profileId} "
          + " ORDER BY v.semester DESC, v.course_id")
    List<Map<String, Object>> selectStudentScoreRows(@Param("profileId") Long profileId);

    /**
     * 全院成绩总览。列：{@code record_count} / {@code student_count} / {@code avg_score} /
     * {@code pass_count}。分母是"课程-学生"条目数（已按最新成绩去重）。
     */
    @Select("SELECT COUNT(*) AS record_count, COUNT(DISTINCT v.student_id) AS student_count, "
          + "       AVG(v.score_total) AS avg_score, "
          + "       SUM(CASE WHEN v.score_total >= " + ScoreCaliber.PASS_LINE
          + "                THEN 1 ELSE 0 END) AS pass_count "
          + "  FROM " + ScoreCaliber.LATEST_VALID_SCORE + " v "
          + "  JOIN student_profile sp ON sp.id = v.student_id "
          + " WHERE sp.class_id IN " + COLLEGE_CLASSES)
    Map<String, Object> selectCollegeScoreOverview(@Param("collegeId") Long collegeId);

    /**
     * 全院成绩分布（五档计数）。列：{@code band1} 90-100 / {@code band2} 80-89 /
     * {@code band3} 70-79 / {@code band4} 60-69 / {@code band5} 0-59。
     *
     * <p>分档在 SQL 里算，避免把整表成绩拉回 Java 再逐行 {@code if}。
     */
    @Select("SELECT "
          + "  SUM(CASE WHEN v.score_total >= 90 THEN 1 ELSE 0 END) AS band1, "
          + "  SUM(CASE WHEN v.score_total >= 80 AND v.score_total < 90 THEN 1 ELSE 0 END) AS band2, "
          + "  SUM(CASE WHEN v.score_total >= 70 AND v.score_total < 80 THEN 1 ELSE 0 END) AS band3, "
          + "  SUM(CASE WHEN v.score_total >= 60 AND v.score_total < 70 THEN 1 ELSE 0 END) AS band4, "
          + "  SUM(CASE WHEN v.score_total < 60 THEN 1 ELSE 0 END) AS band5 "
          + "  FROM " + ScoreCaliber.LATEST_VALID_SCORE + " v "
          + "  JOIN student_profile sp ON sp.id = v.student_id "
          + " WHERE sp.class_id IN " + COLLEGE_CLASSES)
    Map<String, Object> selectScoreDistribution(@Param("collegeId") Long collegeId);

    /**
     * 按课程汇总（平均分、及格率、选课人数）。列：{@code course_id} / {@code course_name} /
     * {@code record_count} / {@code avg_score} / {@code pass_count}。
     */
    @Select("SELECT v.course_id AS course_id, c.name AS course_name, "
          + "       COUNT(*) AS record_count, AVG(v.score_total) AS avg_score, "
          + "       SUM(CASE WHEN v.score_total >= " + ScoreCaliber.PASS_LINE
          + "                THEN 1 ELSE 0 END) AS pass_count "
          + "  FROM " + ScoreCaliber.LATEST_VALID_SCORE + " v "
          + "  LEFT JOIN courses c ON c.id = v.course_id "
          + "  JOIN student_profile sp ON sp.id = v.student_id "
          + " WHERE sp.class_id IN " + COLLEGE_CLASSES + " "
          + " GROUP BY v.course_id, c.name "
          + " ORDER BY avg_score DESC")
    List<Map<String, Object>> selectCourseStats(@Param("collegeId") Long collegeId);

    /**
     * 按课程汇总（**全库**，不限学院）。列名与 {@link #selectCourseStats} 逐字相同。
     *
     * <p>2026-10-01 第二十四轮新增：管理员端的「成绩分析报表」（{@code AdminController}
     * 的报表模板 2）是全校口径，没有 {@code collegeId} 可用。它与上面那个方法的差别
     * <b>只有</b>一层学院过滤 —— 学院版要把学生限定在
     * {@code sp.class_id IN (本学院班级)}，所以必须 JOIN {@code student_profile}；
     * 全校版不需要，因此这里连 JOIN 都省掉。
     *
     * <p>口径一律走 {@link ScoreCaliber#LATEST_VALID_SCORE}（每门课每名学生取最新一次
     * 且期末已录入的成绩）：这套报表此前整块是写死的假数据（张三/李四/王五、
     * 高等数学 50 人 42 及格），替换时必须和预警引擎、学院端统计读同一个源，
     * 不能再手写一套及格率算法。
     */
    @Select("SELECT v.course_id AS course_id, c.name AS course_name, "
          + "       COUNT(*) AS record_count, AVG(v.score_total) AS avg_score, "
          + "       SUM(CASE WHEN v.score_total >= " + ScoreCaliber.PASS_LINE
          + "                THEN 1 ELSE 0 END) AS pass_count "
          + "  FROM " + ScoreCaliber.LATEST_VALID_SCORE + " v "
          + "  LEFT JOIN courses c ON c.id = v.course_id "
          + " GROUP BY v.course_id, c.name "
          + " ORDER BY avg_score DESC")
    List<Map<String, Object>> selectCourseStatsAll();

    /**
     * 班级成绩对比。列：{@code class_id} / {@code class_name} / {@code student_count} /
     * {@code record_count} / {@code avg_score} / {@code pass_count}。
     *
     * <p>一次聚合出全部班级。原实现在 for 循环内做整表查询，是本次改动中最重的一处。
     *
     * <p>{@code record_count} 统计的是 {@code v.student_id} 而非 {@code *}：班级没有学生时
     * {@code LEFT JOIN} 会留下一行全 NULL，{@code COUNT(*)} 会把它算成 1 条成绩记录。
     * 同理 {@code avg_score} 此时为 NULL，调用方按"暂无成绩"处理而不是显示 0 分。
     */
    @Select("SELECT cl.id AS class_id, cl.name AS class_name, "
          + "       COUNT(DISTINCT v.student_id) AS student_count, "
          + "       COUNT(v.student_id) AS record_count, AVG(v.score_total) AS avg_score, "
          + "       SUM(CASE WHEN v.score_total >= " + ScoreCaliber.PASS_LINE
          + "                THEN 1 ELSE 0 END) AS pass_count "
          + "  FROM classes cl "
          + "  LEFT JOIN student_profile sp ON sp.class_id = cl.id "
          + "  LEFT JOIN " + ScoreCaliber.LATEST_VALID_SCORE + " v ON v.student_id = sp.id "
          + " WHERE cl.college_id = #{collegeId} "
          + " GROUP BY cl.id, cl.name "
          + " ORDER BY cl.id")
    List<Map<String, Object>> selectClassComparison(@Param("collegeId") Long collegeId);

    /**
     * 按任课教师汇总。列：{@code teacher_id}（{@code users.id}）/ {@code course_count} /
     * {@code course_names} / {@code student_count} / {@code avg_score}。
     *
     * <p>归属取自 {@code courses.teacher_id} —— {@code scores} 表没有 teacher_id 列，
     * 而课程与教师的对应关系本来就记在课程上。原实现用 {@code score_record.teacher_id}
     * 反查，同一门课换老师后历史记录会记到新老师名下。
     */
    @Select("SELECT c.teacher_id AS teacher_id, "
          + "       COUNT(DISTINCT c.id) AS course_count, "
          + "       GROUP_CONCAT(DISTINCT c.name ORDER BY c.name SEPARATOR '、') AS course_names, "
          + "       COUNT(DISTINCT v.student_id) AS student_count, "
          + "       AVG(v.score_total) AS avg_score "
          + "  FROM " + ScoreCaliber.LATEST_VALID_SCORE + " v "
          + "  JOIN courses c ON c.id = v.course_id "
          + "  JOIN student_profile sp ON sp.id = v.student_id "
          + " WHERE sp.class_id IN " + COLLEGE_CLASSES + " "
          + "   AND c.teacher_id IS NOT NULL "
          + " GROUP BY c.teacher_id")
    List<Map<String, Object>> selectTeacherStats(@Param("collegeId") Long collegeId);

    /**
     * 某教师的授课成绩明细。列：{@code student_name} / {@code class_name} /
     * {@code course_id} / {@code course_name} / {@code regular_score} /
     * {@code final_score} / {@code score_total} / {@code semester}。
     */
    @Select("SELECT sp.name AS student_name, sp.class_name AS class_name, "
          + "       v.course_id AS course_id, c.name AS course_name, "
          + "       v.regular_score AS regular_score, v.final_score AS final_score, "
          + "       v.score_total AS score_total, v.semester AS semester "
          + "  FROM " + ScoreCaliber.LATEST_VALID_SCORE + " v "
          + "  JOIN courses c ON c.id = v.course_id "
          + "  JOIN student_profile sp ON sp.id = v.student_id "
          + " WHERE sp.class_id IN " + COLLEGE_CLASSES + " "
          + "   AND c.teacher_id = #{teacherUserId} "
          + " ORDER BY sp.name, c.name")
    List<Map<String, Object>> selectTeacherScoreRows(@Param("collegeId") Long collegeId,
                                                     @Param("teacherUserId") Long teacherUserId);

    /**
     * 成绩查询／导出：全院成绩明细，可选按班级与关键字（学生姓名或学号）过滤。
     *
     * <p>{@code classId} 与 {@code keyword} 均可为 null，用 {@code <script>} 动态拼接。
     * 过滤条件下推到 SQL，不再把全院成绩拉进内存再筛。
     */
    @Select("<script>"
          + "SELECT sp.name AS student_name, sp.student_id AS student_no, sp.class_name AS class_name, "
          + "       v.course_id AS course_id, c.name AS course_name, "
          + "       v.regular_score AS regular_score, v.final_score AS final_score, "
          + "       v.score_total AS score_total, v.grade_point AS grade_point, "
          + "       v.grade AS grade, v.semester AS semester, v.id AS id "
          + "  FROM " + ScoreCaliber.LATEST_VALID_SCORE + " v "
          + "  LEFT JOIN courses c ON c.id = v.course_id "
          + "  JOIN student_profile sp ON sp.id = v.student_id "
          + " WHERE sp.class_id IN (SELECT cl.id FROM classes cl WHERE cl.college_id = #{collegeId}) "
          + "<if test='classId != null'> AND sp.class_id = #{classId} </if>"
          + "<if test='keyword != null and keyword != \"\"'>"
          + "   AND (sp.name LIKE CONCAT('%', #{keyword}, '%') "
          + "        OR sp.student_id LIKE CONCAT('%', #{keyword}, '%')) "
          + "</if>"
          + " ORDER BY sp.name, c.name, v.semester"
          + "</script>")
    List<Map<String, Object>> selectScoreRows(@Param("collegeId") Long collegeId,
                                              @Param("classId") Long classId,
                                              @Param("keyword") String keyword);
}
