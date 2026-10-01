package com.academic.mapper;

import com.academic.common.constant.ScoreCaliber;
import com.academic.common.entity.Score;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 辅导员端成绩查询。
 *
 * <p>数据源是 {@code scores}（权威表），经 {@link ScoreCaliber#LATEST_VALID_SCORE} 取
 * "每门课最新一次且期末已录入"的成绩。改这张表之前这里读的是 {@code score_record}，
 * 于是辅导员看到的低分名单与预警引擎的名单来自两张已经分叉的表 ——
 * 实测除司明外另外 3 名真实挂科学生在 {@code score_record} 里挂科数是 0，
 * 辅导员端会漏掉 75% 的高危学生。
 *
 * <p>输出列名（{@code student_name}/{@code course_name}/{@code class_name}/{@code score_total}
 * 等）刻意与原实现保持一致，前端 {@code CounselorScores.vue} 按这些字段名取值，
 * 不随数据源切换而改动。
 *
 * <p>班级关联已从"按 {@code class_name} 字符串比对"改为按 {@code student_profile.class_id} 外键。
 * 字符串比对在班级改名或存在重名时会静默失配。
 */
@Mapper
public interface ScoreMapper extends BaseMapper<Score> {

    /**
     * 成绩行的统一列清单，别名与前端约定一致。
     *
     * <p>2026-10-01 第二十一轮补 {@code sp.student_id AS student_no}：辅导员「成绩管理」页的
     * 「学号」列读的是 {@code student_no}，而这里原来只给了 {@code student_name}
     * ⇒ 该列每行空白（同表其余 5 列正常）。注意 {@code student_profile} 有**两个** id 概念：
     * 表主键 {@code id}（成绩表的外键）与学号 {@code student_id}，这里要的是后者。
     */
    String SCORE_COLUMNS =
            "v.id AS id, v.student_id AS student_id, v.course_id AS course_id, "
          + "v.score_total AS score_total, v.semester AS semester, "
          + "v.regular_score AS regular_score, v.final_score AS final_score, "
          + "v.grade AS grade, v.grade_point AS grade_point, v.created_at AS created_at, "
          + ScoreCaliber.SCORE_LEVEL_CASE + " AS score_level, "
          + "sp.name AS student_name, sp.student_id AS student_no, sp.class_name AS class_name, "
          + "c.name AS course_name, c.credits AS credits, c.type AS type";

    /** 科目表 + 学生档案的统一 JOIN，各查询复用。 */
    String SCORE_JOINS =
            "  FROM " + ScoreCaliber.LATEST_VALID_SCORE + " v "
          + "  JOIN student_profile sp ON sp.id = v.student_id "
          + "  LEFT JOIN courses c ON c.id = v.course_id ";

    /** 辅导员所带班级的学生 id 集合，多处复用。 */
    String COUNSELOR_STUDENTS =
            "(SELECT sp2.id FROM student_profile sp2 "
          + "  WHERE sp2.class_id IN (SELECT cl.id FROM classes cl WHERE cl.counselor_id = #{counselorId}))";

    /** 该辅导员名下全部学生的成绩。 */
    @Select("SELECT " + SCORE_COLUMNS + SCORE_JOINS
          + " WHERE v.student_id IN " + COUNSELOR_STUDENTS
          + " ORDER BY v.semester DESC, v.student_id")
    List<Map<String, Object>> getScoresByCounselorId(@Param("counselorId") Long counselorId);

    /**
     * 某个班级的成绩。
     *
     * <p>原实现是 {@code sr.class_name = (SELECT name FROM classes WHERE id = #{classId})} ——
     * 按班级名字符串匹配。班级一旦改名，辅导员端立刻查不到任何成绩；同名班级还会串数据。
     * 改为直接按 {@code student_profile.class_id} 外键关联。
     */
    @Select("SELECT " + SCORE_COLUMNS + SCORE_JOINS
          + " WHERE sp.class_id = #{classId}"
          + " ORDER BY v.semester DESC, v.student_id")
    List<Map<String, Object>> getScoresByClassId(@Param("classId") Long classId);

    /** 某门课在本辅导员学生中的成绩（用于成绩分布）。 */
    @Select("SELECT " + SCORE_COLUMNS + SCORE_JOINS
          + " WHERE v.course_id = #{courseId}"
          + "   AND v.student_id IN " + COUNSELOR_STUDENTS
          + " ORDER BY v.score_total DESC")
    List<Map<String, Object>> getScoresByCourseIdAndCounselor(@Param("courseId") Long courseId,
                                                              @Param("counselorId") Long counselorId);

    /**
     * 某名学生的成绩轨迹。
     *
     * <p>这一条<b>不</b>走"每门课取最新一次"：趋势图需要时间维度，折叠成一人一行后就画不出曲线了。
     * 因此保留每个学期的原始行，仅排除 {@code final_score IS NULL} 的"期末未录入"行 ——
     * 那些行的 {@code score_total} 是平时分折算值，画进趋势图会被误读成一次暴跌。
     *
     * <p>参数 {@code studentId} 是 {@code student_profile.id}，不是 {@code users.id}，也不是学号。
     */
    @Select("SELECT v.id AS id, v.student_id AS student_id, v.course_id AS course_id, "
          + "v.score_total AS score_total, v.semester AS semester, "
          + "v.regular_score AS regular_score, v.final_score AS final_score, "
          + "v.grade AS grade, v.grade_point AS grade_point, v.created_at AS created_at, "
          + ScoreCaliber.SCORE_LEVEL_CASE + " AS score_level, "
          + "sp.name AS student_name, sp.class_name AS class_name, "
          + "c.name AS course_name, c.credits AS credits, c.type AS type "
          + "FROM scores v "
          + "JOIN student_profile sp ON sp.id = v.student_id "
          + "LEFT JOIN courses c ON c.id = v.course_id "
          + "WHERE v.student_id = #{studentId} AND v.final_score IS NOT NULL "
          + "ORDER BY v.semester ASC, v.course_id ASC")
    List<Map<String, Object>> getScoresByStudentId(@Param("studentId") Long studentId);

    /**
     * 按班级汇总，供"学分监控""学分不足率""达标率排名"使用。
     *
     * <p>原实现只返回 {@code score_count}（成绩<b>行数</b>）与 {@code avg_score}，
     * 而控制器把行数当成"班级人数"累加，"总人数"实际是成绩条数；"达标率"则直接
     * 用平均分冒充百分比（{@code Math.min(100, avgScore)}），一个班平均 85 分就报
     * "达标率 85%"，两者根本不是一回事。这里把每个指标单独算清楚，上层不必再猜。
     *
     * <p>"学分达标"的定义：该生所有已录入成绩的课程均已通过，即已修学分等于应修学分。
     * 分母是<b>有成绩的</b>学生数而非花名册人数 —— 完全没有成绩记录的新生不应被算作"达标"，
     * 否则班级达标率会被稀释成虚高。花名册人数单独用 {@code student_count} 给出。
     *
     * <p>一名学生只属于一个班级，所以对同一辅导员的各班求和即为去重后的总人数。
     */
    @Select("SELECT c.id AS class_id, c.name AS name, "
          + "  (SELECT COUNT(*) FROM student_profile sp2 WHERE sp2.class_id = c.id) AS student_count, "
          + "  COUNT(DISTINCT v.student_id) AS graded_student_count, "
          + "  COUNT(DISTINCT CASE WHEN v.score_total < " + ScoreCaliber.PASS_LINE
          + "                      THEN v.student_id END) AS fail_student_count, "
          + "  COUNT(DISTINCT v.student_id) "
          + "    - COUNT(DISTINCT CASE WHEN v.score_total < " + ScoreCaliber.PASS_LINE
          + "                          THEN v.student_id END) AS credit_achievement_count, "
          + "  ROUND(100.0 * (COUNT(DISTINCT v.student_id) "
          + "    - COUNT(DISTINCT CASE WHEN v.score_total < " + ScoreCaliber.PASS_LINE
          + "                          THEN v.student_id END)) "
          + "    / NULLIF(COUNT(DISTINCT v.student_id), 0), 1) AS achievement_rate, "
          + "  COUNT(v.course_id) AS score_count, "
          + "  ROUND(AVG(v.score_total), 2) AS avg_score "
          + "FROM classes c "
          + "LEFT JOIN student_profile sp ON sp.class_id = c.id "
          + "LEFT JOIN " + ScoreCaliber.LATEST_VALID_SCORE + " v ON v.student_id = sp.id "
          + "WHERE c.counselor_id = #{counselorId} "
          + "GROUP BY c.id, c.name "
          + "ORDER BY achievement_rate DESC, c.id")
    List<Map<String, Object>> getClassScoreStatsByCounselorId(@Param("counselorId") Long counselorId);

    /**
     * 辅导员名下低于关注线（但可能尚未挂科）的成绩，用于"低分关注"名单。
     *
     * <p>用 SQL 过滤而非取回全量再在 Java 里筛：原实现把该辅导员名下所有成绩
     * 都捞进内存再 {@code stream().filter()}，一个辅导员几个学期的成绩数以万计，
     * 而结果往往只有几十条。
     */
    @Select("SELECT " + SCORE_COLUMNS + SCORE_JOINS
          + " WHERE v.student_id IN " + COUNSELOR_STUDENTS
          + "   AND v.score_total < #{threshold} "
          + " ORDER BY v.score_total ASC, v.student_id")
    List<Map<String, Object>> getLowScoresByCounselorId(@Param("counselorId") Long counselorId,
                                                        @Param("threshold") int threshold);

    /**
     * 辅导员名下"学分不足"（有挂科，已修学分少于应修学分）的学生。
     *
     * <p>{@code earned_credits} 只累加通过的课程学分，{@code attempted_credits} 累加全部
     * 已修课程学分，两者之差就是因挂科而未拿到的学分。没有任何成绩记录的学生不会出现在
     * 结果里（{@code fail_count} 为 0 被 HAVING 过滤掉）—— 他们不是"学分不足"，
     * 只是还没开始修。原实现把班里每个人都当成学分不足返回，等于没有筛选。
     */
    @Select("SELECT sp.id AS profile_id, sp.student_id AS student_no, sp.name AS student_name, "
          + "  sp.class_name AS class_name, "
          + "  COALESCE(SUM(c.credits), 0) AS attempted_credits, "
          + "  COALESCE(SUM(CASE WHEN v.score_total >= " + ScoreCaliber.PASS_LINE
          + "                    THEN c.credits ELSE 0 END), 0) AS earned_credits, "
          + "  COUNT(v.course_id) AS taken_count, "
          + "  COUNT(CASE WHEN v.score_total < " + ScoreCaliber.PASS_LINE
          + "             THEN 1 END) AS fail_count "
          + "FROM student_profile sp "
          + "JOIN classes cl ON cl.id = sp.class_id AND cl.counselor_id = #{counselorId} "
          + "LEFT JOIN " + ScoreCaliber.LATEST_VALID_SCORE + " v ON v.student_id = sp.id "
          + "LEFT JOIN courses c ON c.id = v.course_id "
          + "GROUP BY sp.id, sp.student_id, sp.name, sp.class_name "
          + "HAVING fail_count > 0 "
          + "ORDER BY (earned_credits / NULLIF(attempted_credits, 0)) ASC, fail_count DESC, sp.id")
    List<Map<String, Object>> getInsufficientCreditStudents(@Param("counselorId") Long counselorId);

    /**
     * 某个班级的学分加权平均 GPA。
     *
     * <p>口径与 {@code STUDENT_GPA} 一致：先算每名学生的学分加权 GPA，再对这些 GPA
     * 取平均。不是"把全班成绩混在一起算一个平均分" —— 后者会被成绩门数多的学生主导。
     * 班级预警对比页面此前返回写死的 {@code 3.0}。
     */
    @Select("SELECT AVG(g.gpa) FROM " + ScoreCaliber.STUDENT_GPA + " g "
          + " JOIN student_profile sp ON sp.id = g.student_id "
          + " WHERE sp.class_id = #{classId} AND g.gpa IS NOT NULL")
    Double getClassAvgGpa(@Param("classId") Long classId);

    /**
     * 辅导员名下每名学生的学分与绩点（第二十一轮加，供「我的学生」详情弹窗）。
     * 列：{@code profile_id} / {@code earned_credits}(通过课程学分) /
     * {@code attempted_credits}(已修课程学分, 作分母) / {@code gpa}(学分加权)。
     *
     * <p>和 {@link #getInsufficientCreditStudents} 的区别：那个只返回**有挂科**的学生
     * (HAVING fail_count &gt; 0)，这个返回全部学生 —— 详情弹窗对每个人都可能打开。
     * GPA 用 {@link ScoreCaliber#GPA_EXPR}，与班级均分/排名同源。
     */
    @Select("SELECT sp.id AS profile_id, "
          + "  COALESCE(SUM(CASE WHEN v.score_total >= " + ScoreCaliber.PASS_LINE
          + "                    AND c.credits IS NOT NULL THEN c.credits ELSE 0 END), 0) AS earned_credits, "
          + "  COALESCE(SUM(CASE WHEN c.credits IS NOT NULL THEN c.credits ELSE 0 END), 0) AS attempted_credits, "
          + "  " + ScoreCaliber.GPA_EXPR + " AS gpa "
          + "FROM student_profile sp "
          + "JOIN classes cl ON cl.id = sp.class_id AND cl.counselor_id = #{counselorId} "
          + "LEFT JOIN " + ScoreCaliber.LATEST_VALID_SCORE + " v ON v.student_id = sp.id "
          + "LEFT JOIN courses c ON c.id = v.course_id "
          + "GROUP BY sp.id")
    List<Map<String, Object>> getStudentCreditStatsByCounselorId(@Param("counselorId") Long counselorId);
}
