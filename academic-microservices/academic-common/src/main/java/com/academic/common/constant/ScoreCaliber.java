package com.academic.common.constant;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 成绩统计口径（全项目唯一权威定义）。
 *
 * <p>背景：本项目曾同时存在两套并行口径 —— 学生端读 {@code scores}，预警引擎读
 * {@code score_record}，导致同一名学生的挂科数在两处对不上。此后所有"挂科/低分"
 * 统计一律以本类为准，禁止在各服务里自行写 {@code score < 60}。
 *
 * <p>口径三条：
 * <ol>
 *   <li>权威数据源是 {@code scores} 表；{@code score_record} 只是教师录入暂存表。</li>
 *   <li>挂科按课程去重，取该课程<b>最新一次有效成绩</b>判定，重修通过可抵扣历史挂科。</li>
 *   <li>{@code final_score IS NULL} 表示期末成绩未录入，<b>不计入</b>挂科，
 *       前端显示 {@link #PENDING_TEXT}。不可当作 0 分处理。</li>
 * </ol>
 *
 * <p>SQL 片段以 {@code static final String} 常量提供。Java 的编译期常量拼接
 * （JLS 15.29）允许它们直接拼进 MyBatis 的 {@code @Select} 注解，从而在
 * "项目零 XML mapper" 的前提下实现口径复用。
 */
public final class ScoreCaliber {

    /** 及格线。低于此值即为挂科。 */
    public static final int PASS_LINE = 60;

    /**
     * 辅导员端"低分关注线"。语义上不同于挂科线：辅导员要关注的是成绩偏低但尚未挂科的学生，
     * 因此这条线高于 {@link #PASS_LINE}，<b>不得</b>用它统计挂科门数。
     */
    public static final int ATTENTION_LINE = 70;

    /** 期末成绩未录入时的展示文案。 */
    public static final String PENDING_TEXT = "待录入";

    /**
     * 每门课程取"期末已录入"的最新一次成绩。
     *
     * <p>两个关键设计：
     * <ul>
     *   <li>用 {@code ROW_NUMBER()} 而非 {@code MAX(semester)} 自连接。自连接在
     *       同 (student, course, semester) 存在多行时会放大成笛卡尔积并重复计数；
     *       窗口函数天然保证一门课只出一行。</li>
     *   <li>先按 {@code final_score IS NOT NULL} 过滤，再取最新。若顺序颠倒，
     *       "上学期挂科、本学期重修但期末还没录"的学生会被洗白成待录入，
     *       从预警名单里消失。先过滤则保留历史挂科 —— 预警宁可保守，不可漏报。</li>
     * </ul>
     *
     * <p>{@code semester} 是真正的 int 列（全局学期序号，如 2~6），因此
     * {@code ORDER BY semester DESC} 是数值排序，不存在 "10" &lt; "2" 的字典序陷阱。
     *
     * <p>用法：{@code "SELECT ... FROM " + LATEST_VALID_SCORE + " v WHERE ..."}
     */
    public static final String LATEST_VALID_SCORE =
            "(SELECT lat.id, lat.student_id, lat.course_id, lat.semester, lat.score_total, "
          + "        lat.regular_score, lat.final_score, lat.year, lat.created_at, "
          + "        lat.grade, lat.grade_point "
          + "   FROM (SELECT s.id, s.student_id, s.course_id, s.semester, s.score_total, "
          + "                s.regular_score, s.final_score, s.year, s.created_at, "
          + "                s.grade, s.grade_point, "
          + "                ROW_NUMBER() OVER (PARTITION BY s.student_id, s.course_id "
          + "                                   ORDER BY s.semester DESC, s.id DESC) AS rn "
          + "           FROM scores s WHERE s.final_score IS NOT NULL) lat "
          + "  WHERE lat.rn = 1)";

    /**
     * 总评成绩 → 五级档（1 优 … 5 不及格）的 SQL 表达式，与
     * {@link com.academic.common.util.ScoreSyncSupport#scoreToLevel} 完全一致。
     *
     * <p>{@code scores} 表只存 {@code grade}（A/A-/B/C/D 字符串），没有五级档这一列。
     * 前端多处仍按 {@code score_level}（数字）渲染，这里统一派生，避免各服务各写一份 CASE。
     *
     * <p>固定绑定别名 {@code v}，即 {@link #LATEST_VALID_SCORE} 的标准别名。
     */
    public static final String SCORE_LEVEL_CASE =
            "CASE WHEN v.score_total >= 90 THEN 1 "
          + "     WHEN v.score_total >= 80 THEN 2 "
          + "     WHEN v.score_total >= 70 THEN 3 "
          + "     WHEN v.score_total >= 60 THEN 4 "
          + "     ELSE 5 END";

    /**
     * 全库每名学生的挂科门数（仅返回有挂科的）。列：studentId / studentName / failCount / avgScore。
     */
    public static final String FAIL_COUNT_BY_STUDENT =
            "SELECT v.student_id AS studentId, sp.name AS studentName, "
          + "       COUNT(*) AS failCount, AVG(v.score_total) AS avgScore "
          + "  FROM " + LATEST_VALID_SCORE + " v "
          + "  JOIN student_profile sp ON sp.id = v.student_id "
          + " WHERE v.score_total < " + PASS_LINE + " "
          + " GROUP BY v.student_id, sp.name "
          + " ORDER BY failCount DESC, v.student_id";

    /**
     * 单名学生的挂科门数。参数：{@code #{studentId}}（student_profile.id）。
     */
    public static final String FAIL_COUNT_BY_ONE_STUDENT =
            "SELECT COUNT(*) FROM " + LATEST_VALID_SCORE + " v "
          + " WHERE v.student_id = #{studentId} AND v.score_total < " + PASS_LINE;

    /**
     * 单名学生的挂科明细，按分数升序（首条即最低分，可用作预警的 triggerScore）。
     * 列：courseId / courseName / scoreTotal / semester。参数：{@code #{studentId}}。
     */
    public static final String FAIL_DETAIL_BY_STUDENT =
            "SELECT v.course_id AS courseId, c.name AS courseName, "
          + "       v.score_total AS scoreTotal, v.semester AS semester "
          + "  FROM " + LATEST_VALID_SCORE + " v "
          + "  LEFT JOIN courses c ON v.course_id = c.id "
          + " WHERE v.student_id = #{studentId} AND v.score_total < " + PASS_LINE + " "
          + " ORDER BY v.score_total ASC";

    /**
     * 某名学生完整成绩单（每门课取最新一次有效成绩）。
     * 参数：{@code #{studentId}}。供需要"自己数挂科"的调用方复用，避免各处重复实现。
     */
    public static final String STUDENT_LATEST_SCORES =
            "SELECT v.course_id AS courseId, c.name AS courseName, c.credits AS credits, "
          + "       v.score_total AS scoreTotal, v.grade AS grade, "
          + "       v.grade_point AS gradePoint, v.semester AS semester "
          + "  FROM " + LATEST_VALID_SCORE + " v "
          + "  LEFT JOIN courses c ON v.course_id = c.id "
          + " WHERE v.student_id = #{studentId} "
          + " ORDER BY v.semester DESC, v.course_id";

    /**
     * 学分加权平均绩点的分子／分母。
     *
     * <p>分子是 Σ(绩点 × 学分)，分母是参与计算的学分之和。两侧都要求
     * {@code grade_point} 与 {@code credits} 同时非空 —— 若只在一侧做非空判断，
     * 分子会漏掉某门课而分母仍计入它的学分，GPA 被系统性拉低。
     *
     * <p>声明为 private：它们只是下面两个常量的组成部分，外部应使用
     * {@link #STUDENT_GPA} 或 {@link #ONE_STUDENT_STATS}，避免各处自行拼装出
     * 分子分母不匹配的表达式。
     */
    private static final String GPA_NUM =
            "SUM(CASE WHEN v.grade_point IS NOT NULL AND c.credits IS NOT NULL "
          + "         THEN v.grade_point * c.credits ELSE 0 END)";
    private static final String GPA_DEN =
            "SUM(CASE WHEN v.grade_point IS NOT NULL AND c.credits IS NOT NULL "
          + "         THEN c.credits ELSE 0 END)";

    /**
     * 学分加权 GPA 的完整表达式（分子/分母），绑定别名 {@code v} 与 {@code c}。
     *
     * <p>供 {@code FROM scores v LEFT JOIN courses c ...} 这类自带 FROM 子句的查询使用
     * （例如"某学期绩点"）。用它替代各处手写 SUM/SUM —— 分子分母必须成对出现，
     * 单独抄一半是最容易犯又最难发现的错误。分母为 0 时返回 NULL 而非报错。
     */
    public static final String GPA_EXPR = GPA_NUM + " / NULLIF(" + GPA_DEN + ", 0)";

    /**
     * 每名学生的学分加权 GPA。列：student_id / gpa / graded_credits / course_count。
     *
     * <p>全班、全专业的平均 GPA 与班级排名都从这张派生表算，保证"我的 GPA"
     * 和"班级平均 GPA"是同一个公式的两个切片。此前这两者分别由
     * {@code getStudentDashboard}（学分加权）与 {@code calcClassAvgGpa}
     * （{@code 总评/20}）用两套公式计算，同一个学生的绩点在两个页面上对不上。
     *
     * <p>只统计有成绩记录的学生；{@code graded_credits} 为 0 的学生 {@code gpa} 为 NULL。
     */
    public static final String STUDENT_GPA =
            "(SELECT v.student_id, " + GPA_EXPR + " AS gpa, "
          + "        " + GPA_DEN + " AS graded_credits, COUNT(*) AS course_count "
          + "   FROM " + LATEST_VALID_SCORE + " v "
          + "   LEFT JOIN courses c ON c.id = v.course_id "
          + "  GROUP BY v.student_id)";

    /**
     * 单名学生的成绩汇总：门数、挂科门数、已修学分、GPA。
     * 参数：{@code #{studentId}}（student_profile.id）。列：totalCourses / failedCourses /
     * completedCredits / gpa / gradedCredits。
     *
     * <p>仪表盘用它取代原先的"取回成绩单在 Java 里循环"：口径写进 SQL 后，
     * 它与 {@link #STUDENT_GPA} 共用同一份 GPA 表达式，不可能再各算各的。
     */
    public static final String ONE_STUDENT_STATS =
            "SELECT COUNT(*) AS totalCourses, "
          + "       SUM(CASE WHEN v.score_total < " + PASS_LINE + " THEN 1 ELSE 0 END) AS failedCourses, "
          + "       SUM(CASE WHEN v.score_total >= " + PASS_LINE + " AND c.credits IS NOT NULL "
          + "                THEN c.credits ELSE 0 END) AS completedCredits, "
          + "       " + GPA_EXPR + " AS gpa, "
          + "       " + GPA_DEN + " AS gradedCredits "
          + "  FROM " + LATEST_VALID_SCORE + " v "
          + "  LEFT JOIN courses c ON c.id = v.course_id "
          + " WHERE v.student_id = #{studentId}";

    /** 毕业要求总学分。目前无对应数据表，暂以常量表示，全项目只此一处。 */
    public static final int GRADUATION_CREDITS = 120;

    // ------------------------------------------------------------------
    // Java 侧同口径实现。
    // 用于 ai-service 这类只拿得到 Feign 返回的 List<Map>、连不上数据库的场景，
    // 保证它算出的挂科数与数据库口径完全一致。
    // ------------------------------------------------------------------

    /**
     * 按课程分组，每门课保留 semester 最大的那一行；期末未录入的行被排除。
     *
     * <p>幂等：对已处理过的列表再调用一次结果不变，因此调用方无需担心重复调用。
     */
    public static List<Map<String, Object>> latestPerCourse(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Object, Map<String, Object>> best = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            if (row == null || isPending(row)) {
                continue;
            }
            Object courseId = row.get("courseId");
            if (courseId == null) {
                continue;
            }
            Map<String, Object> kept = best.get(courseId);
            if (kept == null || semesterOf(row) > semesterOf(kept)) {
                best.put(courseId, row);
            }
        }
        return new ArrayList<>(best.values());
    }

    /**
     * 挂科门数。内部先做 {@link #latestPerCourse}，调用方无需自行去重。
     * 期末未录入的课程不计入。
     */
    public static int countFailCourses(List<Map<String, Object>> rows) {
        int count = 0;
        for (Map<String, Object> row : latestPerCourse(rows)) {
            Double score = toDouble(row.get("scoreTotal"));
            if (score != null && score < PASS_LINE) {
                count++;
            }
        }
        return count;
    }

    /**
     * 是否为"期末未录入"。注意 {@code null < 60} 在 Java/JS 中的比较语义不同
     * （{@code null < 60} 在 Java 下不成立，在 JS 下会被强制转成 0 而成立），
     * 凡是判断某行是否参与统计，一律用本方法。
     *
     * <p>键名同时兼容 camelCase 与下划线两种写法。
     *
     * <p>当 {@code finalScore} 键<b>完全缺失</b>时返回 {@code false}（不视为待录入）：
     * 那说明数据源压根没提供这个字段（例如旧的 Feign 载荷），此时按已有成绩计算
     * 是唯一能做的判断。若反过来当成待录入，会让整份成绩单被清空、
     * 分析结果变成"暂无成绩数据"，代价远大于多算一门。
     */
    public static boolean isPending(Map<String, Object> row) {
        if (row == null) {
            return true;
        }
        if (row.containsKey("finalScore")) {
            return row.get("finalScore") == null;
        }
        if (row.containsKey("final_score")) {
            return row.get("final_score") == null;
        }
        return false;
    }

    /** 是否为挂科科目（已录入且低于及格线）。 */
    public static boolean isFail(Map<String, Object> row) {
        if (isPending(row)) {
            return false;
        }
        Double score = toDouble(row.get("scoreTotal"));
        return score != null && score < PASS_LINE;
    }

    private static int semesterOf(Map<String, Object> row) {
        Double semester = toDouble(row.get("semester"));
        return semester == null ? 0 : semester.intValue();
    }

    /**
     * 宽松数值转换。Feign 反序列化后同一字段可能是 Integer / Double / BigDecimal / String，
     * 这里统一处理；无法识别时返回 null 而非 0 —— 返回 0 会把"未录入"变成"挂科 0 分"。
     */
    public static Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private ScoreCaliber() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
