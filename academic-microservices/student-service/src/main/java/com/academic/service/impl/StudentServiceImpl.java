package com.academic.service.impl;

import com.academic.common.constant.ScoreCaliber;
import com.academic.common.entity.Score;
import com.academic.common.entity.AcademicWarning;
import com.academic.common.entity.AssistancePlan;
import com.academic.dto.StudentDashboardResponse;
import com.academic.entity.*;
import com.academic.mapper.*;
import com.academic.service.StudentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Slf4j
@Service
@Primary
public class StudentServiceImpl implements StudentService {

    private final StudentProfileMapper studentProfileMapper;
    private final ScoreMapper scoreMapper;
    private final AcademicWarningMapper academicWarningMapper;
    private final ScoreAppealMapper scoreAppealMapper;
    private final NotificationMapper notificationMapper;
    private final SubscriptionPreferenceMapper subscriptionPreferenceMapper;
    private final AssistanceEvaluationMapper assistanceEvaluationMapper;
    private final AssistancePlanMapper assistancePlanMapper;

    public StudentServiceImpl(StudentProfileMapper studentProfileMapper,
                              ScoreMapper scoreMapper,
                              AcademicWarningMapper academicWarningMapper,
                              ScoreAppealMapper scoreAppealMapper,
                              NotificationMapper notificationMapper,
                              SubscriptionPreferenceMapper subscriptionPreferenceMapper,
                              AssistanceEvaluationMapper assistanceEvaluationMapper,
                              AssistancePlanMapper assistancePlanMapper) {
        this.studentProfileMapper = studentProfileMapper;
        this.scoreMapper = scoreMapper;
        this.academicWarningMapper = academicWarningMapper;
        this.scoreAppealMapper = scoreAppealMapper;
        this.notificationMapper = notificationMapper;
        this.subscriptionPreferenceMapper = subscriptionPreferenceMapper;
        this.assistanceEvaluationMapper = assistanceEvaluationMapper;
        this.assistancePlanMapper = assistancePlanMapper;
    }

    /**
     * 把入口的 userId 解析成 student_profile.id。
     *
     * <p>本服务的接口路径参数来自前端登录态，是 {@code users.id}；而 scores、
     * academic_warning、assistance_plans、benchmark_analysis 四张表的 {@code student_id}
     * 存的都是 {@code student_profile.id}。两个 ID 空间不同（如司明 user_id=27 / profile.id=3）。
     *
     * <p>混用的后果不是报错而是<b>静默返回空</b>——查询合法、只是查不到人，
     * 于是页面上显示"暂无数据"，排查时极难定位。所有按学生查询的入口都必须先过这里。
     */
    private Long resolveProfileId(Long userId) {
        if (userId == null) {
            return null;
        }
        StudentProfile sp = studentProfileMapper.selectByUserId(userId);
        return sp != null ? sp.getId() : null;
    }

    /** 聚合查询返回的计数/学分等一律经此转换；无法识别时取 0（这些字段是计数，0 是安全值）。 */
    private static int intValue(Object value) {
        Double d = ScoreCaliber.toDouble(value);
        return d == null ? 0 : d.intValue();
    }

    /** 比率类字段：无法识别时取 0，但对调用方而言 0 与"无数据"需自行区分。 */
    private static double doubleValue(Object value) {
        Double d = ScoreCaliber.toDouble(value);
        return d == null ? 0 : d;
    }

    /**
     * 学期号 → 前端展示用的学期标识（{@code 2025-2026-1} 这种）。
     *
     * <p>{@code scores.semester} 是全局学期序号（2~6），前端下拉框用的是学年学期字符串。
     * 映射规则：第 n 个全局学期落在入学后的第 {@code (n-1)/2} 个学年，奇数为上学期。
     * 例如 2023 级：3 → 2024-2025-1、5 → 2025-2026-1、6 → 2025-2026-2。
     *
     * <p>基准学年取自学号前四位（{@code 2023020616} → 2023）。{@code student_profile}
     * 虽有 {@code enrollment_year} 列，但线上全部为 NULL，不能作为依据；同一班级里
     * 也存在不同年级的学生（如 2022 级与 2023 级同班），所以必须按学生各自推算。
     */
    private static String semesterLabel(Integer semester, String studentNo) {
        if (semester == null) {
            return "";
        }
        int year = entranceYear(studentNo) + (semester - 1) / 2;
        int half = (semester - 1) % 2 + 1;
        return year + "-" + (year + 1) + "-" + half;
    }

    /**
     * 入学年份，取自学号前四位（{@code 2023020616} → 2023）。
     *
     * <p>不用 {@code student_profile.enrollment_year}：线上该列全为 NULL。
     * 也不能按班级取一个公共值 —— 同一班级里存在不同年级的学生。学号异常时退回 2023。
     */
    private static int entranceYear(String studentNo) {
        if (studentNo != null && studentNo.length() >= 4) {
            try {
                int parsed = Integer.parseInt(studentNo.substring(0, 4));
                // 学号年份合理区间校验，避免把异常学号算成公元 9999 年
                if (parsed >= 2000 && parsed <= 2100) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
                // 学号不含年份前缀，退回默认基准
            }
        }
        return 2023;
    }

