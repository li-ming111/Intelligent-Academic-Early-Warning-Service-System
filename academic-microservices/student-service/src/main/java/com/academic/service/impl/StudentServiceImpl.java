package com.academic.service.impl;

import com.academic.common.constant.ScoreCaliber;
import com.academic.common.entity.Score;
import com.academic.common.entity.AcademicWarning;
import com.academic.common.entity.AssistancePlan;
import com.academic.common.entity.UserSettings;
import com.academic.dto.StudentDashboardResponse;
import com.academic.entity.*;
import com.academic.mapper.*;
import com.academic.service.StudentService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

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
    private final UserSettingsMapper userSettingsMapper;
    // 预警的「挂科课程明细」（W39）。shared 的接口在 academic-common 的
    // com.academic.mapper 包，本服务的 @MapperScan 已覆盖。
    private final com.academic.mapper.AcademicWarningCourseMapper academicWarningCourseMapper;

    public StudentServiceImpl(StudentProfileMapper studentProfileMapper,
                              ScoreMapper scoreMapper,
                              AcademicWarningMapper academicWarningMapper,
                              ScoreAppealMapper scoreAppealMapper,
                              NotificationMapper notificationMapper,
                              SubscriptionPreferenceMapper subscriptionPreferenceMapper,
                              AssistanceEvaluationMapper assistanceEvaluationMapper,
                              AssistancePlanMapper assistancePlanMapper,
                              UserSettingsMapper userSettingsMapper,
                              com.academic.mapper.AcademicWarningCourseMapper academicWarningCourseMapper) {
        this.studentProfileMapper = studentProfileMapper;
        this.scoreMapper = scoreMapper;
        this.academicWarningMapper = academicWarningMapper;
        this.scoreAppealMapper = scoreAppealMapper;
        this.notificationMapper = notificationMapper;
        this.subscriptionPreferenceMapper = subscriptionPreferenceMapper;
        this.assistanceEvaluationMapper = assistanceEvaluationMapper;
        this.assistancePlanMapper = assistancePlanMapper;
        this.academicWarningCourseMapper = academicWarningCourseMapper;
        this.userSettingsMapper = userSettingsMapper;
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

    /**
     * {@inheritDoc}
     *
     * <p>2026-10-03（W10）：把私有的 {@link #resolveProfileId} 暴露给 controller —— 订阅族的
     * 归属校验需要它（路径收 {@code student_profile.id}、身份是 {@code users.id}）。
     */
    @Override
    public Long getProfileIdByUserId(Long userId) {
        return resolveProfileId(userId);
    }

    // ========== 资源归属闸用（W12，2026-10-04）—— 见接口上的注释（三套 id 空间） ==========

    @Override
    public Long getAppealOwnerProfileId(Long appealId) {
        ScoreAppeal appeal = appealId == null ? null : scoreAppealMapper.selectById(appealId);
        return appeal == null ? null : appeal.getStudentId();
    }

    @Override
    public Long getPlanOwnerProfileId(Long planId) {
        AssistancePlan plan = planId == null ? null : assistancePlanMapper.selectById(planId);
        return plan == null ? null : plan.getStudentId();
    }

    @Override
    public Long getNotificationOwnerUserId(Long notificationId) {
        Notification notification = notificationId == null ? null : notificationMapper.selectById(notificationId);
        return notification == null ? null : notification.getUserId();
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
    /**
     * 学生本人的预警记录。
     *
     * <p><b>2026-10-12（W39）：</b>此前直接回 {@code academic_warning} 裸实体，
     * "挂了哪几门课"只能从 {@code description} 正文里读 —— 而 PC 端
     * {@code Warnings.vue} 正是用正则从正文里<b>抠</b>课程名的
     * （{@code extractCourseName}）∶全仓唯一一处前端编造。现在由后端按
     * {@code academic_warning_course} 补两个字段，前端不必再猜：
     * <ul>
     *   <li>{@code courses} —— 每门挂科课程（课程名/分数/学期）；</li>
     *   <li>{@code courseNames} —— 课程名的"、"拼接。</li>
     * </ul>
     * 返回类型由 {@code Object}（实际是 List&lt;AcademicWarning&gt;）改成
     * {@code List<Map<String,Object>>}，因为字段是运行时补的，塞不进那个实体。
     */
    @Override
    public Object getStudentWarnings(Long studentId) {
        log.info("获取学生预警: userId={}", studentId);
        StudentProfile sp = studentProfileMapper.selectByUserId(studentId);
        if (sp == null) return Collections.emptyList();
        List<AcademicWarning> warnings = academicWarningMapper.selectByStudentId(sp.getId().toString());
        if (warnings.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> warningIds = warnings.stream()
                .map(AcademicWarning::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        Map<Long, List<Map<String, Object>>> coursesByWarningId = new HashMap<>();
        if (!warningIds.isEmpty()) {
            for (Map<String, Object> row : academicWarningCourseMapper.selectByWarningIds(warningIds)) {
                Long wid = toLongValue(row.get("warningId"));
                if (wid == null) continue;
                coursesByWarningId.computeIfAbsent(wid, k -> new ArrayList<>()).add(row);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (AcademicWarning w : warnings) {
            List<Map<String, Object>> courses =
                    coursesByWarningId.getOrDefault(w.getId(), Collections.emptyList());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", w.getId());
            m.put("studentId", w.getStudentId());
            m.put("studentName", w.getStudentName());
            m.put("ruleId", w.getRuleId());
            m.put("type", w.getType());
            m.put("title", w.getTitle());
            m.put("description", w.getDescription());
            m.put("warningLevel", w.getWarningLevel());
            m.put("warningMessage", w.getWarningMessage());
            m.put("triggerScore", w.getTriggerScore());
            m.put("failCount", w.getFailCount());
            m.put("status", w.getStatus());
            m.put("appealStatus", w.getAppealStatus());
            m.put("handledBy", w.getHandledBy());
            m.put("handleResult", w.getHandleResult());
            m.put("handledAt", w.getHandledAt());
            m.put("createdAt", w.getCreatedAt());
            m.put("courses", courses);
            m.put("courseNames", courses.stream()
                    .map(c -> c.get("courseName"))
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .collect(Collectors.joining("、")));
            result.add(m);
        }
        return result;
    }

    /** {@code Number}/{@code String} 混着的 mapper 返回值 → {@code Long}；认不出返回 null。 */
    private static Long toLongValue(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ==================== 学生信息 ====================
    @Override
    public Object getStudentInfo(Long studentId) {
        log.info("获取学生信息: userId={}", studentId);
        StudentProfile sp = studentProfileMapper.selectByUserId(studentId);
        if (sp == null) return Map.of("error", "学生不存在");
        return sp;
    }

    // registerStudent 已删（2026-10-08 W27）：原来的实现体是 `log.info("学生注册"); return "注册成功";`
    // —— 回 HTTP 200 + success:true 却一行都不写库（W25 实测：users/student_profile 打前打后一致）。
    // 真实注册走 user-service `/auth/register/student`。

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

            // 2026-10-01 第二十一轮: 前端这张表还要 classAvgGpa / majorAvgGpa /
            // classRank / classTotal / coursesPassed / coursesFailed 六个 key, 而本方法
            // 原来一个都不给 ⇒ 三列恒 `--`、「通过/挂科」恒「0过/0挂」。
            // ⚠️ 不能读 benchmark_analysis: 那张表**有这些列但零行**(无任何写入方)。
            // 所以按 scores 实算, 均值/名次/人数都从同一个"同学期同班 GPA 行集"里取,
            // 保证分子分母同源(此前班级均分与班级排名各算各的就是这么打架的)。
            if (sp != null && sp.getClassId() != null) {
                List<Map<String, Object>> peers = scoreMapper.selectSemesterClassGpas(profileId, semester);
                bm.put("classAvgGpa", avgGpaOf(peers));
                bm.put("classRank", rankInGpas(peers, profileId, gpa));
                bm.put("classTotal", countWithGpa(peers));
            }
            if (sp != null && sp.getMajorId() != null) {
                List<Map<String, Object>> majors = scoreMapper.selectSemesterMajorGpas(profileId, semester);
                bm.put("majorAvgGpa", avgGpaOf(majors));
                bm.put("majorRank", rankInGpas(majors, profileId, gpa));
                bm.put("majorTotal", countWithGpa(majors));
            }
            Map<String, Object> cs = scoreMapper.selectSemesterCourseStats(profileId, semester);
            int totalCourses = cs != null ? intValue(cs.get("totalCourses")) : 0;
            int failedCourses = cs != null ? intValue(cs.get("failedCourses")) : 0;
            bm.put("coursesPassed", totalCourses - failedCourses);
            bm.put("coursesFailed", failedCourses);
            result.add(bm);
        }
        return result;
    }

    /** 一组"学生-GPA"行的平均值（跳过 GPA 为 null 的学生，与 STUDENT_GPA 的 NULL 语义一致）。 */
    private static BigDecimal avgGpaOf(List<Map<String, Object>> rows) {
        List<Double> gpas = gpasOf(rows);
        if (gpas.isEmpty()) {
            return null;
        }
        double sum = 0;
        for (Double g : gpas) {
            sum += g;
        }
        return BigDecimal.valueOf(sum / gpas.size()).setScale(2, RoundingMode.HALF_UP);
    }

    /** 名次 = 比我 GPA 高的人数 + 1；自己不在名单里（该学期无成绩）时返回 null，不伪造名次。 */
    private static Integer rankInGpas(List<Map<String, Object>> rows, Long profileId, Double myGpa) {
        if (myGpa == null) {
            return null;
        }
        int better = 0;
        for (Map<String, Object> row : rows) {
            Double g = gpaOf(row);
            if (g != null && g > myGpa) {
                better++;
            }
        }
        return better + 1;
    }

    /** 参与比较的人数（GPA 非空者）。 */
    private static int countWithGpa(List<Map<String, Object>> rows) {
        return gpasOf(rows).size();
    }

    private static List<Double> gpasOf(List<Map<String, Object>> rows) {
        List<Double> gpas = new ArrayList<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Double g = gpaOf(row);
                if (g != null) {
                    gpas.add(g);
                }
            }
        }
        return gpas;
    }

    private static Double gpaOf(Map<String, Object> row) {
        Object v = row != null ? row.get("gpa") : null;
        return v instanceof Number ? ((Number) v).doubleValue() : null;
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
        // 传了学期 ⇒ 真按学期过滤；没传 ⇒ 维持累计口径（向后兼容，两个前端当前都不传）。
        Integer semIndex = parseSemesterIndex(semester, sp != null ? sp.getStudentId() : null);
        List<Map<String, Object>> list = semIndex == null
                ? scoreMapper.selectClassGpaRanking(classId)
                : scoreMapper.selectSemesterClassGpaRanking(classId, semIndex);
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
        Integer semIndex = parseSemesterIndex(semester, sp != null ? sp.getStudentId() : null);
        List<Map<String, Object>> list = semIndex == null
                ? scoreMapper.selectMajorGpaRanking(majorId)
                : scoreMapper.selectSemesterMajorGpaRanking(majorId, semIndex);
        return rankingResult(list, profileId, "majorId", majorId, semester);
    }

    /**
     * 把排行榜列表包成统一响应，并算出该生的名次。
     * 空列表时 rank 为 0（而非 1），避免出现"0 人中排第 1"。
     *
     * <p>同时执行隐私过滤：排行榜是**同学之间**互相看得见的界面，所以
     * {@code student_profile.privacy_level} 在这里真正生效（此前这一列全仓无人读，
     * 存了等于没存）。规则见 {@link #maskPeerPrivacy}。
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
        maskPeerPrivacy(list, profileId);
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
     * 按 {@code privacy_level} 遮蔽**他人**的成绩明细。
     *
     * <p>{@code privacy_level} 的取值由学生端「设置 → 隐私设置 → 成绩可见范围」定义：
     * {@code 0}=公开 / {@code 1}=仅教师 / {@code 2}=私密。这里处理的是"同学能不能看见"
     * 这一个维度：只有 {@code 0}（公开）才把 GPA 与已计学分给同学看；
     * {@code 1}/{@code 2} 一律遮蔽。
     *
     * <p><b>教师的可见性不受这里影响</b>：教师/辅导员看成绩走的是另外的接口
     * （成绩录入、预警、帮扶），本方法只作用于学生之间互相看的排行榜。
     * 否则「私密」就等于把学生从预警体系里摘出去 —— 那与本系统的目的相悖。
     *
     * <p>三条口径：
     * <ol>
     *   <li><b>自己的那一行永远不遮</b> —— 学生当然看得见自己的成绩；</li>
     *   <li>遮蔽是<b>把值置空 + 打标记</b>，不是删行。删行会让名次和总人数跟着变，
     *       学生会看到"共 8 人"却排出第 12 名；</li>
     *   <li>名次顺序本身仍按真实 GPA 排（{@code ORDER BY gpa DESC}），所以
     *       「我排第几」依然是真的 —— 只是看不到别人具体多少分。</li>
     * </ol>
     *
     * @param list      排行榜行（就地修改）
     * @param profileId 请求者自己的 {@code student_profile.id}
     */
    static void maskPeerPrivacy(List<Map<String, Object>> list, Long profileId) {
        if (list == null) {
            return;
        }
        for (Map<String, Object> row : list) {
            if (row == null) {
                continue;
            }
            Object id = row.get("profileId");
            boolean isSelf = profileId != null && id != null && id.toString().equals(profileId.toString());
            if (isSelf) {
                row.put("gpaHidden", false);
                continue;
            }
            // 认不出来的等级按"未公开"处理：宁可少露一点，也不要因为读不懂而多露
            Integer level = toInteger(row.get("privacyLevel"));
            boolean isPublic = level != null && level == 0;
            if (isPublic) {
                row.put("gpaHidden", false);
                continue;
            }
            row.put("gpa", null);
            row.put("gradedCredits", null);
            row.put("gpaHidden", true);
        }
    }


    /**
     * 班级 GPA 对标。
     *
     * <p>原实现读 {@code benchmark_analysis} 快照，而该表的 {@code class_avg_gpa} 与
     * {@code class_total} 是种子数据里写死的常量（每班都一样），所以"班级平均 GPA"
     * 无论哪个班、哪个学期都返回同一个数。改为按 {@link ScoreCaliber} 实算。
     *
     * <p><b>2026-10-10（W38）</b>：{@code semester} 现在**真的参与计算**。
     * 传了学期（数字序号或 {@code "2025-2026-1"} 这类学年学期串）就走
     * {@link com.academic.common.constant.ScoreCaliber#SEMESTER_STUDENT_GPA} 按学期过滤的排行；
     * 不传则回退到全学程口径 {@code LATEST_VALID_SCORE}（"每门课最新一次有效成绩"）。
     * 此前这条注释写的是"参数目前不参与计算"——那是 W38 之前的事实，已过期。
     */
    @Override
    public Object getClassBenchmarkComparison(Long classId, String semester) {
        log.info("获取班级对标对比: classId={}, semester={}", classId, semester);
        if (classId == null) {
            return comparisonResult("classId", null, semester, Collections.emptyList());
        }
        // 这两个端点没有 studentId，学期只能用数字序号（getComparison 的前端调用当前都不传 semester）。
        Integer semIndex = parseSemesterIndex(semester, null);
        List<Map<String, Object>> ranking = semIndex == null
                ? scoreMapper.selectClassGpaRanking(classId)
                : scoreMapper.selectSemesterClassGpaRanking(classId, semIndex);
        return comparisonResult("classId", classId, semester, ranking);
    }

    /** 专业 GPA 对标。口径同 {@link #getClassBenchmarkComparison}。 */
    @Override
    public Object getMajorBenchmarkComparison(Long majorId, String semester) {
        log.info("获取专业对标对比: majorId={}, semester={}", majorId, semester);
        if (majorId == null) {
            return comparisonResult("majorId", null, semester, Collections.emptyList());
        }
        Integer semIndex = parseSemesterIndex(semester, null);
        List<Map<String, Object>> ranking = semIndex == null
                ? scoreMapper.selectMajorGpaRanking(majorId)
                : scoreMapper.selectSemesterMajorGpaRanking(majorId, semIndex);
        return comparisonResult("majorId", majorId, semester, ranking);
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
     *
     * <p>⚠️ 2026-10-01 第二十一轮：表 {@code assistance_plans} 的列只有
     * {@code id/counselor_id/student_id/content/status/created_at/updated_at/progress} ——
     * **没有 course_id、没有 start_date/end_date**。所以"目标课程/计划开始/计划结束"这三项
     * 在库层面**没有数据源**，学生端页面对应三块已删（不是补一个编出来的值）。
     * 「辅导教师」补的是 {@code counselorName}（{@code counselor_id} = users.id）。
     */
    @Override
    public Object getAssistancePlans(Long studentId) {
        log.info("获取帮扶计划: userId={}", studentId);
        Long profileId = resolveProfileId(studentId);
        if (profileId == null) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> plans = new ArrayList<>();
        // 2026-10-01 第二十一轮: 前端要显示「辅导教师」, 原来那个 key 后端从来不 put。
        // 姓名在 users 表(counselor_id 就是 users.id), 按 id 缓存避免每行一次查询。
        Map<Long, String> nameCache = new HashMap<>();
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
            if (plan.getCounselorId() != null) {
                p.put("counselorName", nameCache.computeIfAbsent(plan.getCounselorId(),
                        assistancePlanMapper::selectCounselorName));
            }
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

    // ==================== 帮扶进度（学生侧） ====================
    /**
     * 学生更新自己帮扶计划的进度 + 完成情况说明。
     *
     * <p>2026-10-02 新增。此前 PC 学生端的「更新进度」打的是
     * {@code /counselors/assistance-plans/{id}/progress} —— 网关
     * {@code AuthenticationFilter} 对 role≠3 一律 403，**学生点一次失败一次**；
     * 而弹窗里那个**必填**的「完成情况」文本框前端压根没发出来。
     *
     * <p>身份取网关注入的 {@code X-User-Id}（{@code GatewayAuthFilter} 已写进
     * SecurityContext 的 principal），**不信任请求体里的任何 id**；归属校验落在
     * SQL 的 {@code WHERE student_id = ?} 上（改别人的计划影响行数为 0）。
     *
     * @param planId  {@code assistance_plans.id}
     * @param request body：{@code progress}（0-100，必填）、{@code note}（完成情况，可选）
     */
    @Override
    public Object updatePlanProgress(Long planId, Object request) {
        Map<String, Object> body = request instanceof Map
                ? (Map<String, Object>) request
                : Collections.emptyMap();
        Integer progress = intValueOrNull(body.get("progress"));
        if (progress == null) {
            throw new IllegalArgumentException("progress 必填，且需为 0-100 的整数");
        }
        if (progress < 0 || progress > 100) {
            throw new IllegalArgumentException("progress 必须在 0-100 之间，收到: " + progress);
        }
        String note = body.get("note") == null ? null : String.valueOf(body.get("note")).trim();
        if (note != null && note.isEmpty()) {
            note = null;
        }

        Long profileId = resolveProfileId(currentUserId());
        if (profileId == null) {
            throw new IllegalArgumentException("无法定位当前登录学生的档案");
        }

        // 进度顺带同步 status：0→待开始(0)、100→已完成(2)、其余→进行中(1)。
        // 与 counselor-service 的 AssistancePlanServiceImpl.updatePlanProgress 口径一致。
        int status = progress == 0 ? 0 : (progress >= 100 ? 2 : 1);

        int rows = assistancePlanMapper.updateProgress(planId, profileId, progress, status, note);
        if (rows == 0) {
            throw new IllegalArgumentException("帮扶计划不存在或不属于当前用户: " + planId);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("id", planId);
        result.put("progress", progress);
        result.put("status", status);
        result.put("progressNote", note);
        return result;
    }

    /** 取网关注入的当前登录用户 id（users.id）。直连无认证头时 principal 为 null。 */
    private Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(auth.getPrincipal()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 与 {@link #intValue} 同源，但**保留 null**（无法识别与"0"要区分得开）。 */
    private static Integer intValueOrNull(Object value) {
        Double d = ScoreCaliber.toDouble(value);
        return d == null ? null : d.intValue();
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
            // 2026-10-01 第二十一轮: 兜底这一条原来只 put id/content/priority, 而学生端页面
            // 是无条件渲染 `s.courseName` 与 `<score-cell :score="s.score">` 的 ⇒ 这一行会
            // 显示空白课程名 + 一个 undefined 的分数格。这里把 key 补齐为 null(不是编一个课程名),
            // 页面按 v-if 守卫, 只显示正文。
            Map<String, Object> sug = new HashMap<>();
            sug.put("id", id);
            sug.put("courseId", null);
            sug.put("courseName", null);
            sug.put("score", null);
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
    /** 提交体认得的 key。其余一律**记日志**，不再一声不响地丢（见下面 submitAppeal 的注释）。 */
    private static final java.util.Set<String> APPEAL_KEYS = java.util.Set.of(
            "studentId", "scoreId", "reason", "courseId", "attachments", "description",
            // PC 端 AppealManagement.vue 的「关联预警」发的是 warningId，
            // score_appeals 有 warning_id 列 —— 此前它同样被静默丢弃（该页那个输入框填了等于没填）
            "warningId");

    /**
     * 提交成绩申诉。
     *
     * <p>⚠️ 2026-09-30 修：此前这里只读 {@code studentId/scoreId/reason/courseId/attachments}，
     * 而 {@code request} 是 {@code Map}，多出来的 key 被**静默丢弃**。偏偏 PC 端两个页面
     * （{@code Appeals.vue:196}、{@code Scores.vue:501}）的"请详细描述申诉理由"都是**必填**，
     * 填了不让空着交，交上去却从来没进过库 —— 学生写了正文，库里只剩一个原因代码。
     * 现在 {@code description} 有对应的列（{@code score_appeals.description}，
     * 见 db_backups/appeal_description_20260930_apply.sql），真写进去。
     *
     * <p>同一批还接上了 {@code warningId}（{@code score_appeals.warning_id}）——
     * {@code AppealManagement.vue} 的「关联预警」输入框一直发这个键，也一直被丢掉。
     *
     * <p>剩下没认出来的 key 仍然**不拒绝**（拒绝会打断现有页面），但会打 WARN 把 key 名列出来，
     * 所以"提交体被吞"这件事至少是可查的，不再是无声的。
     */
    @Override
    public Object submitAppeal(Object request) {
        log.info("提交成绩申诉: {}", request);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> req = (Map<String, Object>) request;
            java.util.List<String> ignored = req.keySet().stream()
                    .filter(k -> !APPEAL_KEYS.contains(k)).sorted().toList();
            if (!ignored.isEmpty()) {
                log.warn("提交申诉时忽略了不认识的字段（未落库）: keys={}", ignored);
            }
            ScoreAppeal appeal = new ScoreAppeal();
            // 前端传来的是 userId，通过 StudentProfile 查找真实 studentId
            Long uid = req.containsKey("studentId") ? Long.valueOf(req.get("studentId").toString()) : null;
            if (uid == null) {
                // 从前没这条判断：studentId 缺失时会带着 null 的 student_id 去 insert，
                // 撞 NOT NULL 外键后由 catch 兜住，返回的是一句 JDBC 报错
                return Map.of("error", "缺少 studentId");
            }
            StudentProfile sp = studentProfileMapper.selectByUserId(uid);
            if (sp == null) {
                return Map.of("error", "学生档案不存在");
            }
            appeal.setStudentId(sp.getId());
            if (req.containsKey("reason")) appeal.setReason(req.get("reason").toString());
            if (req.containsKey("attachments")) appeal.setAttachments(req.get("attachments").toString());
            if (req.containsKey("description")) appeal.setDescription(req.get("description").toString());

            // ===== scoreId → courseId：先取成绩行，再由它定课程 =====
            // 2026-10-03（第四十三轮 W9）：`score_id` 从前只是 parse 后直接 set，**一次都没校验**。
            // 它确实有外键指向 scores.id，但**外键只管"存在"、不管"是不是这个学生的"** ——
            // 学生可以把自己档案挂到**别人的成绩**上，静默写入成功，正好违反
            // verify_invariants.py D 组第 7 项那条不变量（"非空时必须同一学生同一课程"）；
            // `course_id` 更是连外键都没有。这里照下面 warningId 那段的做法显式查一遍。
            // 顺带：成绩行还用来**回填 courseId** —— 选了"哪一次成绩"就等于选了课程，
            // 不该再让调用方传一个可能与它对不上的 courseId。
            Score scoreRow = null;
            if (req.containsKey("scoreId")) {
                Object raw = req.get("scoreId");
                if (raw != null && !"".equals(raw.toString())) {
                    Long sid = asLong(raw);
                    if (sid == null) {
                        return Map.of("error", "scoreId 必须是数字: " + raw);
                    }
                    scoreRow = scoreMapper.selectById(sid);
                    if (scoreRow == null) {
                        return Map.of("error", "成绩不存在: " + sid);
                    }
                    // scores.student_id 存的是 student_profile.id（与 academic_warning 同一套）
                    if (!sp.getId().equals(scoreRow.getStudentId())) {
                        return Map.of("error", "成绩 " + sid + " 不属于该学生，申诉未提交");
                    }
                    appeal.setScoreId(sid);
                }
            }
            Object rawCourseId = req.get("courseId");
            Long reqCourseId = (rawCourseId == null || "".equals(rawCourseId.toString()))
                    ? null : asLong(rawCourseId);
            if (scoreRow != null) {
                if (reqCourseId != null && !reqCourseId.equals(scoreRow.getCourseId())) {
                    return Map.of("error", "申诉的课程与所选成绩不一致（成绩 " + scoreRow.getId()
                            + " 属于课程 " + scoreRow.getCourseId() + "）");
                }
                // 没带 courseId（或带了空串）时，由成绩行回填
                appeal.setCourseId(scoreRow.getCourseId());
            } else if (reqCourseId != null) {
                // 只到课程级的入口（预警页等）仍然照旧
                appeal.setCourseId(reqCourseId);
            }
            // warning_id 这一列**没有外键**（实测 information_schema 里 score_appeals 只挂了
            // score_id→scores.id 和 student_id→student_profile.id），所以随便填一个数都会被静默存下，
            // 事后看库完全看不出这条"关联预警"指向空气。这里显式把它查一遍：
            // 查不到就拒；查到了但不是这个学生的预警（academic_warning.student_id 用的是 profile id），
            // 也拒 —— 否则学生可以把自己档案挂到别人的预警上。
            if (req.containsKey("warningId")) {
                Object raw = req.get("warningId");
                if (raw != null && !"".equals(raw.toString())) {
                    Long wid = asLong(raw);
                    if (wid == null) {
                        return Map.of("error", "warningId 必须是数字: " + raw);
                    }
                    AcademicWarning w = academicWarningMapper.selectById(wid);
                    if (w == null) {
                        return Map.of("error", "预警不存在: " + wid);
                    }
                    if (!sp.getId().equals(w.getStudentId())) {
                        return Map.of("error", "预警 " + wid + " 不属于该学生，申诉未提交");
                    }
                    appeal.setWarningId(wid);
                }
            }
            appeal.setStatus(0); // pending
            appeal.setCreatedAt(java.time.LocalDateTime.now());
            appeal.setUpdatedAt(java.time.LocalDateTime.now());
            scoreAppealMapper.insert(appeal);
            Map<String, Object> resp = new HashMap<>();
            resp.put("id", appeal.getId());
            resp.put("message", "申诉提交成功");
            // 回读，返回的必须是库里的值（尤其是 description 到底写没写进去）
            ScoreAppeal saved = scoreAppealMapper.selectById(appeal.getId());
            if (saved == null) {
                return Map.of("error", "申诉提交失败：写入后回读不到该条记录");
            }
            resp.put("description", saved.getDescription());
            resp.put("warningId", saved.getWarningId());
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
        if (sid == null) return Map.of("total", 0, "pending", 0, "approved", 0, "rejected", 0, "withdrawn", 0);
        int total = scoreAppealMapper.countByStudentId(sid);
        // 2026-10-02（W5.8）：四个状态**各自 COUNT**，不再用
        // `rejected = total - pending - approved` 的减法 —— 那个减法把已撤回(3)
        // 折进了「已驳回」，页面上四个数加起来对不上 total。
        // 现在 pending + approved + rejected + withdrawn == total（status 只有 0..3 四种）。
        int pending = scoreAppealMapper.countByStudentIdAndStatus(sid, 0);
        int approved = scoreAppealMapper.countByStudentIdAndStatus(sid, 1);
        int rejected = scoreAppealMapper.countByStudentIdAndStatus(sid, 2);
        int withdrawn = scoreAppealMapper.countByStudentIdAndStatus(sid, 3);
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("pending", pending);
        stats.put("approved", approved);
        stats.put("rejected", rejected);
        // 新增键（字段只增不减）：已撤回。前端不读也不影响。
        stats.put("withdrawn", withdrawn);
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

    /**
     * 单条通知标记已读。
     *
     * <p>⚠️ 此前这个端点在 controller 里直接 `return Map.of("success", true, "message", "标记已读成功")`，
     * 一行库都不写 —— 表现是"点了已读、退出再进还是未读"，且不报错。
     * 而 {@code NotificationMapper.markAsRead} 早就写好了（`UPDATE ... SET is_read=1 WHERE id = #{id}`），
     * 只是一直没人调用。现在真的调它，并把影响行数如实返回：
     * id 不存在时 `affected=0` / `success=false`，不再假装成功。
     *
     * <p>与同族的 {@code deleteNotification}（走 `softDelete(id)`）一样，**不按 user_id 圈定**；
     * 批量那条 {@code markNotificationsBatchRead} 反而是带 `user_id` 的。
     * 单条这两条路径的越权面是既有状况，本次只把桩补成真实现，没有顺手改语义。
     */
    @Override
    public Object markNotificationRead(Long notificationId) {
        log.info("标记单条通知已读: notificationId={}", notificationId);
        int rows = notificationMapper.markAsRead(notificationId);
        return Map.of("success", rows > 0, "affected", rows,
                "message", rows > 0 ? "标记已读成功" : "通知不存在");
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

    /** 订阅设置认识的 9 个 key（顺序固定，报错信息才稳定）。其余打 WARN，不再无声丢弃。 */
    private static final List<String> SUBSCRIPTION_KEYS = List.of(
            "subscribeWarnings", "subscribeLow", "subscribeMedium", "subscribeHigh",
            "subscribeAssistance", "subscribeSystem",
            "pushEmail", "pushSms", "pushApp");

    /**
     * 读取订阅偏好。
     *
     * <p>⚠️ 参数是 **`student_profile.id`**（`subscription_preferences.student_id` 存的就是它）。
     *
     * <p>2026-09-30 改：原来查不到就 `insertDefault(studentId)` —— **一个 GET 会写库**，
     * 而且传进来的是 `users.id` 时（两个 id 空间有重叠值），它会照着那个错的 id
     * 造一条指向别人的默认偏好行，页面照常显示默认值、数据库里多一行，全程没人看得出来。
     * 现在先确认这个 id 真是一个学生档案；不是就如实报错，不再制造幽灵行。
     */
    @Override
    public Object getSubscriptionPreferences(Long studentId) {
        log.info("获取订阅偏好: profileId={}", studentId);
        if (studentProfileMapper.selectById(studentId) == null) {
            return subscriptionFail("学生档案不存在: " + studentId + "（这里要的是 student_profile.id）");
        }
        SubscriptionPreference sp = subscriptionPreferenceMapper.selectByStudentId(studentId);
        if (sp == null) {
            // 确实是学生、只是还没有偏好行 → 建一行默认的再读回来（写入的 id 是核对过的）
            subscriptionPreferenceMapper.insertDefault(studentId);
            sp = subscriptionPreferenceMapper.selectByStudentId(studentId);
            if (sp == null) {
                return subscriptionFail("订阅偏好创建后回读不到（studentId=" + studentId + "）");
            }
        }
        return subToMap(sp);
    }

    /**
     * 更新订阅偏好。
     *
     * <p>⚠️ 参数同样是 **`student_profile.id`**。
     *
     * <p>2026-09-30 改：原来逐字段 `(Integer) pref.get(...)` **硬转**，而页面上的
     * `el-checkbox` v-model 提交出来的是**布尔值** —— 于是「保存设置」必然
     * `ClassCastException`（拦在 controller 的 catch 里，回 code 500），
     * 而那时页面用死分支读响应，看不出任何异常，就是"点了没反应"。
     * 现在 true/false 与 1/0 都收；认不出的值**报错**而不是静默跳过；写完回读一行再返回。
     */
    @Override
    public Object updateSubscriptionPreferences(Long studentId, Object preferences) {
        log.info("更新订阅偏好: profileId={}, preferences={}", studentId, preferences);
        @SuppressWarnings("unchecked")
        Map<String, Object> pref = (Map<String, Object>) preferences;
        if (pref == null || pref.isEmpty()) {
            return subscriptionFail("没有要更新的订阅设置");
        }
        if (studentProfileMapper.selectById(studentId) == null) {
            return subscriptionFail("学生档案不存在: " + studentId + "（这里要的是 student_profile.id）");
        }

        List<String> ignored = pref.keySet().stream()
                .filter(k -> !SUBSCRIPTION_KEYS.contains(k)).sorted().toList();
        if (!ignored.isEmpty()) {
            log.warn("更新订阅偏好时忽略了不认识的字段（未落库）: keys={}", ignored);
        }

        Map<String, Integer> values = new LinkedHashMap<>();
        for (String key : SUBSCRIPTION_KEYS) {
            if (!pref.containsKey(key)) {
                continue;
            }
            Integer v = asFlag(pref.get(key));
            if (v == null) {
                return subscriptionFail("订阅设置 " + key + " 只能是 true/false 或 1/0（收到 "
                        + pref.get(key) + "）");
            }
            values.put(key, v);
        }
        if (values.isEmpty()) {
            return subscriptionFail("没有可识别的订阅设置（认识的是 " + SUBSCRIPTION_KEYS + "）");
        }

        SubscriptionPreference sp = subscriptionPreferenceMapper.selectByStudentId(studentId);
        if (sp == null) {
            subscriptionPreferenceMapper.insertDefault(studentId);
            sp = subscriptionPreferenceMapper.selectByStudentId(studentId);
            if (sp == null) {
                return subscriptionFail("订阅偏好创建后回读不到（studentId=" + studentId + "）");
            }
        }
        // 用 for 而不是 forEach：`sp` 在上面可能被重新赋值过（不是 effectively final），lambda 捕获不了
        for (Map.Entry<String, Integer> e : values.entrySet()) {
            String k = e.getKey();
            int v = e.getValue();
            switch (k) {
                case "subscribeWarnings" -> sp.setSubscribeWarnings(v);
                case "subscribeLow" -> sp.setSubscribeLow(v);
                case "subscribeMedium" -> sp.setSubscribeMedium(v);
                case "subscribeHigh" -> sp.setSubscribeHigh(v);
                case "subscribeAssistance" -> sp.setSubscribeAssistance(v);
                case "subscribeSystem" -> sp.setSubscribeSystem(v);
                case "pushEmail" -> sp.setPushEmail(v);
                case "pushSms" -> sp.setPushSms(v);
                case "pushApp" -> sp.setPushApp(v);
                default -> log.warn("未处理的订阅字段: {}", k);
            }
        }
        sp.setUpdatedAt(java.time.LocalDateTime.now());
        int rows = subscriptionPreferenceMapper.updateById(sp);
        if (rows <= 0) {
            return subscriptionFail("订阅设置写入失败（影响 0 行）");
        }

        // 回读：响应里的值必须是库里的值（本服务里"报成功但没写进去"已出现过好几次）
        SubscriptionPreference saved = subscriptionPreferenceMapper.selectByStudentId(studentId);
        if (saved == null) {
            return subscriptionFail("订阅设置写入后回读不到（studentId=" + studentId + "）");
        }
        Map<String, Object> resp = new LinkedHashMap<>(subToMap(saved));
        resp.put("success", true);
        return resp;
    }

    /** 订阅接口失败时的统一形状（HTTP 与 code 仍是 200，成败只在载荷里）。 */
    private static Map<String, Object> subscriptionFail(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", false);
        m.put("message", message);
        return m;
    }

    /**
     * 订阅开关 → 1/0。
     * 接受 `true/false`（el-checkbox 实际发的）、`1/0`（库里原来的存法）、`"true"/"false"/"1"/"0"`；
     * 其它一律返回 null，由调用方报错 —— 不猜、不静默当成 0。
     */
    private static Integer asFlag(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean b) {
            return b ? 1 : 0;
        }
        if (value instanceof Number n) {
            int i = n.intValue();
            return (i == 0 || i == 1) ? i : null;
        }
        String s = value.toString().trim();
        if ("true".equalsIgnoreCase(s) || "1".equals(s)) {
            return 1;
        }
        if ("false".equalsIgnoreCase(s) || "0".equals(s)) {
            return 0;
        }
        return null;
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

    /**
     * 帮扶评价提交体里**认识**的 key（其余的一律打 WARN，不再无声丢弃）。
     *
     * <p>`rating` / `feedback` 是 PC 端 `AssistanceFeedback.vue` **一直在发**的名字，
     * 和库里的 `score` / `content` 对不上，历史行为是**静默丢弃** —— 实测：
     * 学生点 4 分、填 5 分有效性、写一整段反馈，接口回 `{"success":true,"message":"评价提交成功"}`，
     * 而库里那一行是 `score=NULL, content=NULL`（`effectiveness` 当时连列都没有）。
     * 现在两个名字都收（规范名优先），前端也已统一到 `score` / `content`。
     *
     * <p>`studentId` 收下但**不参与写入**（学生身份由 `planId` 关联出来），
     * 只在传了的时候用来做一次一致性校对，见 {@link #submitEvaluation}。
     */
    private static final Set<String> EVALUATION_KEYS = Set.of(
            "id", "planId", "studentId", "counselorId",
            "score", "rating", "content", "feedback", "effectiveness");

    /**
     * 提交 / 更新帮扶评价。
     *
     * <p>2026-09-30 之前这个方法只会 INSERT，且只认 4 个 key。由此产生三个问题，
     * 都在这里一起修掉（每一条都实测复现过）：
     *
     * <ol>
     *   <li>**静默丢字段**：页面发 `rating` / `effectiveness` / `feedback`，方法只读 `score` / `content`，
     *       多出来的键一声不响地消失，接口仍回 `success:true`。现在收别名 + 校验 + 打 WARN。</li>
     *   <li>**"编辑评价"不生效、反而多出一行**：页面的编辑弹窗会带 `id` 提交，方法却总是 INSERT ——
     *       实测带 `id=2` 再提交一次，返回的是 `id=3`，库里变成两条同计划的评价。现在带 `id` 走更新。</li>
     *   <li>**报告与事实不符**：无论写没写进去都回 `{"success":true,"message":"评价提交成功"}`。
     *       现在写完**回读**一次，响应里的 score/content/effectiveness/counselorId 是库里的值；
     *       写不进去就返回 `{"success":false,"message":"..."}`，不再假报成功。</li>
     * </ol>
     *
     * <p>身份与辅导员都取自 `planId` 指向的那条帮扶计划：学生 = 计划的 student（所以没有
     * 独立的 studentId 入库字段），辅导员 = 计划的 counselor_id。客户端传的 `counselorId`
     * 只用来比对，**不信它**（否则谁都能把评价挂到别的辅导员名下）。
     *
     * <p>一条帮扶计划只保留一条评价：不带 `id` 但该计划已有评价时，更新那一行并如实回
     * `message="该计划已有评价，本次为更新"`，不堆重复行（页面按 planId 判断"是否已评价"）。
     */
    @Override
    public Object submitEvaluation(Object evaluation) {
        log.info("提交帮扶评价: {}", evaluation);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> req = (Map<String, Object>) evaluation;

            List<String> ignored = req.keySet().stream()
                    .filter(k -> !EVALUATION_KEYS.contains(k)).sorted().toList();
            if (!ignored.isEmpty()) {
                log.warn("提交帮扶评价时忽略了不认识的字段（未落库）: keys={}", ignored);
            }

            // ---- ① 计划：这条评价归属谁、评的是哪个辅导员，全由它决定 ----
            Long planId = asLong(req.get("planId"));
            if (planId == null) {
                return evaluationFail("缺少 planId（要评价哪条帮扶计划）");
            }
            AssistancePlan plan = assistancePlanMapper.selectById(planId);
            if (plan == null) {
                return evaluationFail("帮扶计划不存在: " + planId);
            }
            if (req.get("studentId") != null) {
                // studentId 传的是 users.id（与 /students/** 其它接口一致）。
                // 对不上就拒绝：两个身份打架时静默挑一个，正是这次要修的那类问题。
                Long profileId = resolveProfileId(asLong(req.get("studentId")));
                if (profileId == null || !profileId.equals(plan.getStudentId())) {
                    return evaluationFail("帮扶计划 " + planId + " 不属于该学生，评价未写入");
                }
            }

            // ---- ② 评分：必填，且必须是 1-5 的整数 ----
            Object rawScore = req.get("score") != null ? req.get("score") : req.get("rating");
            if (rawScore == null) {
                return evaluationFail("缺少评分（score 或 rating，1-5）");
            }
            Integer score = asInteger(rawScore);
            if (score == null) {
                return evaluationFail("评分必须是整数（score=" + rawScore + "）");
            }
            if (score < 1 || score > 5) {
                return evaluationFail("评分必须在 1-5 之间（score=" + score + "）");
            }

            // ---- ③ 有效性：页面标了必填，这里就当真必填（0 不等于"没打分"）----
            Integer effectiveness = null;
            if (req.get("effectiveness") != null) {
                effectiveness = asInteger(req.get("effectiveness"));
                if (effectiveness == null) {
                    return evaluationFail("有效性评分必须是整数（effectiveness=" + req.get("effectiveness") + "）");
                }
                if (effectiveness < 1 || effectiveness > 5) {
                    return evaluationFail("有效性评分必须在 1-5 之间（effectiveness=" + effectiveness + "）");
                }
            }

            // ---- ④ 定位目标行：带 id 按 id，否则按 planId 找已有评价 ----
            Long targetId = asLong(req.get("id"));
            AssistanceEvaluation ae;
            String message;
            if (targetId != null) {
                ae = assistanceEvaluationMapper.selectById(targetId);
                if (ae == null) {
                    return evaluationFail("要更新的评价不存在: " + targetId);
                }
                if (!planId.equals(ae.getPlanId())) {
                    return evaluationFail("评价 " + targetId + " 不属于帮扶计划 " + planId + "，未改动");
                }
                message = "评价已更新";
            } else {
                List<AssistanceEvaluation> existing = assistanceEvaluationMapper.selectByPlanId(planId);
                if (!existing.isEmpty()) {
                    ae = existing.get(0);
                    message = "该计划已有评价，本次为更新";
                } else {
                    ae = new AssistanceEvaluation();
                    ae.setPlanId(planId);
                    ae.setCreatedAt(java.time.LocalDateTime.now());
                    message = "评价提交成功";
                }
            }

            // ---- ⑤ 辅导员一律取计划上的；客户端传的值只用于比对 ----
            Long sentCounselor = asLong(req.get("counselorId"));
            if (sentCounselor != null && !sentCounselor.equals(plan.getCounselorId())) {
                log.warn("客户端传的 counselorId={} 与计划的辅导员 {} 不一致，按计划取值",
                        sentCounselor, plan.getCounselorId());
            }
            ae.setCounselorId(plan.getCounselorId());
            ae.setScore(score);
            ae.setEffectiveness(effectiveness);
            // 提交体里带了 content（或 feedback）才动这一列：带空串 = 学生清空了输入框，
            // 如实写空串；完全没带 = 不动（新建时为 NULL）。
            Object rawContent = req.containsKey("content") ? req.get("content") : req.get("feedback");
            if (rawContent != null) {
                ae.setContent(rawContent.toString());
            } else if (ae.getId() == null) {
                ae.setContent(null);
            }
            ae.setUpdatedAt(java.time.LocalDateTime.now());

            int rows = ae.getId() == null
                    ? assistanceEvaluationMapper.insert(ae)
                    : assistanceEvaluationMapper.updateById(ae);
            if (rows <= 0) {
                return evaluationFail("评价写入失败（影响 0 行）");
            }

            // ---- ⑥ 回读：响应里的值必须是库里的值 ----
            AssistanceEvaluation saved = assistanceEvaluationMapper.selectById(ae.getId());
            if (saved == null) {
                return evaluationFail("评价写入后回读不到该条记录（id=" + ae.getId() + "）");
            }
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("id", saved.getId());
            resp.put("message", message);
            resp.put("score", saved.getScore());
            resp.put("effectiveness", saved.getEffectiveness());
            resp.put("content", saved.getContent());
            resp.put("counselorId", saved.getCounselorId());
            return resp;
        } catch (Exception e) {
            log.error("提交帮扶评价失败: {}", e.getMessage(), e);
            return evaluationFail("提交帮扶评价失败: " + e.getMessage());
        }
    }

    /**
     * 某学生的评价列表。
     *
     * <p>⚠️ 参数是 **`student_profile.id`**，不是 `users.id`：SQL 走
     * `assistance_evaluations JOIN assistance_plans ON ae.plan_id = ap.id WHERE ap.student_id = ?`，
     * 而 `assistance_plans.student_id` 存的是 profile id（1001 号计划 = 3）。
     * 两个 id 空间有重叠值，**传错不会报错、只会永远返回空数组**，
     * 所以调用方必须先把 users.id 换成 profile id（PC 端走 `/students/student-by-user/{uid}`）。
     */
    @Override
    public Object getEvaluationsByStudent(Long studentId) {
        log.info("获取学生评价列表: profileId={}", studentId);
        List<AssistanceEvaluation> list = assistanceEvaluationMapper.selectByStudentId(studentId);
        return list.stream().map(this::evaluationToMap).toList();
    }

    /**
     * 某条帮扶计划的评价。
     *
     * <p>⚠️ 参数是 **`assistance_plans.id`**（与列表接口的 profile id 是两套空间，注意别混）。
     * 原实现在没有评价时返回 `{"error":"无评价数据"}` —— 一个列表接口对"空"回错误信封，
     * 调用方 `list.map(...)` 会直接崩，而且 HTTP 与 code 都是 200，看不出是空还是坏。
     * 现在如实返回空数组。
     */
    @Override
    public Object getPlanEvaluation(Long planId) {
        log.info("获取帮扶计划评价: planId={}", planId);
        List<AssistanceEvaluation> list = assistanceEvaluationMapper.selectByPlanId(planId);
        return list.stream().map(this::evaluationToMap).toList();
    }

    /**
     * 评价统计。
     *
     * <p>⚠️ 参数同样是 **`student_profile.id`**（见 {@link #getEvaluationsByStudent}）。
     *
     * <p>2026-09-30 补：原来只回 `averageScore` / `totalCount`，而页面要的是
     * 「平均评分 / 有效性评分 / 评分分布」三项，全都没有 —— 于是页面上「有效性评分」
     * 恒为 0.0、「评分分布」恒显示"暂无数据"（不是页面写错字，是后端没这个字段）。
     * 现在补 `avgEffectiveness` 与 `ratingDistribution`，并**保留**原来的
     * `averageScore` / `totalCount` 两个键（小程序等既有调用方不用改）。
     *
     * <p>`ratingDistribution` 的五个键（"1".."5"）**一律给出、没有的填 0**，
     * 前端不用自己补零；`avgEffectiveness` 在"有评价但都没填有效性"时是 0，
     * 与"确实打了 0 分"不可能混淆（1-5 是唯一合法取值，0 不会入库）。
     */
    @Override
    public Object getEvaluationStatistics(Long studentId) {
        log.info("获取评价统计: profileId={}", studentId);
        Double avg = assistanceEvaluationMapper.avgScoreByStudentId(studentId);
        Double avgEffectiveness = assistanceEvaluationMapper.avgEffectivenessByStudentId(studentId);
        int count = assistanceEvaluationMapper.countByStudentId(studentId);
        List<Integer> scores = assistanceEvaluationMapper.scoresByStudentId(studentId);

        Map<String, Integer> distribution = new LinkedHashMap<>();
        for (int s = 1; s <= 5; s++) {
            distribution.put(String.valueOf(s), 0);
        }
        for (Integer s : scores) {
            if (s == null) {
                continue;
            }
            if (s < 1 || s > 5) {
                // 提交路径已拦住越界分，这里兜的是历史脏数据：不伪造桶，也不悄悄丢，
                // 打一条 WARN 让它可查（丢进分布里会凭空多出一行星级，更糟）。
                log.warn("发现越界评分（1-5 之外），已从分布中排除: studentId={}, score={}", studentId, s);
                continue;
            }
            distribution.merge(String.valueOf(s), 1, Integer::sum);
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("averageScore", avg != null ? BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP) : 0);
        stats.put("totalCount", count);
        stats.put("avgEffectiveness", avgEffectiveness != null
                ? BigDecimal.valueOf(avgEffectiveness).setScale(1, RoundingMode.HALF_UP) : 0);
        stats.put("ratingDistribution", distribution);
        return stats;
    }

    /**
     * 评价失败时的统一形状（与成功形状 `{"success":true,...}` 对称）。
     *
     * <p>⚠️ 失败也是 **HTTP 200 + code 200**，成败只在载荷里 —— 前端必须显式判
     * `res.success === false`，不能只看请求有没有成功。
     */
    private static Map<String, Object> evaluationFail(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", false);
        m.put("message", message);
        return m;
    }

    /** JSON 数字/字符串 → Long；不是数字就返回 null（不抛异常，让调用方决定怎么报错）。 */
    private static Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.valueOf(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * JSON 数字/字符串 → Integer，但**严格**：3.5 这种不截断、也不四舍五入，返回 null。
     *
     * <p>为什么必须严格：score / effectiveness 都是 INT 列，MySQL 会把 3.5 静默存成 4 ——
     * 学生点 3.5 星、库里 4 星、页面显示 4 星，全程没人知道。页面的 el-rate 已去掉
     * allow-half，这里是不信任客户端时的第二道。
     */
    private static Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        double d;
        if (value instanceof Number n) {
            d = n.doubleValue();
        } else {
            try {
                d = Double.parseDouble(value.toString().trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (!Double.isFinite(d) || d != Math.rint(d)
                || d < Integer.MIN_VALUE || d > Integer.MAX_VALUE) {
            return null;
        }
        return (int) d;
    }


    // ==================== 设置 ====================
    @Override
    public Object updatePrivacyLevel(Long studentId, Integer privacyLevel) {
        log.info("更新隐私级别: studentId={}, privacyLevel={}", studentId, privacyLevel);
        // ⚠️ 这里原来是 selectByStudentId(...)，那个方法查的是**学号列**
        // （StudentProfileMapper:11 `WHERE student_id = #{studentId}`），而本参数是
        // users.id —— 两者不相交，于是这个方法**永远**返回「学生不存在」，
        // 从来没有任何一次隐私级别被写进去过。按 users.id 查要走 selectByUserId。
        StudentProfile sp = studentProfileMapper.selectByUserId(studentId);
        if (sp == null) return Map.of("success", false, "message", "学生不存在");
        sp.setPrivacyLevel(privacyLevel);
        sp.setUpdatedAt(java.time.LocalDateTime.now());
        int rows = studentProfileMapper.updateById(sp);
        return Map.of("success", rows > 0, "privacyLevel", privacyLevel);
    }

    /**
     * 读取学生设置。
     *
     * <p>⚠️ 这里原来返回的是三个**写死的字面量**（`notificationSettings` /
     * `privacySettings` / `displaySettings`），一个字段都不来自数据库 ——
     * 唯一真实的是 `privacyLevel`，而它取的还是错键
     * （`selectByStudentId` 查学号列、传的是 users.id），所以连那一项也永远不出现。
     * 库里 `student_settings.theme` 明明有 'dark'（宋新鹏），接口却一律回 'light'。
     *
     * <p>现在读**真源**：`user_settings`（键 users.id，与 user-service 的
     * `/users/{uid}/settings` 同一张表、同一个实体）+ `student_profile.privacy_level`。
     * 无记录时不落库、返回与建表 DEFAULT 一致的默认值 —— 与 user-service 同口径
     * （那边由 `defaultSettings` 负责，改表时两处要一起改）。
     */
    @Override
    public Object getStudentSettings(Long studentId) {
        log.info("获取学生设置: studentId={}", studentId);
        UserSettings settings = userSettingsMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UserSettings>()
                        .eq("user_id", studentId));
        boolean persisted = settings != null;
        if (!persisted) {
            settings = defaultUserSettings(studentId);
        }
        StudentProfile sp = studentProfileMapper.selectByUserId(studentId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", settings.getUserId());
        result.put("warningNotification", settings.getWarningNotification());
        result.put("systemMessage", settings.getSystemMessage());
        result.put("classNotification", settings.getClassNotification());
        result.put("studentFeedback", settings.getStudentFeedback());
        result.put("emailReminder", settings.getEmailReminder());
        result.put("officeLocation", settings.getOfficeLocation());
        result.put("officePhone", settings.getOfficePhone());
        result.put("officeHours", settings.getOfficeHours());
        result.put("privacyLevel", sp != null && sp.getPrivacyLevel() != null ? sp.getPrivacyLevel() : 2);
        // 让调用方分得清"库里有这行"和"这是默认值"——两者的字段长得一模一样
        result.put("persisted", persisted);
        return result;
    }

    /** 与 `user_settings` 建表 DEFAULT 一致（同 user-service 的 defaultSettings），改表时三处要一起改。 */
    private static UserSettings defaultUserSettings(Long userId) {
        return new UserSettings()
                .setUserId(userId)
                .setWarningNotification(true)
                .setSystemMessage(true)
                .setClassNotification(true)
                .setStudentFeedback(true)
                .setEmailReminder(false);
    }

    /** 本接口认得的 key；不在其中的一律明确报错，不静默丢弃。 */
    private static final java.util.Set<String> SETTINGS_KEYS = java.util.Set.of(
            "warningNotification", "systemMessage", "classNotification", "studentFeedback", "emailReminder",
            "officeLocation", "officePhone", "officeHours", "privacyLevel");

    /**
     * 保存学生设置，**部分更新**语义（只写请求体里出现的字段）。
     *
     * <p>此前是个桩：`return Map.of("success", true)`，一个字段都不落库 ——
     * 页面提示保存成功、退出再进全变回去。
     *
     * <p>通知偏好写 `user_settings`（部分更新的实现依赖 MyBatis-Plus 的
     * `FieldStrategy.NOT_NULL`，所以 `UserSettings` 的布尔字段是包装类型 `Boolean`，
     * 分得清"没传"与"传了 false"）；`privacyLevel` 不在那张表里，写
     * `student_profile.privacy_level`（键 student_profile.id，故先按 users.id 查档案）。
     *
     * <p>未识别的 key **返回错误**而不是忽略：这是一族反复出现的毛病
     * （申诉的 `description`、评价的 `rating` 都是这么被吞掉的），
     * 保存设置这种"用户以为存上了"的场景尤其不能静默吞。
     */
    @Override
    public Object updateStudentSettings(Long userId, Map<String, Object> body) {
        log.info("保存学生设置: userId={}, keys={}", userId, body == null ? null : body.keySet());
        if (body == null || body.isEmpty()) {
            return Map.of("success", false, "message", "请求体为空");
        }
        List<String> unknown = body.keySet().stream().filter(k -> !SETTINGS_KEYS.contains(k)).toList();
        if (!unknown.isEmpty()) {
            return Map.of("success", false,
                    "message", "无法识别的设置项: " + String.join(", ", unknown)
                            + "；可接受: " + String.join(", ", SETTINGS_KEYS));
        }

        Integer privacyLevel = null;
        if (body.containsKey("privacyLevel")) {
            privacyLevel = toInteger(body.get("privacyLevel"));
            if (privacyLevel == null) {
                return Map.of("success", false, "message", "privacyLevel 不是整数: " + body.get("privacyLevel"));
            }
        }

        UserSettings patch = new UserSettings()
                .setUserId(userId)
                .setWarningNotification(toBoolean(body.get("warningNotification")))
                .setSystemMessage(toBoolean(body.get("systemMessage")))
                .setClassNotification(toBoolean(body.get("classNotification")))
                .setStudentFeedback(toBoolean(body.get("studentFeedback")))
                .setEmailReminder(toBoolean(body.get("emailReminder")))
                .setOfficeLocation(toStringOrNull(body.get("officeLocation")))
                .setOfficePhone(toStringOrNull(body.get("officePhone")))
                .setOfficeHours(toStringOrNull(body.get("officeHours")));

        UserSettings existing = userSettingsMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UserSettings>()
                        .eq("user_id", userId));
        if (existing == null) {
            // 新行：null 字段被 NOT_NULL 策略跳过，由建表 DEFAULT 兜底
            userSettingsMapper.insert(patch);
        } else {
            patch.setId(existing.getId());
            userSettingsMapper.updateById(patch);
        }

        boolean privacyApplied = false;
        if (privacyLevel != null) {
            StudentProfile sp = studentProfileMapper.selectByUserId(userId);
            if (sp == null) {
                // 如实说出来：通知偏好存了、隐私级别没存，别整单报成功
                return Map.of("success", false, "message", "通知偏好已保存，但该用户没有学生档案，privacyLevel 未写入");
            }
            sp.setPrivacyLevel(privacyLevel);
            sp.setUpdatedAt(java.time.LocalDateTime.now());
            privacyApplied = studentProfileMapper.updateById(sp) > 0;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("privacyApplied", privacyApplied);
        // 回读：返回的是库里的样子
        result.putAll((Map<String, Object>) getStudentSettings(userId));
        return result;
    }

    private static Boolean toBoolean(Object value) {
        if (value == null) return null;
        if (value instanceof Boolean b) return b;
        String s = value.toString().trim();
        if (s.isEmpty()) return null;
        return "true".equalsIgnoreCase(s) || "1".equals(s);
    }

    private static Integer toInteger(Object value) {
        if (value == null) return null;
        // ⚠️ Boolean / "true"/"false" 这两支不是可选的：`privacy_level` 是 `tinyint(1)`，
        // MySQL JDBC 默认把 tinyint(1) 映射成 Boolean（tinyInt1isBit=true）。
        // 少了它们，「公开（0）」会取成 Boolean.FALSE → parse 失败 → 返回 null，
        // 于是**最公开的那一档反而被当成"没配过"**，排行榜上被遮住。
        // （负向对照：把这两支一起删掉，privacy_tinyint1AsBooleanIsUnderstood 立刻变红。）
        if (value instanceof Boolean b) return b ? 1 : 0;
        if (value instanceof Number n) return n.intValue();
        String s = value.toString().trim();
        // "true"/"false" 也认（某些驱动/序列化路径会给字符串）
        if ("true".equalsIgnoreCase(s)) return 1;
        if ("false".equalsIgnoreCase(s)) return 0;
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String toStringOrNull(Object value) {
        return value == null ? null : value.toString();
    }

    // 2026-10-08（W29）删除 getSecurityLogs(studentId, page, pageSize)。
    // 原实现把 page/pageSize 原样回显、total 写死 0、logs 写死空列表 —— **一次查询都没有**，
    // 分页参数收下了却没有任何可翻的页。`security_logs` 表存在但全仓没有写入方
    // （AdminController 里已明写这一点），而学生真正看的「登录历史」走
    // `GET /users/{uid}/login-logs`（读有真写入方的 `login_logs`）。
    // 端点零调用方 ⇒ 按「零调用方 = 删」处理，不补实现。详见 StudentController 里的注释。

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

    // ==================== 导出 ====================
    /**
     * 生成学生成绩单 xlsx（W1.8，2026-10-02 新增）。
     *
     * <p>改之前这里是**两个**方法（{@code exportScoresToExcel} / {@code downloadScores}），
     * 都只回一个 JSON 摘要，且入参按"学号"查（{@code selectByStudentId(userId.toString())}）
     * —— 路径上给的是 {@code users.id}，查的是学号列，**一个学生也查不到**。
     * 现在只留一条路径：真出工作簿。
     *
     * <p>学期列不直接写 {@code scores.semester} 那个全局序号（"4"），而是按
     * {@code student_profile.enrollment_year} 换算成 "2023-2024-2" —— 与前端
     * {@code Scores.vue} 的 {@code semesterLabel()} 同一套规则，两边显示一致。
     * 入学年份缺失时退回原始序号，不编。
     */
    @Override
    public byte[] exportScoresWorkbook(Long userId) {
        log.info("导出学生成绩单: userId={}", userId);
        StudentProfile sp = studentProfileMapper.selectByUserId(userId);
        if (sp == null) {
            return null;
        }
        // scores.student_id 存的是 student_profile.id，不是学号、也不是 users.id
        List<Score> scores = scoreMapper.selectByStudentId(sp.getId().toString());

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("成绩单");

            // 首行写一行学生信息，表头从第二行开始 —— 导出的文件是给人看的
            Row info = sheet.createRow(0);
            info.createCell(0).setCellValue("姓名");
            info.createCell(1).setCellValue(sp.getName() == null ? "" : sp.getName());
            info.createCell(2).setCellValue("学号");
            info.createCell(3).setCellValue(sp.getStudentId() == null ? "" : sp.getStudentId());
            info.createCell(4).setCellValue("班级");
            info.createCell(5).setCellValue(sp.getClassName() == null ? "" : sp.getClassName());

            String[] headers = {"学期", "课程名称", "学分", "平时成绩", "期末成绩", "总评成绩", "绩点", "等级", "授课教师"};
            Row headerRow = sheet.createRow(1);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            int rowNum = 2;
            for (Score s : scores) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(semesterLabel(s.getSemester(), sp.getEnrollmentYear()));
                row.createCell(1).setCellValue(s.getCourseName() == null ? "" : s.getCourseName());
                setNumeric(row.createCell(2), s.getCredits());
                setNumeric(row.createCell(3), s.getRegularScore());
                setNumeric(row.createCell(4), s.getFinalScore());
                setNumeric(row.createCell(5), s.getScoreTotal());
                setNumeric(row.createCell(6), s.getGradePoint());
                row.createCell(7).setCellValue(s.getGrade() == null ? "" : s.getGrade());
                row.createCell(8).setCellValue(s.getTeacherName() == null ? "" : s.getTeacherName());
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            log.info("导出学生成绩单完成: userId={}, 行数={}", userId, scores.size());
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("生成成绩单失败: " + e.getMessage(), e);
        }
    }

    /** 数字列：空值写空串，别写成 0（0 分与"没录入"是两回事）。 */
    private static void setNumeric(Cell cell, BigDecimal value) {
        if (value == null) {
            cell.setCellValue("");
        } else {
            cell.setCellValue(value.doubleValue());
        }
    }

    /**
     * {@code scores.semester}（全局学期序号）→ "2023-2024-2"。
     *
     * <p>与前端 {@code Scores.vue:semesterLabel()} 同一算法：入学年份 +
     * {@code floor((n-1)/2)} 得学年，{@code (n-1)%2+1} 得学期。入学年份缺失或序号
     * 不是正整数时原样返回，**不猜**。
     */
    static String semesterLabel(String semester, Integer enrollmentYear) {
        if (semester == null || semester.isBlank()) {
            return "";
        }
        int n;
        try {
            n = Integer.parseInt(semester.trim());
        } catch (NumberFormatException e) {
            return semester;
        }
        if (n < 1 || enrollmentYear == null) {
            return semester;
        }
        int year = enrollmentYear + (n - 1) / 2;
        int half = (n - 1) % 2 + 1;
        return year + "-" + (year + 1) + "-" + half;
    }

    // ==================== 学院相关 ====================
    @Override
    public long countByCollege(Long collegeId) {
        return studentProfileMapper.countByCollegeId(collegeId);
    }

    /**
     * 按学院取学生列表；{@code collegeId == null} 表示**不筛学院（全部）**。
     *
     * <p><b>W16 修的静默错</b>：此前无条件调 {@code selectByCollegeId(collegeId)}，而那条
     * SQL 是 {@code WHERE college_id = #{collegeId}} —— 传 null 时成了 {@code college_id = NULL}，
     * 恒不成立 ⇒ "取全部学生"的调用（{@code AdminController.getAllStudents} 传的就是 null）
     * 恒定返回**空数组**，而不是"学生服务出错"。同一语义在辅导员侧是**对的**
     * （{@code CounselorServiceImpl.getAllCounselors} 用 {@code LambdaQueryWrapper} +
     * {@code if (collegeId != null)}），于是 {@code /admin/counselors} 有数据、
     * {@code /admin/students} 空 —— 两个同形的管理员列表端点给出互相矛盾的结果。
     */
    @Override
    public java.util.List<Object> getStudentsByCollege(Long collegeId) {
        List<StudentProfile> profiles = (collegeId == null)
                ? studentProfileMapper.selectList(null)
                : studentProfileMapper.selectByCollegeId(collegeId);
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
        // 学生自己写的正文。加列（2026-09-30）之前的申诉这里是 null，前端按空处理、不显示那一块。
        m.put("description", a.getDescription());
        m.put("attachments", a.getAttachments());
        m.put("status", a.getStatus());
        // 2026-10-01 第二十一轮: 学生端「预警」页的申诉卡读的是 courseName/submittedAt 与
        // 字符串 status('pending'/'approved'/'rejected') —— 三者后端从来不给, 于是课程名与
        // 时间恒空白, 而状态标签**恒落到 else 分支显示「已驳回」**(待处理的申诉被标成已驳回)。
        // 这里补 courseName(course_id 是真实关联)与 statusText; 前端改用数值口径。
        if (a.getCourseId() != null) {
            m.put("courseName", scoreAppealMapper.selectCourseName(a.getCourseId()));
        }
        // 2026-10-03（第四十三轮 W9）: `score_id` 从前只是**原样吐一个裸数字**，从不查 scores ——
        // 于是即使库里填好了"申诉的是哪一次成绩"（见 §6.41 I 补的 157/152），页面也看不出，
        // 因为学期与分数都拿不到。这里把成绩行带出来给前端显示。
        // 查不到就**不 put**：不编造，也不让前端渲染出一个 "null"。
        // ⚠️ 这是 N+1（每行申诉一次 selectById）。单个学生的申诉条数是个位数，可接受；
        //    日后若有端要一次拉很多行，再改成批量查询。
        if (a.getScoreId() != null) {
            Score s = scoreMapper.selectById(a.getScoreId());
            if (s != null) {
                m.put("scoreSemester", s.getSemester());
                m.put("scoreYear", s.getYear());
                m.put("scoreTotal", s.getScoreTotal());
                m.put("scoreGrade", s.getGrade());
            }
        }
        m.put("statusText", appealStatusText(a.getStatus()));
        m.put("reply", a.getReply());
        m.put("createdAt", a.getCreatedAt() != null ? a.getCreatedAt().toString() : null);
        m.put("updatedAt", a.getUpdatedAt() != null ? a.getUpdatedAt().toString() : null);
        return m;
    }

    /** 申诉状态码 → 展示文案。0 待处理 / 1 已通过 / 2 已驳回 / 3 已撤回。 */
    private static String appealStatusText(Integer status) {
        if (status == null) {
            return "待处理";
        }
        return switch (status) {
            case 1 -> "已通过";
            case 2 -> "已驳回";
            case 3 -> "已撤回";
            default -> "待处理";
        };
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
        // 2026-09-30 补：有效性评分原来**没有列也没有这个键**，页面读 `row.effectiveness`
        // 恒为 undefined（表格里那一栏永远显示 "0 / 5"）。加列同时把它列出来。
        // 加列前的历史行为 NULL —— 是"当时没收集"，不是 0 分，页面不要拿 `|| 0` 盖过去。
        m.put("effectiveness", ae.getEffectiveness());
        m.put("createdAt", ae.getCreatedAt() != null ? ae.getCreatedAt().toString() : null);
        return m;
    }

    private void ensureSubscriptionExists(Long studentId) {
        SubscriptionPreference sp = subscriptionPreferenceMapper.selectByStudentId(studentId);
        if (sp == null) subscriptionPreferenceMapper.insertDefault(studentId);
    }
}