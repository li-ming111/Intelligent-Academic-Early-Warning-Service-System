package com.academic.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 把库里的真实数据拼成 AI 分析提示词。
 *
 * <p>只给模型一个 ID 时，它没有数据可依，会自己编等级和数字
 * （实测轻度预警被写成"中度偏高风险"）。分析必须基于库里查出的
 * 预警记录、统计口径和成绩，否则前端显示的内容和页面数据对不上。
 */
@Service
public class AnalysisContextService {

    private final JdbcTemplate jdbcTemplate;

    public AnalysisContextService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 单条预警分析；预警不存在时返回 null。 */
    public String buildWarningPrompt(Long warningId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT aw.student_name, aw.warning_level, aw.fail_count, aw.description, "
              + "wr.name AS rule_name, "
              + "awc.course_name, awc.score_total, awc.semester "
              + "FROM academic_warning aw "
              + "LEFT JOIN warning_rules wr ON wr.id = aw.rule_id "
              + "LEFT JOIN academic_warning_course awc ON awc.warning_id = aw.id "
              + "WHERE aw.id = ? ORDER BY awc.id", warningId);
        if (rows.isEmpty()) {
            return null;
        }

        Map<String, Object> head = rows.get(0);
        String studentName = text(head.get("student_name"), "该学生");
        int level = number(head.get("warning_level"), 1);
        String levelName = levelName(level);

        StringBuilder courses = new StringBuilder();
        for (Map<String, Object> row : rows) {
            Object courseName = row.get("course_name");
            if (courseName == null) {
                continue;
            }
            if (courses.length() > 0) {
                courses.append("；");
            }
            courses.append(courseName).append(" ").append(score(row.get("score_total"))).append(" 分");
            Object semester = row.get("semester");
            if (semester != null) {
                courses.append("（第").append(semester).append("学期）");
            }
        }