    // ==================== 仪表盘 ====================
    @Override
    public StudentDashboardResponse getStudentDashboard(Long studentId) {
        log.info("获取学生仪表盘数据: userId={}", studentId);
        StudentProfile sp = studentProfileMapper.selectByUserId(studentId);
        if (sp == null) {
            log.warn("学生不存在: userId={}", studentId);
            return new StudentDashboardResponse();
        }
        Long profileId = sp.getId();
        // 统计口径走 ScoreCaliber.ONE_STUDENT_STATS：每门课取最新一次有效成绩，
        // 期末未录入不计入，只有通过的课程才计学分。
        //
        // 口径写在 SQL 常量里而不是在这里循环，是为了与"班级平均 GPA / 班级排名"
        // 共用同一份 GPA 表达式 —— 否则"我的 GPA"和"班级平均 GPA"会各算各的，
        // 出现我的 1.53 而班级均值 3.05 这种换算不到一起的数字。
        Map<String, Object> stats = scoreMapper.selectOneStudentStats(profileId);
        int totalCourses = stats == null ? 0 : intValue(stats.get("totalCourses"));
        int failedCourses = stats == null ? 0 : intValue(stats.get("failedCourses"));
        int completedCredits = stats == null ? 0 : intValue(stats.get("completedCredits"));
        double gpa = stats == null ? 0 : doubleValue(stats.get("gpa"));

        List<AcademicWarning> warnings = academicWarningMapper.selectPendingByStudentId(profileId.toString());
        int wc = warnings.size();
        // 取最高等级，而不是列表首条 —— 列表顺序不保证
        int maxLevel = 0;
        for (AcademicWarning w : warnings) {
            if (w.getWarningLevel() != null && w.getWarningLevel() > maxLevel) {
                maxLevel = w.getWarningLevel();
            }
        }
        String wl = maxLevel > 0 ? String.valueOf(maxLevel) : "正常";

        // 最近预警列表
        java.util.List<java.util.Map<String, Object>> recentWarnings = new java.util.ArrayList<>();
        for (AcademicWarning w : warnings) {
            java.util.Map<String, Object> item = new java.util.HashMap<>();
            item.put("id", w.getId());
            // 正文按 warning_message → description 兜底；标题优先用 title。
            // 早期实现只读 description，而生成预警时正文写在 warning_message，
            // 结果学生端"最近预警"永远显示成空的"学业预警"。
            String body = w.getWarningMessage() != null ? w.getWarningMessage() : w.getDescription();
            String title = w.getTitle() != null
                    ? w.getTitle()
                    : (body != null && body.length() > 20 ? body.substring(0, 20) : (body != null ? body : "学业预警"));
            item.put("title", title);
            item.put("description", body);
            item.put("warningLevel", w.getWarningLevel() != null ? w.getWarningLevel() : 1);
            item.put("createdAt", w.getCreatedAt() != null ? w.getCreatedAt().toString() : "");
            recentWarnings.add(item);
        }

        StudentDashboardResponse resp = new StudentDashboardResponse();
        resp.setStudentId(studentId);
        resp.setStudentName(sp.getName());
        resp.setClassName(sp.getClassName() != null ? sp.getClassName() : "未知班级");
        // 专业名按 major_id 查表。原来写死"计算机科学与技术"，换专业的学生看到的是错的。
        String majorName = sp.getMajorId() != null
                ? studentProfileMapper.selectMajorName(sp.getMajorId()) : null;
        resp.setMajorName(majorName != null ? majorName : "未知专业");
        resp.setGpa(BigDecimal.valueOf(gpa).setScale(2, RoundingMode.HALF_UP).doubleValue());
        resp.setWarningCount(wc);
        resp.setWarningLevel(wl);
        resp.setFailedCoursesCount(failedCourses);
        resp.setTotalCoursesCount(totalCourses);
        resp.setCompletedCredits(completedCredits);
        // 毕业学分要求全项目统一取 ScoreCaliber.GRADUATION_CREDITS（原为 120/40/20 三个值并存）
        resp.setRequiredCredits(ScoreCaliber.GRADUATION_CREDITS);
        resp.setRecentWarnings(recentWarnings);
        return resp;
    }

    // ==================== 成绩 ====================
    @Override
    public Object getStudentScores(Long studentId) { return getStudentScores(studentId, null); }

    /**
     * 学生成绩单，可按学期筛选。
     *
     * <p>学期筛选原先这样解析 {@code "2025-2026-2"}：{@code year = parts[0]}（=2025）、
     * {@code sem = parts[2]}（=2 学期内的第几学期），再按 {@code year = 2025 AND semester = 2}
     * 过滤。两个都对不上：
     *
     * <ul>
     *   <li>{@code semester} 是**全局学期序号**，不是"学年内的第几学期"。标签
     *       {@code 2025-2026-2} 对应的是第 6 个学期，不是第 2 个。</li>
     *   <li>{@code year} 列与 {@code semester} 不自洽（同一序号在不同行挂着不同年份），
     *       拿它做等值过滤会把该学期的绝大多数行排除掉。</li>
     * </ul>
     *
     * <p>实际后果是学生端学期下拉里，除了个别巧合命中的标签，其余全部显示"暂无成绩记录"。
     * 现在统一走 {@link #parseSemesterIndex}，把标签按入学年份换算成全局序号，
     * 并且**不再用 {@code year} 列做过滤** —— 学期身份只由 {@code scores.semester} 决定。
     */
    @Override
    public Object getStudentScores(Long studentId, String semester) {
        log.info("获取学生成绩: userId={}, semester={}", studentId, semester);
        StudentProfile sp = studentProfileMapper.selectByUserId(studentId);
        if (sp == null) return Collections.emptyList();
        Long profileId = sp.getId();
        if (semester == null || semester.trim().isEmpty()) {
            return scoreMapper.selectByStudentId(profileId.toString());
        }
        Integer semIndex = parseSemesterIndex(semester, sp.getStudentId());
        if (semIndex == null) {
            // 传了学期但解析不出来：返回空。若退回整份成绩单，学生会以为筛选成功了
            log.warn("无法解析的学期标识,按空结果返回: studentNo={}, semester={}", sp.getStudentId(), semester);
            return Collections.emptyList();
        }
        return scoreMapper.selectByStudentIdAndSemester(profileId.toString(), semIndex);
    }

    // ==================== 预警 ====================
    @Override
    public Object getStudentWarnings(Long studentId) {
        log.info("获取学生预警: userId={}", studentId);
        StudentProfile sp = studentProfileMapper.selectByUserId(studentId);
        if (sp == null) return Collections.emptyList();
        return academicWarningMapper.selectByStudentId(sp.getId().toString());
    }

    // ==================== 学生信息 ====================
    @Override
    public Object getStudentInfo(Long studentId) {
        log.info("获取学生信息: userId={}", studentId);
        StudentProfile sp = studentProfileMapper.selectByUserId(studentId);
        if (sp == null) return Map.of("error", "学生不存在");
        return sp;
    }

    @Override
    public Object registerStudent(Object request) {
        log.info("学生注册");
        return "注册成功";
    }

    // ==================== 对标分析 ====================
    /**
     * 各学期 GPA 趋势。
     *
     * <p>原实现把 userId 当学号查（恒查不到人，返回空列表），且用
     * {@code 总分/门数/20} 折算 GPA —— 该公式与本服务其它地方的学分加权口径
     * 不是一回事。现在统一走 {@link ScoreCaliber#GPA_EXPR}，按学期分别计算
     * 该学期所修课程的学分加权 GPA。
     */
    @Override
    public Object getHistoryBenchmark(Long studentId) {
        log.info("获取历史GPA数据: userId={}", studentId);
        Long profileId = resolveProfileId(studentId);
        if (profileId == null) {
            return Collections.emptyList();
        }
        StudentProfile sp = studentProfileMapper.selectById(profileId);
        String studentNo = sp != null ? sp.getStudentId() : null;

        List<Map<String, Object>> result = new ArrayList<>();
        for (Integer semester : scoreMapper.selectSemesters(profileId)) {
            Double gpa = scoreMapper.calcSemesterGpa(profileId, semester);
            Map<String, Object> bm = new HashMap<>();
            bm.put("semester", semester);
            // 同时给出可读标识，前端下拉框按 "2025-2026-1" 这类字符串筛选
            bm.put("semesterLabel", semesterLabel(semester, studentNo));
            bm.put("studentGpa", BigDecimal.valueOf(gpa != null ? gpa : 0)
                    .setScale(2, RoundingMode.HALF_UP));
            result.add(bm);
        }
        return result;
    }

    /**
     * 最新一次对标分析。
     *
     * <p>原实现把 userId 直接当 student_profile.id 查（{@code benchmark_analysis.student_id}
     * 存的是后者），两个 ID 空间不相交，因此永远返回空 map。
     *
     * <p>表里的快照是与成绩解耦的历史归档，没有任何代码会更新它，直接用会给出过期的
     * GPA（种子数据还是按 {@code 总评/20} 算的）。因此这里全部改为按 {@code scores}
     * 实算，且 {@code studentGpa} 取整体学分加权绩点，与仪表盘同源同值。
     */
    @Override
    public Object getLatestBenchmark(Long studentId) {
        log.info("获取最新对标分析: userId={}", studentId);
        Long profileId = resolveProfileId(studentId);
        if (profileId == null) {
            return Collections.emptyMap();
        }
        StudentProfile sp = studentProfileMapper.selectById(profileId);
        if (sp == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> live = new HashMap<>();
        Map<String, Object> stats = scoreMapper.selectOneStudentStats(profileId);
        live.put("studentId", profileId);
        live.put("studentName", sp.getName());
        live.put("className", sp.getClassName());
        live.put("studentGpa", BigDecimal.valueOf(stats != null ? doubleValue(stats.get("gpa")) : 0)
                .setScale(2, RoundingMode.HALF_UP));
        live.put("coursesPassed", stats != null ? intValue(stats.get("totalCourses"))
                - intValue(stats.get("failedCourses")) : 0);
        live.put("coursesFailed", stats != null ? intValue(stats.get("failedCourses")) : 0);
        live.put("requiredCredits", ScoreCaliber.GRADUATION_CREDITS);
        live.put("creditsOnTrack", stats != null ? intValue(stats.get("completedCredits")) : 0);
        if (sp.getClassId() != null) {
            Double classAvg = scoreMapper.calcClassAvgGpa(sp.getClassId());
            live.put("classAvgGpa", classAvg != null
                    ? BigDecimal.valueOf(classAvg).setScale(2, RoundingMode.HALF_UP) : null);
            live.put("classRank", scoreMapper.calcClassRank(profileId, sp.getClassId()));
            live.put("classTotal", scoreMapper.countClassStudents(sp.getClassId()));
        }
        if (sp.getMajorId() != null) {
            Double majorAvg = scoreMapper.calcMajorAvgGpa(sp.getMajorId());
            live.put("majorAvgGpa", majorAvg != null
                    ? BigDecimal.valueOf(majorAvg).setScale(2, RoundingMode.HALF_UP) : null);
            live.put("majorRank", scoreMapper.calcMajorRank(profileId, sp.getMajorId()));
            live.put("majorTotal", scoreMapper.countMajorStudents(sp.getMajorId()));
        }
        return live;
    }

    /**
     * 学期对标分析。
     *
     * <p>原实现只有一处"真"数据（学生自己的成绩），其余对比项全是写死的常量：
     * 每门课的班级均分 75.0、专业均分 72.0、单科排名 10、总人数 100，
     * 以及整学期的班级均分 3.0、专业均分 2.9、排名 15、总人数 100。
     * 也就是说学生看到的"我在班里排第 15 / 共 100 人"是凭空生成的。
     *
     * <p>现在全部改为按 {@link ScoreCaliber} 从 {@code scores} 实算：
     * 单科均分取同班/同专业修过该课的学生的平均分，排名取 GPA 名次。
     */
    @Override
    public Object getSemesterBenchmark(Long studentId, String semester) {
        log.info("获取学期对标分析: userId={}, semester={}", studentId, semester);
        Long profileId = resolveProfileId(studentId);
        if (profileId == null) {
            return Collections.emptyMap();
        }
        StudentProfile sp = studentProfileMapper.selectById(profileId);
        Integer semIndex = parseSemesterIndex(semester, sp != null ? sp.getStudentId() : null);

        // 这里曾优先返回 benchmark_analysis 里的快照，现已去掉：那张表没有任何代码写入，
        // 全部 27 行都是种子数据，class_avg_gpa / required_credits 是写死的常量。
        // "优先读快照"的实际效果是学生永远看到那份假数据，实算分支根本走不到。

        if (semIndex == null) {
            // 学期标识无法解析（既不是 "2025-2026-1" 也不是序号），无法定位成绩
            return Collections.emptyMap();
        }

        List<Map<String, Object>> courseDetails = new ArrayList<>();
        List<Score> scores = scoreMapper.selectByStudentIdAndSemester(profileId.toString(), semIndex);
        for (Score s : scores) {
            if (s.getFinalScore() == null) {
                continue;   // 期末未录入，不参与均分对比
            }
            Map<String, Object> cd = new HashMap<>();
            cd.put("courseId", s.getCourseId());
            cd.put("courseName", s.getCourseName() != null
                    ? s.getCourseName() : "课程" + s.getCourseId());
            cd.put("studentScore", s.getScoreTotal() != null ? s.getScoreTotal().doubleValue() : null);
            cd.put("classAverage", scoreMapper.calcClassAvgScoreOfCourse(profileId, s.getCourseId()));
            cd.put("departmentAverage", scoreMapper.calcMajorAvgScoreOfCourse(profileId, s.getCourseId()));
            courseDetails.add(cd);
        }

        Double gpa = scoreMapper.calcSemesterGpa(profileId, semIndex);
        Map<String, Object> bm = new HashMap<>();
        bm.put("studentId", profileId);
        bm.put("semester", semester);
        bm.put("semesterIndex", semIndex);
        bm.put("gpa", BigDecimal.valueOf(gpa != null ? gpa : 0)
                .setScale(2, RoundingMode.HALF_UP).doubleValue());
        bm.put("courses", courseDetails);
        if (sp != null && sp.getClassId() != null) {
            Double classAvg = scoreMapper.calcClassAvgGpa(sp.getClassId());
            bm.put("classAverage", classAvg != null
                    ? BigDecimal.valueOf(classAvg).setScale(2, RoundingMode.HALF_UP).doubleValue() : null);
            bm.put("rank", scoreMapper.calcClassRank(profileId, sp.getClassId()));
            bm.put("totalStudents", scoreMapper.countClassStudents(sp.getClassId()));
        }
        if (sp != null && sp.getMajorId() != null) {
            Double majorAvg = scoreMapper.calcMajorAvgGpa(sp.getMajorId());
            bm.put("departmentAverage", majorAvg != null
                    ? BigDecimal.valueOf(majorAvg).setScale(2, RoundingMode.HALF_UP).doubleValue() : null);
            bm.put("majorRank", scoreMapper.calcMajorRank(profileId, sp.getMajorId()));
            bm.put("majorTotalStudents", scoreMapper.countMajorStudents(sp.getMajorId()));
        }
        return bm;
    }

    /**
     * 把前端的学期标识解析成 {@code scores.semester} 的全局学期序号。
     * 接受两种写法：学年学期标签 {@code "2025-2026-1"} 或直接的数字序号（{@code "6"}）。
     *
     * <p>标签换算：{@code (起始年 - 入学年) * 2 + 学期内的第几学期}。
     * 与 {@link #semesterLabel} 互为逆运算，改动必须同步。
     *
     * @return 全局学期序号；无法识别时返回 {@code null}
     */
    private Integer parseSemesterIndex(String semester, String studentNo) {
        if (semester == null || semester.trim().isEmpty()) {
            return null;
        }
        String text = semester.trim();
        // 直接是序号
        try {
            int index = Integer.parseInt(text);
            return index >= 1 ? index : null;
        } catch (NumberFormatException ignored) {
            // 继续按 "2025-2026-1" 解析
        }
        String[] parts = text.split("-");
        if (parts.length < 3) {
            return null;
        }
        try {
            int year = Integer.parseInt(parts[0].trim());
            int half = Integer.parseInt(parts[2].trim());
            if (half != 1 && half != 2) {
                return null;
            }
            int index = (year - entranceYear(studentNo)) * 2 + half;
            // 标签年份早于入学年份说明该学期不存在（如 2022 级学生传了 2021-2022-1）
            return index >= 1 ? index : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * 班级排名。
     *
     * <p>原实现有三个问题叠加：{@code classId} 缺失时用 userId 当学号查学生（查不到）；
     * 排行榜取自不会被更新的 {@code benchmark_analysis} 快照；比较的是
     * {@code list.get(i).getStudentId().equals(studentId)} —— 左边是 profile.id、
     * 右边是 users.id，永远不相等，所以 {@code rank} 恒为 0。学生看到的就是
     * "排名 0 / 共 N 人"。现在改为按 {@code scores} 实算 GPA 排名。
     */
    @Override
    public Object getBenchmarkClassRanking(Long studentId, Long classId, String semester) {
        log.info("获取班级排名: userId={}, classId={}, semester={}", studentId, classId, semester);
        Long profileId = resolveProfileId(studentId);
        if (profileId == null) {
            return Collections.emptyMap();
        }
        StudentProfile sp = studentProfileMapper.selectById(profileId);
        if (classId == null && sp != null) {
            classId = sp.getClassId();
        }
        if (classId == null) {
            return Collections.emptyMap();
        }
        List<Map<String, Object>> list = scoreMapper.selectClassGpaRanking(classId);
        return rankingResult(list, profileId, "classId", classId, semester);
    }

    /** 专业排名。详见 {@link #getBenchmarkClassRanking}。 */
    @Override
    public Object getBenchmarkMajorRanking(Long studentId, Long majorId, String semester) {
        log.info("获取专业排名: userId={}, majorId={}, semester={}", studentId, majorId, semester);
        Long profileId = resolveProfileId(studentId);
        if (profileId == null) {
            return Collections.emptyMap();
        }
        StudentProfile sp = studentProfileMapper.selectById(profileId);
        if (majorId == null && sp != null) {
            majorId = sp.getMajorId();
        }
        if (majorId == null) {
            return Collections.emptyMap();
        }
        List<Map<String, Object>> list = scoreMapper.selectMajorGpaRanking(majorId);
        return rankingResult(list, profileId, "majorId", majorId, semester);
    }

    /**
     * 把排行榜列表包成统一响应，并算出该生的名次。
     * 空列表时 rank 为 0（而非 1），避免出现"0 人中排第 1"。
     */
    private Map<String, Object> rankingResult(List<Map<String, Object>> list, Long profileId,
                                              String scopeKey, Long scopeId, String semester) {
        int rank = 0;
        for (int i = 0; i < list.size(); i++) {
            Object id = list.get(i).get("profileId");
            if (id != null && id.toString().equals(profileId.toString())) {
                rank = i + 1;
                break;
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("studentId", profileId);
        result.put(scopeKey, scopeId);
        result.put("semester", semester);
        result.put("rank", rank);
        result.put("total", list.size());
        result.put("list", list);
        return result;
    }

    /**
     * 班级 GPA 对标。
     *
     * <p>原实现读 {@code benchmark_analysis} 快照，而该表的 {@code class_avg_gpa} 与
     * {@code class_total} 是种子数据里写死的常量（每班都一样），所以"班级平均 GPA"
     * 无论哪个班、哪个学期都返回同一个数。改为按 {@link ScoreCaliber} 实算。
     *
     * <p>{@code semester} 参数目前不参与计算：GPA 排行按"每门课最新一次有效成绩"
     * 统计（见 {@code LATEST_VALID_SCORE}），是全学程口径。保留参数以兼容已有调用方。
     */
    @Override
    public Object getClassBenchmarkComparison(Long classId, String semester) {
        log.info("获取班级对标对比: classId={}, semester={}", classId, semester);
        return comparisonResult("classId", classId, semester,
                classId == null ? Collections.emptyList() : scoreMapper.selectClassGpaRanking(classId));
    }

    /** 专业 GPA 对标。口径同 {@link #getClassBenchmarkComparison}。 */
    @Override
    public Object getMajorBenchmarkComparison(Long majorId, String semester) {
        log.info("获取专业对标对比: majorId={}, semester={}", majorId, semester);
        return comparisonResult("majorId", majorId, semester,
                majorId == null ? Collections.emptyList() : scoreMapper.selectMajorGpaRanking(majorId));
    }

    /** 把 GPA 排行包成对标响应，平均分直接由排行列表求得，保证两者同源。 */
    private Map<String, Object> comparisonResult(String scopeKey, Long scopeId, String semester,
                                                 List<Map<String, Object>> ranking) {
        double sum = 0;
        int counted = 0;
        for (Map<String, Object> row : ranking) {
            Double gpa = ScoreCaliber.toDouble(row.get("gpa"));
            if (gpa != null) {
                sum += gpa;
                counted++;
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put(scopeKey, scopeId);
        result.put("semester", semester);
        result.put("averageGpa", counted == 0 ? null
                : BigDecimal.valueOf(sum / counted).setScale(3, RoundingMode.HALF_UP));
        result.put("count", ranking.size());
        result.put("list", ranking);
        return result;
    }

    /**
     * 毕业进度报告。
     *
     * <p>原实现有三处错：毕业总学分写死 {@code 40}（与仪表盘的 120、
     * {@code benchmark_analysis.required_credits} 的 20 互不相同）；入参 userId 被当学号
     * 查询，永远查不到人；且用<b>课程门数</b>充当<b>学分</b>——一门 1 学分的选修
     * 与一门 5 学分的专业课在这里等值。
     *
     * <p>现在：已修学分只累加及格课程的 {@code credits}，总学分取
     * {@link ScoreCaliber#GRADUATION_CREDITS}，逐学期明细同样按学分统计。
     */
    @Override
    public Object getProgressReport(Long studentId) {
        log.info("获取进度报告: userId={}", studentId);
        Long profileId = resolveProfileId(studentId);
        if (profileId == null) {
            return Collections.emptyMap();
        }
        StudentProfile sp = studentProfileMapper.selectById(profileId);
        String studentNo = sp != null ? sp.getStudentId() : null;

        Map<String, Object> stats = scoreMapper.selectOneStudentStats(profileId);
        int totalCredits = ScoreCaliber.GRADUATION_CREDITS;
        int completedCredits = stats != null ? intValue(stats.get("completedCredits")) : 0;

        // 每门课一条（已按最新一次有效成绩去重），含学分与所在学期
        List<Map<String, Object>> courses = scoreMapper.selectLatestScoresByCourse(profileId);

        // 逐学期：该学期开课的学分 vs 其中已通过的学分。用 TreeMap 保证按学期号有序。
        Map<Integer, int[]> bySemester = new TreeMap<>(Comparator.reverseOrder());
        for (Map<String, Object> c : courses) {
            Integer semester = ScoreCaliber.toDouble(c.get("semester")) == null ? null
                    : ScoreCaliber.toDouble(c.get("semester")).intValue();
            if (semester == null) {
                continue;
            }
            Double credits = ScoreCaliber.toDouble(c.get("credits"));
            Double score = ScoreCaliber.toDouble(c.get("scoreTotal"));
            int[] acc = bySemester.computeIfAbsent(semester, k -> new int[2]);
            if (credits != null) {
                acc[1] += credits.intValue();
                if (score != null && score >= ScoreCaliber.PASS_LINE) {
                    acc[0] += credits.intValue();
                }
            }
        }
        List<Map<String, Object>> semesters = new ArrayList<>();
        for (Map.Entry<Integer, int[]> e : bySemester.entrySet()) {
            int earned = e.getValue()[0];
            int offered = e.getValue()[1];
            Map<String, Object> si = new HashMap<>();
            si.put("semester", semesterLabel(e.getKey(), studentNo));
            si.put("semesterIndex", e.getKey());
            si.put("completedCredits", earned);
            si.put("requiredCredits", offered);
            si.put("completionRate", offered == 0 ? 0
                    : BigDecimal.valueOf((double) earned / offered * 100)
                            .setScale(1, RoundingMode.HALF_UP).doubleValue());
            semesters.add(si);
        }

        double rate = totalCredits > 0 ? (double) completedCredits / totalCredits * 100 : 0;
        Map<String, Object> pr = new HashMap<>();
        pr.put("studentId", profileId);
        pr.put("totalCredits", totalCredits);
        pr.put("completedCredits", completedCredits);
        pr.put("remainingCredits", Math.max(0, totalCredits - completedCredits));
        pr.put("completionRate", BigDecimal.valueOf(rate).setScale(1, RoundingMode.HALF_UP));
        pr.put("gpa", stats == null || stats.get("gpa") == null ? null
                : BigDecimal.valueOf(doubleValue(stats.get("gpa"))).setScale(2, RoundingMode.HALF_UP));
        pr.put("semesters", semesters);
        return pr;
    }

    // ==================== 班级信息 ====================
    /**
     * 班级信息。
     *
     * <p>原实现两个问题：入参 userId 被当成学号查（{@code selectByStudentId} 查的是
     * 学号列），查不到就返回空 Map，前端于是永远显示兜底文案；{@code rankInClass}
     * 写死 {@code "1/45"}，<b>每名学生看到的都是"班级第 1 名 / 共 45 人"</b>。
     */
    @Override
    public Object getClassInfo(Long studentId) {
        log.info("获取班级信息: userId={}", studentId);
        Long profileId = resolveProfileId(studentId);
        if (profileId == null) {
            return Collections.emptyMap();
        }
        StudentProfile sp = studentProfileMapper.selectById(profileId);
        if (sp == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> ci = new HashMap<>();
        ci.put("classIdentifier", sp.getClassName() != null ? sp.getClassName() : "未知班级");
        ci.put("classId", sp.getClassId());
        ci.put("className", sp.getClassName());
        ci.put("studentNo", sp.getStudentId());
        ci.put("name", sp.getName());
        if (sp.getClassId() != null) {
            Integer rank = scoreMapper.calcClassRank(profileId, sp.getClassId());
            Integer total = scoreMapper.countClassStudents(sp.getClassId());
            ci.put("rank", rank);
            ci.put("totalStudents", total);
            // 分母以实算人数为准；无排名（该生尚无成绩）时给出 0 而非伪造名次
            ci.put("rankInClass", (rank == null || total == null || rank <= 0)
                    ? null : rank + "/" + total);
        }
        return ci;
    }

    // ==================== 帮扶计划 ====================
    /**
     * 学生端帮扶计划列表。
     *
     * <p>原实现根本没读 {@code assistance_plans}：它拿<b>预警条数</b>当计划数，
     * 标题由预警等级拼出来（"2级帮扶计划"这种），状态写死"进行中"、进度写死 50。
     * 于是学生看到的帮扶计划与辅导员实际下发的计划毫无关系，
     * 而且辅导员改了进度学生端也不会变。
     *
     * <p>{@code status} 返回数值（0 未开始 / 1 进行中 / 2 已完成），与库中一致，
     * 小程序端就是按数值判断的；同时给出 {@code statusText} 供直接展示。
     * 表里没有"计划标题"列，故 title 用固定名称、正文放在 content/description。
     */
    @Override
    public Object getAssistancePlans(Long studentId) {
        log.info("获取帮扶计划: userId={}", studentId);
        Long profileId = resolveProfileId(studentId);
        if (profileId == null) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> plans = new ArrayList<>();
        for (AssistancePlan plan : assistancePlanMapper.selectByStudentId(profileId)) {
            Map<String, Object> p = new HashMap<>();
            p.put("id", plan.getId());
            p.put("title", "学业帮扶计划");
            p.put("content", plan.getContent());
            p.put("description", plan.getContent());
            p.put("status", plan.getStatus());
            p.put("statusText", planStatusText(plan.getStatus()));
            p.put("progress", plan.getProgress() != null ? plan.getProgress() : 0);
            p.put("counselorId", plan.getCounselorId());
            p.put("createdAt", plan.getCreatedAt());
            p.put("updatedAt", plan.getUpdatedAt());
            plans.add(p);
        }
        return plans;
    }

    /** 帮扶计划状态码 → 展示文案。未知状态统一按"未开始"展示，不抛出。 */
    private static String planStatusText(Integer status) {
        if (status == null) {
            return "未开始";
        }
        return switch (status) {
            case 1 -> "进行中";
            case 2 -> "已完成";
            default -> "未开始";
        };
    }

    // ==================== 学业建议 ====================
    /**
     * 学业建议。
     *
     * <p>原实现读的是全量成绩明细（含重修记录与期末未录入的占位分），且文案里的
     * 课程名是 {@code "课程" + courseId}（"建议加强课程3的学习"）。改用最新一次
     * 有效成绩与真实课程名；关注线用 {@link ScoreCaliber#ATTENTION_LINE}。
     */
    @Override
    public Object getSuggestions(Long studentId) {
        log.info("获取学业建议: userId={}", studentId);
        Long profileId = resolveProfileId(studentId);
        if (profileId == null) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> suggestions = new ArrayList<>();
        long id = 1;
        for (Map<String, Object> course : scoreMapper.selectLatestScoresByCourse(profileId)) {
            Double score = ScoreCaliber.toDouble(course.get("scoreTotal"));
            if (score == null) {
                continue;
            }
            Object name = course.get("courseName");
            String courseName = name != null ? name.toString() : "课程" + course.get("courseId");
            String priority;
            String content;
            if (score < ScoreCaliber.PASS_LINE) {
                priority = "high";
                content = "「" + courseName + "」当前 " + trimScore(score)
                        + " 分，已低于及格线，建议优先安排补考复习";
            } else if (score < ScoreCaliber.ATTENTION_LINE) {
                priority = "medium";
                content = "「" + courseName + "」当前 " + trimScore(score)
                        + " 分，接近及格线，建议适当增加学习时间";
            } else {
                continue;
            }
            Map<String, Object> sug = new HashMap<>();
            sug.put("id", id++);
            sug.put("courseId", course.get("courseId"));
            sug.put("courseName", courseName);
            sug.put("score", score);
            sug.put("content", content);
            sug.put("priority", priority);
            suggestions.add(sug);
        }
        if (suggestions.isEmpty()) {
            Map<String, Object> sug = new HashMap<>();
            sug.put("id", id);
            sug.put("content", "当前各科成绩均在关注线以上，建议参加更多实践项目提升动手能力");
            sug.put("priority", "low");
            suggestions.add(sug);
        }
        return suggestions;
    }

    /** 分数去掉多余的小数位：75.0 → "75"，56.5 → "56.5"。仅用于文案。 */
    private static String trimScore(double score) {
        return score == Math.floor(score) ? String.valueOf((long) score) : String.valueOf(score);
    }

    // ==================== 申诉 ====================
    @Override
    public Object submitAppeal(Object request) {
        log.info("提交成绩申诉: {}", request);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> req = (Map<String, Object>) request;
            ScoreAppeal appeal = new ScoreAppeal();
            // 前端传来的是 userId，通过 StudentProfile 查找真实 studentId
            Long uid = req.containsKey("studentId") ? Long.valueOf(req.get("studentId").toString()) : null;
            if (uid != null) {
                StudentProfile sp = studentProfileMapper.selectByUserId(uid);
                if (sp != null) {
                    appeal.setStudentId(sp.getId());
                } else {
                    return Map.of("error", "学生档案不存在");
                }
            }
            if (req.containsKey("scoreId")) appeal.setScoreId(Long.valueOf(req.get("scoreId").toString()));
            if (req.containsKey("reason")) appeal.setReason(req.get("reason").toString());
            if (req.containsKey("courseId")) appeal.setCourseId(Long.valueOf(req.get("courseId").toString()));
            if (req.containsKey("attachments")) appeal.setAttachments(req.get("attachments").toString());
            appeal.setStatus(0); // pending
            appeal.setCreatedAt(java.time.LocalDateTime.now());
            appeal.setUpdatedAt(java.time.LocalDateTime.now());
            scoreAppealMapper.insert(appeal);
            Map<String, Object> resp = new HashMap<>();
            resp.put("id", appeal.getId());
            resp.put("message", "申诉提交成功");
            return resp;
        } catch (Exception e) {
            log.error("提交申诉失败: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    @Override
    public Object getStudentAppeals(Long studentId) {
        log.info("获取学生申诉列表: studentId={}", studentId);
        // studentId 参数实际是 userId，通过 StudentProfile 查找真实 studentId
        StudentProfile sp = studentProfileMapper.selectByUserId(studentId);
        if (sp == null) return List.of();
        List<ScoreAppeal> list = scoreAppealMapper.selectByStudentId(sp.getId());
        return list.stream().map(this::appealToMap).toList();
    }

    @Override
    public Object getAppealDetail(Long appealId) {
        log.info("获取申诉详情: appealId={}", appealId);
        ScoreAppeal a = scoreAppealMapper.selectById(appealId);
        return a != null ? appealToMap(a) : Map.of("error", "申诉不存在");
    }

    @Override
    public Object withdrawAppeal(Long appealId, Long userId) {
        log.info("撤销申诉: appealId={}, userId={}", appealId, userId);
        Long sid = resolveStudentProfileId(userId);
        if (sid == null) return Map.of("success", false, "message", "学生档案不存在");
        int rows = scoreAppealMapper.withdrawAppeal(appealId, sid);
        return Map.of("success", rows > 0, "message", rows > 0 ? "撤销成功" : "撤销失败");
    }

    @Override
    public Object getPendingAppeals(Long userId) {
        log.info("获取待处理申诉: userId={}", userId);
        Long sid = resolveStudentProfileId(userId);
        if (sid == null) return List.of();
        List<ScoreAppeal> list = scoreAppealMapper.selectPendingByStudentId(sid);
        return list.stream().map(this::appealToMap).toList();
    }

    @Override
    public Object getAppealStatistics(Long userId) {
        log.info("获取申诉统计: userId={}", userId);
        Long sid = resolveStudentProfileId(userId);
        if (sid == null) return Map.of("total", 0, "pending", 0, "approved", 0, "rejected", 0);
        int total = scoreAppealMapper.countByStudentId(sid);
        int pending = scoreAppealMapper.selectPendingByStudentId(sid).size();
        int success = scoreAppealMapper.countSuccessByStudentId(sid);
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("pending", pending);
        stats.put("approved", success);
        stats.put("rejected", total - pending - success);
        return stats;
    }

    @Override
    public Object getAppealSuccessRate(Long userId) {
        log.info("获取申诉成功率: userId={}", userId);
        Long sid = resolveStudentProfileId(userId);
        if (sid == null) { Map<String, Object> r = new HashMap<>(); r.put("rate", 0); r.put("total", 0); r.put("success", 0); return r; }
        int total = scoreAppealMapper.countByStudentId(sid);
        int success = scoreAppealMapper.countSuccessByStudentId(sid);
        double rate = total > 0 ? (double) success / total * 100 : 0;
        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("success", success);
        result.put("rate", BigDecimal.valueOf(rate).setScale(1, RoundingMode.HALF_UP));
        return result;
    }

    @Override
    public Object getAppealsByStatus(Long userId, Integer status) {
        log.info("按状态获取申诉: userId={}, status={}", userId, status);
        Long sid = resolveStudentProfileId(userId);
        if (sid == null) return List.of();
        List<ScoreAppeal> list = scoreAppealMapper.selectByStudentIdAndStatus(sid, status);
        return list.stream().map(this::appealToMap).toList();
    }

    @Override
    public Object getAppealHistory(Long userId, int page, int pageSize) {
        log.info("获取申诉历史: userId={}, page={}, pageSize={}", userId, page, pageSize);
        Long sid = resolveStudentProfileId(userId);
        if (sid == null) { Map<String, Object> r = new HashMap<>(); r.put("total", 0); r.put("list", List.of()); return r; }
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<ScoreAppeal> p = 
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, pageSize);
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ScoreAppeal>()
            .eq(ScoreAppeal::getStudentId, sid)
            .orderByDesc(ScoreAppeal::getCreatedAt);
        var ip = scoreAppealMapper.selectPage(p, wrapper);
        Map<String, Object> result = new HashMap<>();
        result.put("total", ip.getTotal());
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("list", ip.getRecords().stream().map(this::appealToMap).toList());
        return result;
    }

    @Override
    public Object getAppealReasonStatistics(Long userId) {
        log.info("获取申诉原因统计: userId={}", userId);
        Long sid = resolveStudentProfileId(userId);
        if (sid == null) return List.of();
        List<Map<String, Object>> rawList = scoreAppealMapper.countByReason(sid);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rawList) {
            Map<String, Object> item = new HashMap<>();
            item.put("reason", row.get("reason"));
            item.put("count", row.get("cnt"));
            result.add(item);
        }
        return result;
    }

    // ==================== 通知中心 ====================
    @Override
    public Object getNotificationCenterList(Long userId, int page, int pageSize) {
        log.info("获取通知列表: userId={}, page={}, pageSize={}", userId, page, pageSize);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Notification> p =
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, pageSize);
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Notification>()
            .eq(Notification::getUserId, userId)
            .eq(Notification::getIsDeleted, 0)
            .orderByDesc(Notification::getCreatedAt);
        var ip = notificationMapper.selectPage(p, wrapper);
        Map<String, Object> result = new HashMap<>();
        result.put("total", ip.getTotal());
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("list", ip.getRecords().stream().map(this::notificationToMap).toList());
        return result;
    }

    @Override
    public Object getNotificationUnreadCount(Long userId) {
        log.info("获取未读通知数量: userId={}", userId);
        int count = notificationMapper.countUnreadByUserId(userId);
        return Map.of("unreadCount", count);
    }

    @Override
    public Object markNotificationsBatchRead(Long userId, List<Long> notificationIds) {
        log.info("批量标记通知已读: userId={}, ids={}", userId, notificationIds);
        String ids = notificationIds.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
        if (ids.isEmpty()) return Map.of("success", false, "message", "无通知ID");
        int rows = notificationMapper.markBatchRead(userId, ids);
        return Map.of("success", rows > 0, "affected", rows);
    }

    @Override
    public Object deleteNotification(Long notificationId) {
        log.info("删除通知: notificationId={}", notificationId);
        int rows = notificationMapper.softDelete(notificationId);
        return Map.of("success", rows > 0, "message", rows > 0 ? "删除成功" : "删除失败");
    }

    @Override
    public Object clearReadNotifications(Long userId) {
        log.info("清除已读通知: userId={}", userId);
        int rows = notificationMapper.clearReadNotifications(userId);
        return Map.of("success", true, "cleared", rows);
    }

    @Override
    public Object getUnreadNotifications(Long userId) {
        log.info("获取未读通知: userId={}", userId);
        List<Notification> list = notificationMapper.selectUnreadByUserId(userId);
        return list.stream().map(this::notificationToMap).toList();
    }

    // ==================== 订阅管理 ====================
    @Override
    public Object getSubscriptionPreferences(Long studentId) {
        log.info("获取订阅偏好: studentId={}", studentId);
        SubscriptionPreference sp = subscriptionPreferenceMapper.selectByStudentId(studentId);
        if (sp == null) {
            // 创建默认偏好
            subscriptionPreferenceMapper.insertDefault(studentId);
            sp = subscriptionPreferenceMapper.selectByStudentId(studentId);
        }
        return subToMap(sp);
    }

    @Override
    public Object updateSubscriptionPreferences(Long studentId, Object preferences) {
        log.info("更新订阅偏好: studentId={}", studentId);
        @SuppressWarnings("unchecked")
        Map<String, Object> pref = (Map<String, Object>) preferences;
        // 使用 MyBatis-Plus 的 updateById
        SubscriptionPreference sp = subscriptionPreferenceMapper.selectByStudentId(studentId);
        if (sp == null) {
            subscriptionPreferenceMapper.insertDefault(studentId);
            sp = subscriptionPreferenceMapper.selectByStudentId(studentId);
        }
        if (sp != null) {
            if (pref.containsKey("subscribeWarnings")) sp.setSubscribeWarnings((Integer)pref.get("subscribeWarnings"));
            if (pref.containsKey("subscribeLow")) sp.setSubscribeLow((Integer)pref.get("subscribeLow"));
            if (pref.containsKey("subscribeMedium")) sp.setSubscribeMedium((Integer)pref.get("subscribeMedium"));
            if (pref.containsKey("subscribeHigh")) sp.setSubscribeHigh((Integer)pref.get("subscribeHigh"));
            if (pref.containsKey("subscribeAssistance")) sp.setSubscribeAssistance((Integer)pref.get("subscribeAssistance"));
            if (pref.containsKey("subscribeSystem")) sp.setSubscribeSystem((Integer)pref.get("subscribeSystem"));
            if (pref.containsKey("pushEmail")) sp.setPushEmail((Integer)pref.get("pushEmail"));
            if (pref.containsKey("pushSms")) sp.setPushSms((Integer)pref.get("pushSms"));
            if (pref.containsKey("pushApp")) sp.setPushApp((Integer)pref.get("pushApp"));
            sp.setUpdatedAt(java.time.LocalDateTime.now());
            subscriptionPreferenceMapper.updateById(sp);
        }
        return Map.of("success", true);
    }

    @Override
    public Object subscribeWarningLevel(Long studentId, String level) {
        log.info("订阅预警等级: studentId={}, level={}", studentId, level);
        String column = switch (level.toLowerCase()) {
            case "low" -> "subscribe_low";
            case "medium" -> "subscribe_medium";
            case "high" -> "subscribe_high";
            default -> "";
        };
        if (column.isEmpty()) return Map.of("success", false, "message", "无效的预警等级");
        ensureSubscriptionExists(studentId);
        subscriptionPreferenceMapper.updateField(studentId, column, 1);
        return Map.of("success", true);
    }

    @Override
    public Object unsubscribeWarningLevel(Long studentId, String level) {
        log.info("取消订阅预警等级: studentId={}, level={}", studentId, level);
        String column = switch (level.toLowerCase()) {
            case "low" -> "subscribe_low";
            case "medium" -> "subscribe_medium";
            case "high" -> "subscribe_high";
            default -> "";
        };
        if (column.isEmpty()) return Map.of("success", false, "message", "无效的预警等级");
        ensureSubscriptionExists(studentId);
        subscriptionPreferenceMapper.updateField(studentId, column, 0);
        return Map.of("success", true);
    }

    @Override
    public Object setPushChannel(Long studentId, String channel, Boolean enabled) {
        log.info("设置推送渠道: studentId={}, channel={}, enabled={}", studentId, channel, enabled);
        String column = switch (channel.toLowerCase()) {
            case "email" -> "push_email";
            case "sms" -> "push_sms";
            case "app" -> "push_app";
            default -> "";
        };
        if (column.isEmpty()) return Map.of("success", false, "message", "无效的推送渠道");
        ensureSubscriptionExists(studentId);
        subscriptionPreferenceMapper.updateField(studentId, column, enabled ? 1 : 0);
        return Map.of("success", true);
    }

    // ==================== 帮扶反馈 ====================
    @Override
    public Object submitEvaluation(Object evaluation) {
        log.info("提交帮扶评价: {}", evaluation);
        @SuppressWarnings("unchecked")
        Map<String, Object> req = (Map<String, Object>) evaluation;
        AssistanceEvaluation ae = new AssistanceEvaluation();
        if (req.containsKey("planId")) ae.setPlanId(Long.valueOf(req.get("planId").toString()));
        if (req.containsKey("counselorId")) ae.setCounselorId(Long.valueOf(req.get("counselorId").toString()));
        if (req.containsKey("content")) ae.setContent(req.get("content").toString());
        if (req.containsKey("score")) ae.setScore(Integer.valueOf(req.get("score").toString()));
        ae.setCreatedAt(java.time.LocalDateTime.now());
        ae.setUpdatedAt(java.time.LocalDateTime.now());
        assistanceEvaluationMapper.insert(ae);
        return Map.of("success", true, "id", ae.getId(), "message", "评价提交成功");
    }

    @Override
    public Object getEvaluationsByStudent(Long studentId) {
        log.info("获取学生评价列表: studentId={}", studentId);
        List<AssistanceEvaluation> list = assistanceEvaluationMapper.selectByStudentId(studentId);
        return list.stream().map(this::evaluationToMap).toList();
    }

    @Override
    public Object getPlanEvaluation(Long planId) {
        log.info("获取帮扶计划评价: planId={}", planId);
        List<AssistanceEvaluation> list = assistanceEvaluationMapper.selectByPlanId(planId);
        if (list.isEmpty()) return Map.of("error", "无评价数据");
        return list.stream().map(this::evaluationToMap).toList();
    }

    @Override
    public Object getEvaluationStatistics(Long studentId) {
        log.info("获取评价统计: studentId={}", studentId);
        Double avg = assistanceEvaluationMapper.avgScoreByStudentId(studentId);
        int count = assistanceEvaluationMapper.countByStudentId(studentId);
        Map<String, Object> stats = new HashMap<>();
        stats.put("averageScore", avg != null ? BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP) : 0);
        stats.put("totalCount", count);
        return stats;
    }

    // ==================== 设置 ====================
    @Override
    public Object updatePrivacyLevel(Long studentId, Integer privacyLevel) {
        log.info("更新隐私级别: studentId={}, privacyLevel={}", studentId, privacyLevel);
        StudentProfile sp = studentProfileMapper.selectByStudentId(studentId.toString());
        if (sp == null) return Map.of("success", false, "message", "学生不存在");
        sp.setPrivacyLevel(privacyLevel);
        sp.setUpdatedAt(java.time.LocalDateTime.now());
        int rows = studentProfileMapper.updateById(sp);
        return Map.of("success", rows > 0);
    }

    @Override
    public Object getStudentSettings(Long studentId) {
        log.info("获取学生设置: studentId={}", studentId);
        StudentProfile sp = studentProfileMapper.selectByStudentId(studentId.toString());
        Map<String, Object> settings = new HashMap<>();
        settings.put("studentId", studentId);
        settings.put("notificationSettings", Map.of("emailNotifications", true, "smsNotifications", false, "appNotifications", true));
        settings.put("privacySettings", Map.of("shareGrades", false, "shareAttendance", false, "shareProgress", true));
        settings.put("displaySettings", Map.of("theme", "light", "language", "zh-CN", "timezone", "Asia/Shanghai"));
        if (sp != null) settings.put("privacyLevel", sp.getPrivacyLevel() != null ? sp.getPrivacyLevel() : 2);
        return settings;
    }

    @Override
    public Object getSecurityLogs(Long studentId, int page, int pageSize) {
        log.info("获取安全日志: studentId={}, page={}, pageSize={}", studentId, page, pageSize);
        Map<String, Object> result = new HashMap<>();
        result.put("studentId", studentId);
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("total", 0);
        result.put("logs", Collections.emptyList());
        return result;
    }

    // ==================== 消息 ====================
    @Override
    public Object getStudentMessages(Long studentId) {
        log.info("获取学生消息: studentId={}", studentId);
        // 融合 notifications 表数据
        List<Notification> notifs = notificationMapper.selectByUserId(studentId);
        if (!notifs.isEmpty()) return notifs.stream().map(this::notificationToMap).toList();
        return Collections.emptyList();
    }

    @Override
    public Object getUnreadMessageCount(Long studentId) {
        log.info("获取未读消息数量: studentId={}", studentId);
        int count = notificationMapper.countUnreadByUserId(studentId);
        return Map.of("unreadCount", count);
    }

    // ==================== 导出/下载 ====================
    @Override
    public Object exportScoresToExcel(Long studentId) {
        log.info("导出学生成绩到Excel: studentId={}", studentId);
        StudentProfile sp = studentProfileMapper.selectByStudentId(studentId.toString());
        if (sp == null) return Map.of("message", "学生不存在");
        List<Score> scores = scoreMapper.selectByStudentId(studentId.toString());
        Map<String, Object> result = new HashMap<>();
        result.put("message", "导出成功");
        result.put("studentId", studentId);
        result.put("studentName", sp.getName());
        result.put("scoreCount", scores.size());
        return result;
    }

    @Override
    public Object downloadScores(Long studentId) {
        log.info("下载成绩: studentId={}", studentId);
        return exportScoresToExcel(studentId); // 共用导出逻辑
    }

    // ==================== 学院相关 ====================
    @Override
    public long countByCollege(Long collegeId) {
        return studentProfileMapper.countByCollegeId(collegeId);
    }

    @Override
    public java.util.List<Object> getStudentsByCollege(Long collegeId) {
        List<StudentProfile> profiles = studentProfileMapper.selectByCollegeId(collegeId);
        return new ArrayList<>(profiles);
    }

    // ==================== 私有辅助方法 ====================
    /** 前端传来的是 userId，通过 StudentProfile 获得真正的 studentId */
    private Long resolveStudentProfileId(Long userId) {
        StudentProfile sp = studentProfileMapper.selectByUserId(userId);
        return sp != null ? sp.getId() : null;
    }

    private Map<String, Object> appealToMap(ScoreAppeal a) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", a.getId());
        m.put("studentId", a.getStudentId());
        m.put("scoreId", a.getScoreId());
        m.put("courseId", a.getCourseId());
        m.put("warningId", a.getWarningId());
        m.put("reason", a.getReason());
        m.put("attachments", a.getAttachments());
        m.put("status", a.getStatus());
        m.put("reply", a.getReply());
        m.put("createdAt", a.getCreatedAt() != null ? a.getCreatedAt().toString() : null);
        m.put("updatedAt", a.getUpdatedAt() != null ? a.getUpdatedAt().toString() : null);
        return m;
    }

    private Map<String, Object> notificationToMap(Notification n) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", n.getId());
        m.put("userId", n.getUserId());
        m.put("title", n.getTitle());
        m.put("content", n.getContent());
        m.put("type", n.getType());
        m.put("warningId", n.getWarningId());
        m.put("pushChannel", n.getPushChannel());
        m.put("isRead", n.getIsRead());
        m.put("createdAt", n.getCreatedAt() != null ? n.getCreatedAt().toString() : null);
        return m;
    }

    private Map<String, Object> subToMap(SubscriptionPreference sp) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", sp.getId());
        m.put("studentId", sp.getStudentId());
        m.put("subscribeWarnings", sp.getSubscribeWarnings());
        m.put("subscribeLow", sp.getSubscribeLow());
        m.put("subscribeMedium", sp.getSubscribeMedium());
        m.put("subscribeHigh", sp.getSubscribeHigh());
        m.put("subscribeAssistance", sp.getSubscribeAssistance());
        m.put("subscribeSystem", sp.getSubscribeSystem());
        m.put("pushEmail", sp.getPushEmail());
        m.put("pushSms", sp.getPushSms());
        m.put("pushApp", sp.getPushApp());
        return m;
    }

    private Map<String, Object> evaluationToMap(AssistanceEvaluation ae) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", ae.getId());
        m.put("planId", ae.getPlanId());
        m.put("counselorId", ae.getCounselorId());
        m.put("content", ae.getContent());
        m.put("score", ae.getScore());
        m.put("createdAt", ae.getCreatedAt() != null ? ae.getCreatedAt().toString() : null);
        return m;
    }

    private void ensureSubscriptionExists(Long studentId) {
        SubscriptionPreference sp = subscriptionPreferenceMapper.selectByStudentId(studentId);
        if (sp == null) subscriptionPreferenceMapper.insertDefault(studentId);
    }
}