        return "你是高校学业预警系统的分析助手。下面是一条真实预警记录，只能依据这些数据作答，不得编造，也不得改变预警等级。\n"
             + "【预警记录】\n"
             + "学生：" + studentName + "\n"
             + "预警等级：" + levelName + "（系统已判定，共三级，级别 " + level + "）\n"
             + "触发规则：" + text(head.get("rule_name"), "-") + "\n"
             + "累计挂科门数：" + number(head.get("fail_count"), 0) + "\n"
             + "预警说明：" + text(head.get("description"), "-") + "\n"
             + "挂科课程：" + (courses.length() == 0 ? "无课程明细" : courses.toString()) + "\n"
             + "【输出要求】\n"
             + "1. 第一句写“风险判断：系统判定为" + levelName + "”，再补一句依据；\n"
             + "2. 原因分析只引用上面的课程、分数和预警说明，不要臆测缺勤、作业未交、基础薄弱等未提供的信息；\n"
             + "3. 给出 2-3 条可执行的帮扶建议；\n"
             + "4. 全文 200 字以内，不要使用 Markdown 符号（如 **、#、-）。";
    }

    /** 辅导员所管学生的预警趋势；辅导员不存在或未管理班级时返回 null。 */
    public String buildWarningTrendPrompt(Long counselorId) {
        List<Map<String, Object>> classes = jdbcTemplate.queryForList(
                "SELECT name FROM classes WHERE counselor_id = ? ORDER BY id", counselorId);
        if (classes.isEmpty()) {
            return null;
        }
        List<String> classNames = new ArrayList<>();
        for (Map<String, Object> row : classes) {
            classNames.add(text(row.get("name"), "-"));
        }

        Integer students = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM student_profile sp "
              + "JOIN classes c ON c.id = sp.class_id WHERE c.counselor_id = ?",
                Integer.class, counselorId);

        int mild = 0, medium = 0, severe = 0;
        for (Map<String, Object> row : jdbcTemplate.queryForList(
                "SELECT aw.warning_level AS lv, COUNT(*) AS c "
              + "FROM academic_warning aw "
              + "JOIN student_profile sp ON sp.id = aw.student_id "
              + "JOIN classes c ON c.id = sp.class_id "
              + "WHERE c.counselor_id = ? GROUP BY aw.warning_level", counselorId)) {
            int c = number(row.get("c"), 0);
            switch (number(row.get("lv"), 1)) {
                case 3 -> severe += c;
                case 2 -> medium += c;
                default -> mild += c;
            }
        }
        int total = mild + medium + severe;

        int pending = 0, released = 0;
        for (Map<String, Object> row : jdbcTemplate.queryForList(
                "SELECT aw.status AS st, COUNT(*) AS c "
              + "FROM academic_warning aw "
              + "JOIN student_profile sp ON sp.id = aw.student_id "
              + "JOIN classes c ON c.id = sp.class_id "
              + "WHERE c.counselor_id = ? GROUP BY aw.status", counselorId)) {
            int c = number(row.get("c"), 0);
            if (number(row.get("st"), 0) == 0) {
                pending += c;
            } else {
                released += c;
            }
        }

        List<Map<String, Object>> months = jdbcTemplate.queryForList(
                "SELECT DATE_FORMAT(aw.created_at, '%Y-%m') AS m, COUNT(*) AS c "
              + "FROM academic_warning aw "
              + "JOIN student_profile sp ON sp.id = aw.student_id "
              + "JOIN classes c ON c.id = sp.class_id "
              + "WHERE c.counselor_id = ? GROUP BY m ORDER BY m DESC LIMIT 6", counselorId);
        Collections.reverse(months);
        StringBuilder monthly = new StringBuilder();
        for (Map<String, Object> row : months) {
            if (monthly.length() > 0) {
                monthly.append("、");
            }
            monthly.append(text(row.get("m"), "-")).append(" 共 ").append(number(row.get("c"), 0)).append(" 条");
        }

        return "你是高校学业预警系统的分析助手。下面是该辅导员所管学生的真实预警统计，"
             + "只能依据这些数据作答，不得编造未提供的数字、姓名或原因。\n"
             + "【管理范围】班级：" + String.join("、", classNames) + "；学生总数：" + students + " 人\n"
             + "【预警数据】预警总数：" + total + " 条；等级分布：轻度 " + mild
             + " 条、中度 " + medium + " 条、严重 " + severe + " 条；"
             + "处理状态：待处理 " + pending + " 条、已解除 " + released + " 条\n"
             + "【月度分布】" + (monthly.length() == 0 ? "暂无预警" : monthly.toString()) + "\n"
             + "【输出要求】\n"
             + "1. 第一句写“预警概况：当前共有" + total + "条预警，其中轻度" + mild
             + "条、中度" + medium + "条、严重" + severe + "条，待处理" + pending + "条”，数字必须与上面一致；\n"
             + "2. 结合月度分布说明变化趋势，只引用上面的月份和条数；\n"
             + "3. 给出 2-3 条重点关注与干预建议；\n"
             + "4. 全文 300 字以内，不要使用 Markdown 符号，不要编造学生姓名。";
    }

    /** 班级成绩分析；班级不存在时返回 null。 */
    public String buildClassScoresPrompt(Long classId) {
        List<Map<String, Object>> classRows = jdbcTemplate.queryForList(
                "SELECT name FROM classes WHERE id = ?", classId);
        if (classRows.isEmpty()) {
            return null;
        }
        String className = text(classRows.get(0).get("name"), "该班级");

        Integer students = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM student_profile WHERE class_id = ?", Integer.class, classId);

        Map<String, Object> stats = jdbcTemplate.queryForMap(
                "SELECT COUNT(*) AS c, AVG(s.score_total) AS avg_score, "
              + "SUM(s.score_total < 60) AS fails, "
              + "MIN(s.semester) AS min_sem, MAX(s.semester) AS max_sem "
              + "FROM scores s JOIN student_profile sp ON sp.id = s.student_id "
              + "WHERE sp.class_id = ?", classId);
        int scoreCount = number(stats.get("c"), 0);
        int fails = number(stats.get("fails"), 0);
        String avgText = scoreCount == 0 ? "暂无" : decimal(stats.get("avg_score"));
        String passRate = scoreCount == 0 ? "暂无"
                : decimal(BigDecimal.valueOf(scoreCount - fails)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(scoreCount), 1, RoundingMode.HALF_UP)) + "%";
        String semesterRange = scoreCount == 0 ? "暂无"
                : "第" + number(stats.get("min_sem"), 0) + "-" + number(stats.get("max_sem"), 0) + "学期";

        List<Map<String, Object>> weakCourses = jdbcTemplate.queryForList(
                "SELECT co.name, COUNT(*) AS n, AVG(s.score_total) AS avg_score, "
              + "SUM(s.score_total < 60) AS fails "
              + "FROM scores s "
              + "JOIN student_profile sp ON sp.id = s.student_id "
              + "JOIN courses co ON co.id = s.course_id "
              + "WHERE sp.class_id = ? GROUP BY co.id, co.name "
              + "ORDER BY avg_score ASC LIMIT 5", classId);
        StringBuilder weak = new StringBuilder();
        for (Map<String, Object> row : weakCourses) {
            if (weak.length() > 0) {
                weak.append("；");
            }
            weak.append(text(row.get("name"), "-"))
                .append("（平均 ").append(decimal(row.get("avg_score"))).append(" 分，")
                .append("不及格 ").append(number(row.get("fails"), 0)).append(" 人次）");
        }

        return "你是高校学业预警系统的分析助手。下面是该班级的真实成绩统计，"
             + "只能依据这些数据作答，不得编造未提供的数字或学生姓名。\n"
             + "【班级数据】班级：" + className + "；学生数：" + students + " 人；"
             + "成绩记录：" + scoreCount + " 条（" + semesterRange + "）\n"
             + "【成绩概况】平均分：" + avgText + "；及格率：" + passRate
             + "；不及格记录：" + fails + " 条\n"
             + "【薄弱课程】" + (weak.length() == 0 ? "暂无成绩记录" : weak.toString()) + "\n"
             + "【输出要求】\n"
             + "1. 第一句写“班级概况：" + className + "共有" + students + "名学生，平均分" + avgText
             + "，及格率" + passRate + "”，数字必须与上面一致；\n"
             + "2. 结合薄弱课程的平均分和不及格人次分析薄弱环节，只引用上面的数据；\n"
             + "3. 给出 2-3 条教学或帮扶建议；\n"
             + "4. 全文 300 字以内，不要使用 Markdown 符号，不要编造学生姓名。";
    }

    private static String levelName(int level) {
        return switch (level) {
            case 3 -> "严重预警";
            case 2 -> "中度预警";
            default -> "轻度预警";
        };
    }

    private static String text(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? fallback : s;
    }

    private static int number(Object value, int fallback) {
        return value instanceof Number n ? n.intValue() : fallback;
    }

    private static String score(Object value) {
        return value instanceof BigDecimal d ? d.stripTrailingZeros().toPlainString() : text(value, "-");
    }

    private static String decimal(Object value) {
        if (value instanceof BigDecimal d) {
            return d.setScale(1, RoundingMode.HALF_UP).toPlainString();
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue()).setScale(1, RoundingMode.HALF_UP).toPlainString();
        }
        return text(value, "-");
    }
}
