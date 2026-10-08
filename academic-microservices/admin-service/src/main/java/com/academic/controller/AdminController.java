package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.common.entity.User;
import com.academic.common.entity.College;
import com.academic.common.entity.Course;
import com.academic.common.entity.Major;
import com.academic.common.entity.Student;
import com.academic.common.entity.TeacherProfile;
import com.academic.common.entity.CounselorProfile;
import com.academic.common.entity.CollegeAdminProfile;
import com.academic.common.entity.AcademicWarning;
import com.academic.common.entity.Score;
import com.academic.common.entity.ScoreRecord;
import com.academic.common.util.ExcelUploadErrors;
import com.academic.common.util.UpdateBodyGuard;
import com.academic.entity.*;
import com.academic.service.BackupService;
import com.academic.service.CollegeService;
import com.academic.service.CourseService;
import com.academic.service.MajorService;
import com.academic.service.StudentService;
import com.academic.service.CounselorService;
import com.academic.service.UserService;
import com.academic.service.WarningRuleService;
import com.academic.service.StudentProfileService;
import com.academic.service.ScoreRecordService;
import com.academic.service.ReportTemplateService;
import com.academic.service.StudentImportService;
import com.academic.mapper.ClassesMapper;
import com.academic.mapper.TeacherProfileMapper;
import com.academic.mapper.ExportHistoryMapper;
import com.academic.mapper.StudentProfileMapper;
import com.academic.mapper.CounselorProfileMapper;
import com.academic.mapper.CollegeAdminProfileMapper;
import com.academic.mapper.ScoreRecordMapper;
import com.academic.mapper.AcademicWarningMapper;
import com.academic.mapper.ScoreStatMapper;
import com.academic.mapper.UserMapper;
import com.academic.mapper.AdminTaskMapper;
import com.academic.exception.ResourceNotFoundException;

import java.math.BigDecimal;
import java.util.ArrayList;
import com.academic.feign.MessageServiceClient;
import com.academic.feign.StudentServiceClient;
import com.academic.feign.CounselorServiceClient;
import com.academic.feign.CourseServiceClient;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin")
@Slf4j
public class AdminController {

    /** 只用于把文件端点的失败体写成 JSON（见 {@link #writeFileEndpointError}）—— 无状态，可静态复用。 */
    private static final com.fasterxml.jackson.databind.ObjectMapper FILE_ENDPOINT_JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

    /**
     * 导出/下载端点的**失败回写**（W26，2026-10-05）。
     *
     * <p>这些端点的响应是**文件流**，前端一律用 {@code responseType:'blob'} 调。原先的 catch 只写了
     * {@code response.getWriter().write("导出失败: " + e.getMessage())} —— **既没 setStatus、也没改
     * Content-Type**，而此时 Content-Type 往往已经被上面那行设成 xlsx 了。结果是一条
     * {@code HTTP 200} + {@code Content-Type: …spreadsheetml.sheet} + 一段中文纯文本：
     * 前端把错误提示**当成文件存了下来**，用户拿到一个"下载成功"却打不开的坏 xlsx。
     *
     * <p>修法与 {@code teacher-service} 的 {@code TeacherController#writeJsonDenied} 同款
     * （那里的注释写的正是这个病）：**blob 端点不能用"HTTP 200 + body code"那套**，必须回真状态码；
     * 前端 {@code utils/download.js} 的 {@code describeDownloadError} 会把 Blob 读回文本、取
     * {@code message} 显示出来。
     *
     * @param response 目标响应
     * @param message  已经拼好人话的错误描述（如 {@code "导出失败: …"}）
     */
    private void writeFileEndpointError(HttpServletResponse response, String message) {
        try {
            if (response.isCommitted()) {
                // 文件已经开始写进响应了（如 Files.copy 写到一半才抛）——状态码与 Content-Type 都已发出，
                // 改不了。前端会拿到一个截断的文件，这是无法挽回的，只能留一条日志。
                log.error("导出响应已提交，错误体写不进去: {}", message);
                return;
            }
            response.reset();   // 丢掉上面可能已设的 xlsx Content-Type
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("application/json;charset=UTF-8");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("code", 500);
            payload.put("message", message);
            payload.put("success", false);
            response.getWriter().write(FILE_ENDPOINT_JSON.writeValueAsString(payload));
        } catch (Exception ignored) {
            // 连错误体都写不出去就只能算了 —— 状态码已是 500，前端不会把它当成功文件存下来。
        }
    }

    @Autowired
    private com.academic.mapper.LoginLogMapper loginLogMapper;

    /**
     * 审计日志写入（2026-10-02 补的写入方）。
     *
     * <p>{@code audit_logs} 此前**全仓零写入方**（0 行），而「用户详情」页的操作日志计数
     * 一直读它 ⇒ 恒 0。现在下面这些关键写操作各记一条：账号增删改/重置口令/启停/审批、
     * 预警规则增删改、预警处理、申诉审核。
     */
    @Autowired
    private com.academic.service.AuditLogService auditLogService;

    @Autowired
    private com.academic.mapper.AuditLogMapper auditLogMapper;

    /** 管理端任务（W1.11）。`/admin/tasks/*` 原来是整组桩，现在落到 `admin_tasks` 表上。 */
    @Autowired
    private AdminTaskMapper adminTaskMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CollegeService collegeService;

    @Autowired
    private MajorService majorService;

    @Autowired
    private CourseService courseService;

    @Autowired
    private AcademicWarningMapper academicWarningMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private StudentServiceClient studentServiceClient;

    @Autowired
    private CounselorServiceClient counselorServiceClient;

    @Autowired
    private WarningRuleService warningRuleService;

    @Autowired
    private StudentService studentService;

    @Autowired
    private CounselorService counselorService;

    @Autowired
    private com.academic.mapper.TeacherClassApplicationMapper applicationMapper;

    @Autowired
    private com.academic.mapper.CounselorClassApplicationMapper counselorClassApplicationMapper;

    @Autowired
    private ClassesMapper classesMapper;

    @Autowired
    private com.academic.mapper.ScoreAppealMapper scoreAppealMapper;

    // 发通知共用实现（id 空间转换 + 偏好闸 + 落库），见 academic-common。
    // 此前这里自带一个 NotificationMapper 手写 insert，已收敛掉 —— 两处口径必然漂移。
    @Autowired
    private com.academic.common.notify.StudentNotifier studentNotifier;

    @Autowired
    private TeacherProfileMapper teacherProfileMapper;

    @Autowired
    private ExportHistoryMapper exportHistoryMapper;

    @Autowired
    private StudentProfileMapper studentProfileMapper;
    
    @Autowired
    private StudentProfileService studentProfileService;

    @Autowired
    private StudentImportService studentImportService;
    
    @Autowired
    private CounselorProfileMapper counselorProfileMapper;

    @Autowired
    private CollegeAdminProfileMapper collegeAdminProfileMapper;


    @Autowired
    private ScoreRecordMapper scoreRecordMapper;

    @Autowired
    private com.academic.mapper.ScoreMapper scoreMapper;

    /** 成绩聚合统计（按课程/按班级）。与 {@code CollegeAdminController} 共用同一个 mapper，
     *  报表路径也从这里取数，避免出现第二套及格率/平均分算法。*/
    @Autowired
    private ScoreStatMapper scoreStatMapper;

    @Autowired
    private ScoreRecordService scoreRecordService;

    /**
     * 把一条录入记录同步进 {@code scores}（权威成绩源）。
     *
     * <p>查重键是 (student_id, course_id, semester) —— 带学期才能让重修成为新的一行，
     * 而不是覆盖旧学期成绩。学期未提供时兜底为该生已有的最大学期序号，
     * 避免新记录比历史记录更旧而被"取最新一次成绩"的口径丢弃。
     *
     * <p>只覆盖调用方明确提供的字段。{@code final_score} 缺失时用总评成绩补齐：
     * 该列为空会被"期末未录入"口径整体排除，导入的成绩就永远不会参与预警。
     *
     * <p>折算部分走 {@link com.academic.common.util.ScoreSyncSupport#applyToAuthoritative}，
     * 与教师端录入共用同一份实现。此前这里自己写了一份，并且把等级/绩点的重算
     * 包在 {@code if (score.getGrade() == null)} 里 —— 更新一条已有成绩时旧值非空
     * 就会跳过重算，于是导入一个修正后的分数，{@code score_total} 变了、
     * {@code grade}/{@code grade_point} 还停在旧值上，
     * 把已经统一好的口径重新写歪。
     */
    private void syncToScores(ScoreRecord sr) {
        if (sr.getStudentId() == null || sr.getCourseId() == null) return;

        String semester = sr.getSemester();
        if (semester == null || semester.trim().isEmpty()) {
            Integer max = scoreMapper.selectMaxSemester(sr.getStudentId());
            semester = String.valueOf(max != null ? max : 1);
        }

        Score existing = scoreMapper.selectByStudentCourseSemester(
                sr.getStudentId(), sr.getCourseId(), semester);
        boolean isNew = existing == null;
        Score score = isNew ? new Score() : existing;
        score.setStudentId(sr.getStudentId());
        score.setCourseId(sr.getCourseId());
        score.setSemester(semester);

        com.academic.common.util.ScoreSyncSupport.applyToAuthoritative(
                score, sr.getScore(), sr.getRegularScore(), sr.getFinalScore());
        if (sr.getYear() != null) {
            score.setYear(sr.getYear());
        }

        if (isNew) {
            score.setCreatedAt(LocalDateTime.now());
            scoreMapper.insert(score);
        } else {
            scoreMapper.updateById(score);
        }
    }


    @Autowired
    private MessageServiceClient messageServiceClient;

    @Autowired
    private CourseServiceClient courseServiceClient;

    @Autowired
    private ReportTemplateService reportTemplateService;

    @Value("${export.dir:./exports}")
    private String exportDir;
    
    @Value("${template.dir:./templates}")
    private String templateDir;

    // Service availability tracking
    private volatile boolean courseServiceAvailable = true;
    private volatile boolean warningServiceAvailable = true;
    private volatile long courseServiceLastFailedTime = 0;
    private volatile long warningServiceLastFailedTime = 0;
    private static final long SERVICE_RETRY_INTERVAL = 60000; // 1 minute

    /**
     * 系统动态（管理端首页「系统动态」块）。
     *
     * <p>2026-10-01 第二十四轮：这个方法此前直接 {@code return ApiResponse.success(new ArrayList<>())}
     * —— 一个**永远返回空数组**的桩。小程序 {@code pages/admin/dashboard.vue} 那块「系统动态」
     * 因此一直挂着一个写死的空态，旁边还留了一句"实测恒为空数组、没有写入方"的注解。
     *
     * <p>这次改成从**三张真有写入方的表**里取最近发生的事，按时间倒序合并：
     * <ul>
     *   <li>{@code login_logs}（写入方是 user-service 的 {@code LoginLogService}，每次登录写一条）→「X 登录系统」</li>
     *   <li>{@code academic_warning}（预警引擎/定时任务写）→「系统生成学业预警」</li>
     *   <li>{@code export_history}（本类各导出方法写）→「导出 X 数据」</li>
     * </ul>
     * 刻意**不**用 {@code audit_logs}/{@code security_logs}：{@code security_logs} 至今
     * 0 行且全仓无写入方，接上去只会再造一个"恒空"；{@code audit_logs} 2026-10-02 起有了
     * 写入方（见 {@code AuditLogService}），但它有自己的专页（「操作日志」，
     * 读 {@code /admin/audit-logs}），混进"系统动态"里只会让两种日志互相淹没。
     *
     * <p>时间一律取各表自己的时间列，不掺 {@code LocalDateTime.now()} —— 否则每条动态
     * 都会显示成"刚刚"，看起来热闹，实际是在编时间。
     */
    @GetMapping("/activities")
    public ApiResponse<List<Map<String, Object>>> getActivities() {
        try {
            List<Map<String, Object>> activities = new ArrayList<>();

            for (LoginLog l : loginLogMapper.selectList(
                    new QueryWrapper<LoginLog>().orderByDesc("login_time").last("LIMIT 20"))) {
                activities.add(activity("login",
                        nz(l.getUsername()) + " 登录系统",
                        nz(l.getLocation()).isEmpty() ? nz(l.getDevice()) : nz(l.getLocation()),
                        l.getUsername(), l.getLoginTime()));
            }

            for (AcademicWarning w : academicWarningMapper.selectList(
                    new QueryWrapper<AcademicWarning>().orderByDesc("created_at").last("LIMIT 20"))) {
                activities.add(activity("warning",
                        "系统生成学业预警",
                        nz(w.getStudentName()) + " " + warningLevelText(w.getWarningLevel()),
                        null, w.getCreatedAt()));
            }

            for (ExportHistory e : exportHistoryMapper.selectList(
                    new QueryWrapper<ExportHistory>().orderByDesc("created_at").last("LIMIT 20"))) {
                activities.add(activity("export",
                        "导出" + exportTypeText(e.getDataType()) + "数据",
                        nz(e.getFileName()) + "（" + (e.getRecordCount() == null ? 0 : e.getRecordCount()) + " 条）",
                        null, e.getCreatedAt()));
            }

            activities.sort(Comparator.comparing(
                    (Map<String, Object> a) -> (LocalDateTime) a.get("createdAt"),
                    Comparator.nullsLast(Comparator.reverseOrder())));
            List<Map<String, Object>> top = activities.size() > 20
                    ? new ArrayList<>(activities.subList(0, 20))
                    : activities;
            return ApiResponse.success(top);
        } catch (Exception e) {
            log.error("Get activities failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /** 系统动态的一行。{@code createdAt} 保持时间类型，由序列化层统一成 ISO 串。*/
    private static Map<String, Object> activity(String type, String title, String description,
                                                String userName, LocalDateTime createdAt) {
        Map<String, Object> a = new HashMap<>();
        a.put("type", type);
        a.put("title", title);
        a.put("description", description == null ? "" : description);
        // 预警/导出这两类没有"操作人"（系统行为），留空串而不是编一个用户名。
        a.put("userName", userName == null ? "" : userName);
        a.put("createdAt", createdAt);
        return a;
    }

    /** {@code export_history.data_type} → 中文。库里实测只有 users/warnings/students/colleges。*/
    private static String exportTypeText(String dataType) {
        if (dataType == null) {
            return "";
        }
        return switch (dataType) {
            case "users" -> "用户";
            case "warnings" -> "预警";
            case "students" -> "学生";
            case "colleges" -> "学院";
            case "scores" -> "成绩";
            case "courses" -> "课程";
            case "teachers" -> "教师";
            case "counselors" -> "辅导员";
            default -> dataType;
        };
    }

    @GetMapping("/statistics")
    @SentinelResource(value = "adminApi:getStatistics", fallback = "getStatisticsFallback")
    public ApiResponse<Map<String, Object>> getStatistics() {
        try {
            Map<String, Object> statistics = new HashMap<>();

            // 先获取本地统计数据（快速响应）
            long userCount = userService.count();
            long studentCount = studentProfileService.count();  // 从 student_profile 表统计，比 users.role=1 更准确
            
            QueryWrapper<User> teacherQuery = new QueryWrapper<>();
            teacherQuery.eq("role", 2);
            long teacherCount = userService.count(teacherQuery);
            
            QueryWrapper<User> counselorQuery = new QueryWrapper<>();
            counselorQuery.eq("role", 3);
            long counselorCount = userService.count(counselorQuery);
            
            long courseCount = courseService.count();
            long collegeCount = collegeService.count();
            long majorCount = majorService.count();
            long ruleCount = warningRuleService.count();

            statistics.put("totalUsers", userCount);
            statistics.put("totalStudents", studentCount);
            statistics.put("totalTeachers", teacherCount);
            statistics.put("totalCounselors", counselorCount);
            statistics.put("totalCourses", courseCount);
            statistics.put("totalColleges", collegeCount);
            statistics.put("totalMajors", majorCount);
            statistics.put("ruleCount", ruleCount);

            // 直接查本地 academic_warning 表统计
            try {
                List<AcademicWarning> warnings = academicWarningMapper.selectList(null);
                statistics.put("totalWarnings", warnings.size());
                int redCount = 0, yellowCount = 0, lowCount = 0, processedCount = 0;
                for (AcademicWarning w : warnings) {
                    Integer level = w.getWarningLevel();
                    if (level != null) {
                        if (level >= 3) redCount++;
                        else if (level == 2) yellowCount++;
                        else lowCount++;
                    }
                    // 2026-10-01 第十九轮: 首页那四枚「严重/中度/轻度/已处理」徽章原来是从
                    // /admin/dashboard 的 recentWarnings(只取前 5 条)算的 —— 数据一超 5 条就少算。
                    // 这里给出全量的「已处理」计数, 与上面的分级计数同源。
                    if (w.getStatus() != null && w.getStatus() == 1) {
                        processedCount++;
                    }
                }
                statistics.put("redWarnings", redCount);
                statistics.put("yellowWarnings", yellowCount);
                statistics.put("lowWarnings", lowCount);
                statistics.put("processedWarnings", processedCount);
            } catch (Exception e) {
                log.warn("获取预警数据失败: {}", e.getMessage());
                statistics.put("totalWarnings", 0);
                statistics.put("redWarnings", 0);
                statistics.put("yellowWarnings", 0);
                statistics.put("lowWarnings", 0);
                statistics.put("processedWarnings", 0);
            }

            // 2026-10-01 第二十四轮：这里原本有两组**恒为字面量 0** 的字段，已删。
            //
            //  · todayStats{newWarnings, processedWarnings, pendingTasks, unreadMessages,
            //    onlineTeachers, onlineStudents} —— 后两项在库里**没有任何在线状态来源**
            //    （没有会话表、没有心跳表），前四项则要从没被任何人读过：PC 端
            //    AdminDashboard/AdminStatistics 只取上面那 12 个 key，小程序 api.js 的注释
            //    里也写明"todayStats.* 全是 0 —— 没有写入方"。
            //  · changeRates{student,course,user,warning}ChangeRate —— 环比要**历史快照**，
            //    库里没有这张表，四个 0 永远算不出来。小程序注释早有警示：
            //    "界面上不能画成'较上月 +0%'，那是在把'没数据'讲成'没有变化'"。
            //
            // 零读者 + 无数据源 ⇒ 删，不留一个看着像真值其实是常量的字段让后来人接。
            // 如果哪天要"今日统计"，正确的做法是**先建快照表**再算，而不是把 0 换个数。
            return ApiResponse.success(statistics);
        } catch (Exception e) {
            log.error("Get statistics failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    public ApiResponse<Map<String, Object>> getStatisticsFallback(Throwable t) {
        Map<String, Object> fallbackData = new HashMap<>();
        fallbackData.put("totalUsers", 0);
        fallbackData.put("totalStudents", 0);
        fallbackData.put("totalCourses", 0);
        fallbackData.put("totalColleges", 0);
        fallbackData.put("totalWarnings", 0);
        fallbackData.put("message", "adminApi:getStatistics API is degraded: " + t.getMessage());
        return ApiResponse.success(fallbackData);
    }

    /**
     * 获取待处理数量（班级申请、申诉等）
     */
    @GetMapping("/pending-requests")
    public ApiResponse<Map<String, Object>> getPendingRequests() {
        Map<String, Object> result = new HashMap<>();
        // 统计教师班级申请
        try {
            long teacherClassRequests = applicationMapper.selectCount(
                new QueryWrapper<>(new com.academic.entity.TeacherClassApplication()).eq("status", 0));
            result.put("teacherClassRequests", (int) teacherClassRequests);
        } catch (Exception e) { result.put("teacherClassRequests", 0); }
        // 统计辅导员班级申请
        try {
            long counselorClassRequests = counselorClassApplicationMapper.selectCount(
                new QueryWrapper<>(new com.academic.entity.CounselorClassApplication()).eq("status", 0));
            result.put("counselorClassRequests", (int) counselorClassRequests);
        } catch (Exception e) { result.put("counselorClassRequests", 0); }
        // 合并班级申请总数
        int teacherRequests = (int) result.getOrDefault("teacherClassRequests", 0);
        int counselorRequests = (int) result.getOrDefault("counselorClassRequests", 0);
        result.put("classRequests", teacherRequests + counselorRequests);
        // 2026-10-01 第十九轮: 原先是 `result.put("appeals", 0)` **写死** —— 管理员首页那张
        // 「成绩申诉」待办卡永远是 0, 而库里 score_appeals 真有 1 条 status=0。
        // 同方法里另外三个计数本来就是真查询, 只有它没查。
        try {
            result.put("appeals", (int) scoreAppealMapper.countPending());
        } catch (Exception e) { result.put("appeals", 0); }
        // 待处理预警数
        try {
            long pendingWarnings = academicWarningMapper.selectCount(
                new QueryWrapper<AcademicWarning>().eq("status", 0));
            result.put("pendingWarnings", (int) pendingWarnings);
        } catch (Exception e) { result.put("pendingWarnings", 0); }
        return ApiResponse.success(result);
    }

    // ==================== 成绩申诉审核（2026-10-02 第二十五轮新增） ====================

    /**
     * 成绩申诉列表。
     *
     * <p>此前全仓对 {@code score_appeals} **只有一条 UPDATE**（学生自己撤回 status=3），
     * 没有任何审核入口 —— 管理员在 PC 和小程序上都看不到待审申诉，库里那条 status=0
     * 永远处理不掉。这里补上列表 + 审核两个端点。
     *
     * @param status 可选：0 待处理 / 1 已通过 / 2 已驳回 / 3 已撤销；不传返回全部
     */
    @GetMapping("/appeals")
    public ApiResponse<List<Map<String, Object>>> listAppeals(
            @RequestParam(required = false) Integer status) {
        try {
            return ApiResponse.success(scoreAppealMapper.selectAppeals(status));
        } catch (Exception e) {
            log.error("List appeals failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 审核成绩申诉：通过（status=1）或驳回（status=2），并写审核意见 {@code reply}。
     *
     * <p>审核成功后给申诉学生写一条 {@code notifications} —— 兑现文档承诺的
     * 「申诉处理通知：管理员审核申诉后，通知申诉学生结果」。
     *
     * <p>两点刻意的取舍：
     * <ul>
     *   <li><b>不加 {@code @Transactional}</b>：审核是一条原子 UPDATE，通知是"尽力而为"的
     *       附加动作。若把两者放进一个事务、又在这里 catch 通知异常，事务会变成假事务
     *       （本仓在班级管理写路径上踩过）。分开写语义更诚实。</li>
     *   <li>通知的 {@code user_id} 是 {@code users.id}（有真外键），由
     *       {@code student_profile.user_id} 换过来；换不到就**只审不通知**并记 warn，
     *       绝不让通知失败把审核结果带崩。</li>
     * </ul>
     */
    @PostMapping("/appeals/{id}/review")
    public ApiResponse<Map<String, Object>> reviewAppeal(@PathVariable Long id,
                                                         @RequestBody Map<String, Object> body) {
        Integer status = toInt(body.get("status"));
        if (status == null || (status != 1 && status != 2)) {
            throw new IllegalArgumentException("status 只能是 1（通过）或 2（驳回）");
        }
        String reply = body.get("reply") == null ? null : String.valueOf(body.get("reply")).trim();
        if (reply != null && reply.isEmpty()) {
            reply = null;
        }

        Map<String, Object> appeal = scoreAppealMapper.selectAppealById(id);
        if (appeal == null) {
            throw new IllegalArgumentException("申诉不存在: " + id);
        }
        int rows = scoreAppealMapper.reviewAppeal(id, status, reply);
        if (rows == 0) {
            throw new IllegalArgumentException("该申诉不是待处理状态（可能已被审核或撤回）: " + id);
        }

        String courseName = appeal.get("courseName") == null
                ? "相关课程" : String.valueOf(appeal.get("courseName"));
        try {
            Long profileId = toLong(appeal.get("studentId"));
            if (profileId != null) {
                // 共用发通知实现：student_profile.id → users.id 的转换、偏好闸、落库都在里面。
                // 用 Category.APPEAL：申诉结果是学生自己发起事件的回执，不受"系统消息"开关影响
                // （关掉开关的学生不该连自己申诉的结果都收不到）。
                studentNotifier.notifyStudent(profileId,
                        com.academic.common.notify.NotificationPreferences.Category.APPEAL,
                        null,
                        status == 1 ? "成绩申诉已通过" : "成绩申诉已驳回",
                        (status == 1 ? "你的成绩申诉已通过：" : "你的成绩申诉未通过：") + courseName
                                + (reply == null ? "" : "\n审核意见：" + reply),
                        null);
            }
        } catch (Exception e) {
            log.warn("申诉审核后写通知失败（不影响审核结果）: {}", e.getMessage());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        result.put("status", status);
        result.put("reply", reply);
        auditLogService.record("REVIEW", "APPEAL", id,
                (status == 1 ? "通过" : "驳回") + "成绩申诉（课程：" + courseName + "）");
        return ApiResponse.success(result);
    }

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> getDashboard() {
        try {
            Map<String, Object> dashboard = new HashMap<>();

            dashboard.put("totalUsers", userService.count());
            dashboard.put("totalStudents", studentService.count());
            dashboard.put("totalColleges", collegeService.count());
            dashboard.put("totalMajors", majorService.count());
            dashboard.put("totalCourses", courseService.count());

            // 直接查本地 academic_warning 表
            try {
                List<AcademicWarning> warnings = academicWarningMapper.selectList(null);
                dashboard.put("totalWarnings", warnings.size());
                dashboard.put("recentWarnings", warnings.size() > 5 ? warnings.subList(0, 5) : warnings);
            } catch (Exception e) {
                log.warn("获取预警失败: {}", e.getMessage());
                dashboard.put("totalWarnings", 0);
                dashboard.put("recentWarnings", List.of());
            }

            return ApiResponse.success(dashboard);
        } catch (Exception e) {
            log.error("Get dashboard failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    // 2026-10-04（W15）删：`GET /admin/users`（裸路径）。
    //   它声明了 `collegeId` 参数却**从不用** —— 方法体里只按 `role` 过滤，传学院也返回全部，
    //   是个会骗人的过滤器。而且它已经被 `GET /admin/users/page`（W5.7，`userIdsInCollege`
    //   真正把学院翻成 id 集合、并富集 collegeName/status）完全取代；前端 `adminAPI.getUsers`
    //   与小程序的 `getUsers` 打的都是 `/users/page`，探针 `admin-probe.mjs` 验的也是 `/users/page`。
    //   零调用方留着只会让下一个读签名的人以为按学院能筛。`/users/all` 仍在用
    //   `getAllUsersFallback`，故该 fallback 保留。

    @GetMapping("/users/all")
    @SentinelResource(value = "adminApi:getUsersAll", fallback = "getAllUsersFallback")
    public ApiResponse<List<User>> getAllUsersList() {
        List<User> users = userService.list();
        stripPasswords(users);
        return ApiResponse.success(users);
    }

    /** 列表类读接口一律去掉口令哈希，理由见 getUserById 的注释。 */
    private void stripPasswords(List<User> users) {
        if (users == null) {
            return;
        }
        for (User u : users) {
            if (u != null) {
                u.setPassword(null);
            }
        }
    }

    public ApiResponse<List<User>> getAllUsersFallback(Throwable t) {
        log.warn("adminApi:getUsers API is degraded: {}", t.getMessage());
        return ApiResponse.error("adminApi:getUsers API is degraded: " + t.getMessage());
    }

    @GetMapping("/users/{id}")
    public ApiResponse<User> getUserById(@PathVariable Long id) {
        User user = userService.getById(id);
        if (user != null) {
            // 不回传口令哈希。这里返回的是整个 User 实体，password 字段就是库里那串
            // BCrypt 哈希 —— 一个读接口把凭据材料发给前端，不管前端用不用都是不对的
            // （这与"不要调 /users/by-username/{name}，它会返回含口令哈希的原始实体"
            //  是同一条规矩）。PC 的 AdminSettings.vue 只读 createdAt，去掉无影响；
            // 校验口令请走登录接口，不需要读哈希。
            user.setPassword(null);
            return ApiResponse.success(user);
        }
        return ApiResponse.notFound("User not found");
    }

    // ⚠️ 两个写端点（本方法与 deleteUser）**不要**挂 @SentinelResource 的 fallback。
    //    实测（2026-09-30）：Sentinel 的切面落在事务边界**内侧**，与注解书写顺序无关。
    //    fallback 一旦接住异常，事务拦截器看到的就是"正常返回"（日志里打的是
    //    "Completing transaction for [...]"，异常路径才会打 "Initiating transaction rollback"），
    //    于是把前半截写入照常提交 —— 用一个 student_id 超长的用户名建号，
    //    users 插入成功、student_profile 插入失败，结果 users 那行照样留在库里，
    //    而 @Transactional 的存在让人以为不会。去掉 fallback，异常才能抵达事务拦截器。
    //    限流不受影响：BlockException 归 blockHandler 管，这两个端点都没有 blockHandler。
    @PostMapping("/users")
    @Transactional
    @SentinelResource(value = "adminApi:createUser")
    public ApiResponse<String> createUser(@RequestBody User user) {
        try {
            // ⚠️ 原来是 userService.save(user) —— MyBatis-Plus 的裸插入，绕过了
            //    UserServiceImpl.createUser()，于是三件事都没做：
            //      1) 口令不加密，库里落下明文。**但账号是能登的**：登录
            //         （user-service 的 UserServiceImpl.login）对不以 $2a$ 开头的口令
            //         有一条"明文兼容"分支，比中后顺手升级成 BCrypt —— 所以问题不是
            //         "登不上"，而是"首次登录之前，users 表里存着可读明文"，
            //         这与本工程"库里只有哈希"的口径不符；
            //      2) status 不置 1：登录对 null 是放行的（`getStatus() != null && != 1`），
            //         同样不挡登录，但账号会以"状态未定"存着，列表里状态列是空的；
            //      3) 不查重名：重名直接抛唯一键异常，报错对管理员没有意义。
            //    createUser() 这三件都做了，改调它。
            //
            //    @Transactional 是补上的：这个方法会写 users + 可能写一张 profile 表，
            //    没有事务时后半段失败会留下"接口报错、账号已经建出来了"的半截状态 ——
            //    实测过一次：teacher_profile.college_id 是 NOT NULL，插入失败抛 500，
            //    而 users 里那个账号已经落库了（id=76），管理员看到报错会再建一次，
            //    于是撞重名。deleteUser 早就是 @Transactional，这里对齐。
            boolean ok = userService.createUser(user);
            if (!ok) {
                return ApiResponse.error("用户名已存在：" + user.getUsername());
            }

            createProfileIfNeeded(user);

            log.info("User created: id={}, username={}, role={}", user.getId(), user.getUsername(), user.getRole());
            auditLogService.record("CREATE", "USER", user.getId(),
                    "新建账号 " + user.getUsername() + "（角色 " + getRoleNameText(user.getRole()) + "）");
            return ApiResponse.success("User created successfully");
        } catch (Exception e) {
            log.error("Create user failed: {}", e.getMessage());
            // 抛出去让事务回滚：返回 error 而不抛，@Transactional 会以为一切正常照常提交
            throw new RuntimeException("Create user failed: " + e.getMessage(), e);
        }
    }

    /**
     * 给新建账号补一张最小 profile 行（只在缺的时候补）。
     *
     * 口径：**只在该角色的必填列都拿得到时才建**，拿不到就不建、也不猜。
     * 各表 NOT NULL 且无默认值的列（实测 information_schema）：
     *   student_profile        user_id, student_id, name   → 新建时全都有（用户名即学号）
     *   teacher_profile        user_id, **college_id**     → 建号表单里没有学院 → 不建
     *   counselor_profiles     user_id                     → 有（编号用用户名兜）
     *   college_admin_profile  user_id, name, **college_id** → 同上 → 不建
     * 也就是"教师/学院管理员新建出来的账号只有账号、没有资料行"，
     * 学院要由管理员在资料里补 —— 这比造一个 college_id=0 的假资料要好：
     * 0 不是任何学院，查学院时会静默漏掉这些人。
     * users.username 就是学号/工号/编号（学生 2023020616、教师 10001 都是这么存的）。
     */
    private void createProfileIfNeeded(User user) {
        if (user.getId() == null || user.getRole() == null) {
            return;
        }
        String name = user.getName() != null ? user.getName() : user.getUsername();
        if (user.getRole() == 1) {
            QueryWrapper<StudentProfile> exists = new QueryWrapper<StudentProfile>().eq("user_id", user.getId());
            if (studentProfileMapper.selectCount(exists) == 0) {
                StudentProfile profile = new StudentProfile();
                profile.setUserId(user.getId());
                profile.setStudentId(user.getUsername());
                profile.setName(name);
                studentProfileMapper.insert(profile);
            }
        } else if (user.getRole() == 3) {
            QueryWrapper<CounselorProfile> exists = new QueryWrapper<CounselorProfile>().eq("user_id", user.getId());
            if (counselorProfileMapper.selectCount(exists) == 0) {
                CounselorProfile profile = new CounselorProfile();
                profile.setUserId(user.getId());
                // 编号列有唯一约束，用 users.username 兜（'C53' 那种命名是种子数据的手工编号，
                // 新建账号没有编号来源，用账号本身最不容易撞）
                profile.setCounselorId(user.getUsername());
                profile.setStaffId(user.getUsername());
                profile.setName(name);
                profile.setStatus(1);
                counselorProfileMapper.insert(profile);
            }
        }
        // 教师(2)/学院管理员(5) 的 profile 必填列里有 college_id，建号入参里没有学院，故不建。
    }

    @PutMapping("/users/{id}")
    @SentinelResource(value = "adminApi:updateUser", fallback = "updateUserFallback")
    public ApiResponse<String> updateUser(@PathVariable Long id, @RequestBody User user) {
        try {
            user.setId(id);

            // ⚠️ 密码必须单独处理，不能跟着 updateById 一起写，两条理由：
            //    1) 空串是**真锁死**。表单上写着"留空表示不修改密码"（PC AdminUsers.vue
            //       的编辑弹窗），但留空提交上来的是空字符串而不是 null，而 MyBatis-Plus
            //       只跳过 null 字段 —— 于是 password='' 落库。登录时 `password.isEmpty()`
            //       挡的是请求参数，比对那一步看到 '' 不以 $2a$ 开头就走明文兼容分支，
            //       拿真实口令去 equals('') 永远为 false：这个账号此后谁都登不上，
            //       且没有任何提示指向"密码字段被清空了"。
            //    2) 非空明文会以明文落库。登录有明文兼容分支，能登进去并自愈成哈希，
            //       但在那之前库里存的是可读明文，不符合"库里只有哈希"的口径。
            //    口径：非空 → 加密后写入；空串或未传 → 不碰这个字段。
            String rawPassword = user.getPassword();
            if (rawPassword == null || rawPassword.trim().isEmpty()) {
                user.setPassword(null);   // MyBatis-Plus 默认不更新 null 字段
            } else if (!rawPassword.startsWith("$2a$")
                    && !rawPassword.startsWith("$2b$")
                    && !rawPassword.startsWith("$2y$")) {
                // 已经是 BCrypt 串的就原样写回，避免二次加密（表单回显哈希的场景）
                user.setPassword(passwordEncoder.encode(rawPassword));
            }

            // 2026-10-03（W7）：读影响行数 —— 0 行说明该 id 不存在，如实回 404。
            // 同时**不能留审计**：什么都没改动，"修改账号"的痕就是假的。
            if (!userService.updateUser(user)) {
                return ApiResponse.notFound("User not found");
            }
            log.info("User updated: id={}, username={}, passwordChanged={}",
                    id, user.getUsername(), user.getPassword() != null);
            // 口令是否被改也记进审计：这是最该留痕的一类操作。
            auditLogService.record("UPDATE", "USER", id,
                    "修改账号 " + user.getUsername()
                            + (user.getPassword() != null ? "（含重置口令）" : ""));
            return ApiResponse.success("User updated successfully");
        } catch (Exception e) {
            log.error("Update user failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    public ApiResponse<String> updateUserFallback(Long id, User user, Throwable t) {
        return ApiResponse.error("adminApi:updateUser API is degraded: " + t.getMessage());
    }

    @Transactional
    @DeleteMapping("/users/{id}")
    // fallback 同理去掉 —— 见上面 createUser 上方的说明。
    @SentinelResource(value = "adminApi:deleteUser")
    public ApiResponse<String> deleteUser(@PathVariable Long id) {
        try {
            User user = userService.getById(id);
            if (user == null) {
                return ApiResponse.notFound("User not found");
            }

            // 动手之前先数一遍关联数据。`users` 被 6 张表引用、`student_profile` 被 9 张、
            // `teacher_profile` 被 teacher_course 引用，**全是 NO ACTION 外键**；而本方法
            // 只清理四张 profile 表，其余一律不管 —— 所以只要还剩一行，整笔删除必然中途失败。
            // 与其让管理员吃一个 HTTP 500「系统内部错误」（既不知原因，也不知能不能修），
            // 不如直接说清被什么挡住。2026-09-30 实测：35 个演示账号里 34 个过不了这一关
            // —— 学生卡在 student_profile 那一步，管理员/辅导员/学院管理员卡在账号级那一步。
            String blocking = describeBlockingReferences(userMapper.countReferences(id));
            if (blocking != null) {
                return ApiResponse.error("该账号仍有关联数据，不能删除：" + blocking
                        + "。可改为「停用」；如需彻底删除，要先清理这些数据。");
            }

            // 这里原来还有一次 messageServiceClient.deleteMessagesByUserId(id)（best-effort），已删。
            // 它删的正是 messages 里 sender_id/receiver_id 命中该账号的行，与上面 countReferences
            // 的谓词逐字一致 —— 能走到这一行就说明那两列一行都没有，它必然是空操作。
            // 而 message-service 未启动时它要阻塞到 Feign 超时：实测 selectById 到
            // DELETE FROM student_profile 之间隔了 12.1 秒，白白给每次删号加上这个等待。

            if (user.getRole() == 1) {
                QueryWrapper<StudentProfile> studentQuery = new QueryWrapper<>();
                studentQuery.eq("user_id", id);
                studentProfileMapper.delete(studentQuery);
            } else if (user.getRole() == 2) {
                QueryWrapper<TeacherProfile> teacherQuery = new QueryWrapper<>();
                teacherQuery.eq("user_id", id);
                teacherProfileMapper.delete(teacherQuery);
            } else if (user.getRole() == 3) {
                QueryWrapper<CounselorProfile> counselorQuery = new QueryWrapper<>();
                counselorQuery.eq("user_id", id);
                counselorProfileMapper.delete(counselorQuery);
            } else if (user.getRole() == 5) {
                // 学院管理员原来**不在**这里的清理名单里：删掉账号后
                // college_admin_profile 会留下一行 user_id 指向不存在的账号。
                // 它本身不报错（按学院查账号是 `users in (ids)`，多出来的 id 匹配不到任何人），
                // 但会污染 userIdsInCollege 的结果集，也让"删干净"这句话不成立。
                QueryWrapper<CollegeAdminProfile> adminQuery = new QueryWrapper<>();
                adminQuery.eq("user_id", id);
                collegeAdminProfileMapper.delete(adminQuery);
            }
            // 角色 4（系统管理员）没有对应的 profile 表，只删账号行。

            userService.removeById(id);
            auditLogService.record("DELETE", "USER", id,
                    "删除账号 " + user.getUsername() + "（角色 " + getRoleNameText(user.getRole()) + "）");
            return ApiResponse.success("User deleted successfully");
        } catch (Exception e) {
            log.error("Delete user failed: {}", e.getMessage());
            // 必须抛出去：原来是 return ApiResponse.error(...)，异常根本没离开方法，
            // @Transactional 看到的是"正常返回"，于是"profile 删了、账号还在"这类
            // 半截状态会照常提交 —— 这里比 createUser 更直白，连 Sentinel 都不需要。
            throw new RuntimeException("Delete user failed: " + e.getMessage(), e);
        }
    }

    /**
     * 把 {@link com.academic.mapper.UserMapper#countReferences} 的分列计数翻成
     * 「成绩 16 条、预警 1 条」这样一句话；各类别全为 0 时返回 {@code null}（表示可以删）。
     *
     * 顺序按管理员的思路排：学生学业数据在前，账号级数据在后 —— 前者是"这个学生有成绩，
     * 当然删不掉"，后者更接近"这账号有登录痕迹"。别按 SQL 里的列顺序排，那样第一项会是
     * scores，而一个只有 security_logs 的管理员账号会显示得像是没问题。
     */
    private String describeBlockingReferences(Map<String, Object> counts) {
        if (counts == null || counts.isEmpty()) {
            return null;
        }
        String[][] labels = {
                {"scores", "成绩"}, {"warnings", "预警"}, {"enrollments", "选课记录"},
                {"appeals", "成绩申诉"}, {"assistancePlans", "帮扶计划"},
                {"studentSettings", "学生设置"}, {"subscriptions", "订阅偏好"},
                {"benchmarks", "对标分析"}, {"feedbacks", "反馈"},
                {"teacherCourses", "授课安排"},
                {"userSettings", "账号设置"}, {"notifications", "通知"}, {"messages", "消息"},
                {"securityLogs", "安全日志"}, {"auditLogs", "审计日志"},
                {"adminProfile", "管理员档案"},
        };
        List<String> parts = new ArrayList<>();
        for (String[] label : labels) {
            Object raw = counts.get(label[0]);
            long n = (raw instanceof Number) ? ((Number) raw).longValue() : 0L;
            if (n > 0) {
                parts.add(label[1] + " " + n + " 条");
            }
        }
        // 计数列全为 0 却走了进来，只可能是 SQL 与 labels 对不上（比如加了新列忘了写标签）。
        // 这种情况宁可放行删除、让外键自己去报错，也不要编一句"仍有关联数据："却列不出任何东西。
        return parts.isEmpty() ? null : String.join("、", parts);
    }

    @PostMapping("/users/{id}/reset-password")
    public ApiResponse<String> resetPassword(@PathVariable Long id) {
        try {
            User user = userService.getById(id);
            if (user == null) {
                return ApiResponse.notFound("User not found");
            }
            // ⚠️ 这里原来生成随机密码，然后提示「新密码已发送至用户注册邮箱」。
            //    但本工程**没有任何邮件发送代码**，密码又是 BCrypt 单向哈希
            //    （服务端自己取不回明文），也没有"管理员指定新密码"的接口 ——
            //    三重叠加的结果是：点了重置，账号就被永久锁死，提示还在说已经发邮件了。
            //    本库全部账号本来就用统一演示口令 123456（见 db_backups 种子脚本，
            //    批量导入用户那条路径的默认值也是 123456），所以这里重置成同一个口令，
            //    并把提示写成实话。若要恢复"随机密码"语义，需先补一个把新密码
            //    交付给用户/管理员的通道（邮件服务或一次性回显），否则不可用。
            final String defaultPassword = "123456";
            user.setPassword(passwordEncoder.encode(defaultPassword));
            userService.updateById(user);
            log.info("Reset password to demo default for user id={}, username={}", id, user.getUsername());
            auditLogService.record("RESET_PASSWORD", "USER", id, "重置账号 " + user.getUsername() + " 的登录口令");
            return ApiResponse.success("密码已重置为演示口令 123456（系统无邮件服务，特此明示）");
        } catch (Exception e) {
            log.error("Reset password failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }
    
    // generateRandomPassword() 已删除：它是给"随机重置密码"用的，
    // 而随机密码没有任何交付通道（无邮件服务、无一次性回显），只会锁死账号。
    // 恢复随机语义前，先补上把新密码交给用户/管理员的通道。

    @PostMapping("/users/{id}/toggle-status")
    public ApiResponse<String> toggleStatus(@PathVariable Long id) {
        try {
            User user = userService.getById(id);
            if (user == null) {
                return ApiResponse.notFound("User not found");
            }
            // 置反：非活跃→活跃, 活跃→禁用
            if (user.getStatus() == 1) {
                user.setStatus(0);
            } else {
                user.setStatus(1);
            }
            userService.updateById(user);
            auditLogService.record("TOGGLE_STATUS", "USER", id,
                    (user.getStatus() == 1 ? "启用" : "停用") + "账号 " + user.getUsername());
            return ApiResponse.success(user.getStatus() == 1 ? "User enabled" : "User disabled");
        } catch (Exception e) {
            log.error("Toggle status failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PutMapping("/users/{id}/approve")
    public ApiResponse<String> approveUser(@PathVariable Long id) {
        try {
            User user = userService.getById(id);
            if (user == null) {
                return ApiResponse.notFound("User not found");
            }
            user.setStatus(1); // 审批通过，设为活跃
            userService.updateById(user);
            log.info("User approved: id={}, username={}", id, user.getUsername());
            auditLogService.record("APPROVE", "USER", id, "审批通过账号 " + user.getUsername());
            return ApiResponse.success("审批通过，用户已激活");
        } catch (Exception e) {
            log.error("Approve user failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 操作日志（审计日志）分页列表 —— 管理端「操作日志」视图的数据源。
     *
     * <p>2026-10-02 之前 {@code audit_logs} 零写入方、也没有任何列表读取口，只有
     * 「用户详情」页一个 {@code COUNT(*)}。现在写入方是 {@link com.academic.service.AuditLogService}
     * （账号增删改/重置口令/启停/审批、预警规则增删改、预警处理、申诉审核各记一条），
     * 读取口是这里。
     *
     * <p>空串过滤条件归一成 {@code null}（前端下拉"全部"提交的是空串，直接塞进
     * {@code <if test='x != null'>} 会变成一个永远不匹配的 {@code = ''}）。
     */
    @GetMapping("/audit-logs")
    public ApiResponse<Map<String, Object>> getAuditLogs(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String actionType) {
        try {
            String entity = blankToNull(entityType);
            String action = blankToNull(actionType);
            int p = (page == null || page < 1) ? 1 : page;
            int s = (size == null || size < 1) ? 20 : Math.min(size, 200);

            long total = auditLogMapper.countWithFilter(entity, action);
            List<Map<String, Object>> items = auditLogMapper.selectPageWithActor(
                    entity, action, (p - 1) * s, s);

            Map<String, Object> result = new HashMap<>();
            result.put("total", total);
            result.put("items", items);
            result.put("page", p);
            result.put("size", s);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Get audit logs failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    private static String blankToNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() || "all".equalsIgnoreCase(trimmed) ? null : trimmed;
    }

    @GetMapping("/login-logs/{userId}")
    public ApiResponse<List<Map<String, Object>>> getLoginLogs(@PathVariable Long userId) {
        try {
            QueryWrapper<com.academic.entity.LoginLog> wrapper = new QueryWrapper<>();
            wrapper.eq("user_id", userId).orderByDesc("login_time").last("LIMIT 20");
            List<com.academic.entity.LoginLog> logs = loginLogMapper.selectList(wrapper);
            List<Map<String, Object>> result = new ArrayList<>();
            for (com.academic.entity.LoginLog log : logs) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", log.getId());
                item.put("loginTime", log.getLoginTime());
                item.put("ipAddress", log.getIpAddress());
                item.put("device", log.getDevice());
                item.put("location", log.getLocation());
                item.put("status", log.getStatus());
                result.add(item);
            }
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Get login logs failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 账号分页。
     *
     * <p>2026-10-02（W5.7）新增 {@code status} 过滤：此前**只能按 role / collegeId 筛**，
     * 于是「待审批账号有几个」这个问题在接口层答不出来（小程序 admin 首页那格只能写死 0）。
     * {@code status=0} 就是待审批，{@code total} 直接可当计数用。
     */
    @GetMapping("/users/page")
    @SentinelResource(value = "adminApi:getUsersByPage", fallback = "getUsersByPageFallback")
    public ApiResponse<Map<String, Object>> getUsersByPage(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long collegeId,
            @RequestParam(required = false) Integer role,
            @RequestParam(required = false) Integer status) {
        try {
            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            if (role != null) {
                queryWrapper.eq("role", role);
            }
            if (status != null) {
                queryWrapper.eq("status", status);
            }

            // 按学院过滤必须落进 SQL（见 userIdsInCollege 的注释）。
            // 之前是在内存里滤这一页，导致 total 与分页都错：
            // 实测 collegeId=2 返回 total=35（全库用户数）、items 只有 1 条。
            if (collegeId != null) {
                Set<Long> collegeUserIds = userIdsInCollege(collegeId);
                if (collegeUserIds.isEmpty()) {
                    // 该学院一个账号都没有。不能把空集合交给 in()（SQL 会变成 in () 语法错），
                    // 直接返回空页；total=0 也是这里唯一正确的答案。
                    Map<String, Object> emptyPage = new HashMap<>(2);
                    emptyPage.put("total", 0L);
                    emptyPage.put("items", List.of());
                    return ApiResponse.success(emptyPage);
                }
                queryWrapper.in("id", collegeUserIds);
            }

            // 待审批用户置顶
            queryWrapper.orderByAsc("status").orderByDesc("id");

            long total = userService.count(queryWrapper);
            int start = (page - 1) * size;
            List<User> users = userService.list(queryWrapper.last("LIMIT " + start + ", " + size));

            List<Map<String, Object>> enhancedUsers = new ArrayList<>(users.size());
            for (User user : users) {
                Map<String, Object> userMap = new HashMap<>();
                userMap.put("id", user.getId());
                userMap.put("username", user.getUsername());
                userMap.put("name", user.getName());
                userMap.put("email", user.getEmail());
                userMap.put("role", user.getRole());
                userMap.put("status", user.getStatus());
                userMap.put("createdAt", user.getCreatedAt());
                // 2026-10-01 第二十一轮: 小程序「账号详情」有「手机」与「最近改动」两行,
                // 读的是 phone / updatedAt, 而这里从前只给到 createdAt ⇒ 手机恒 '--'、
                // 最近改动恒空。users 表这两列都在, 直接带上, 不用前端去猜。
                userMap.put("phone", user.getPhone());
                userMap.put("updatedAt", user.getUpdatedAt());

                enhanceUserProfile(user, userMap);
                enhancedUsers.add(userMap);
            }

            Map<String, Object> result = new HashMap<>(2);
            result.put("total", total);
            result.put("items", enhancedUsers);

            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Get users failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }
    
    /**
     * 某学院下所有账号的 users.id 集合。
     *
     * 为什么要绕 profile 表：学院只挂在四张 profile 表上（student_profile /
     * teacher_profile / counselor_profiles / college_admin_profile），
     * users 表自己**没有** college_id 列，所以"按学院查账号"只能先把学院翻译成 id 集合。
     *
     * ⚠️ 表名注意：辅导员的表是 `counselor_profiles`（复数），学院管理员的是
     * `college_admin_profile`（单数）—— 差异来自 @TableName，别按命名习惯猜。
     *
     * 收 role 1/2/3/5，与下面 enhanceUserProfile 能填出 collegeId 的角色一致；
     * role=4 系统管理员不属于任何学院，所以永远不在结果里。
     */
    private Set<Long> userIdsInCollege(Long collegeId) {
        Set<Long> ids = new HashSet<>();

        List<StudentProfile> students = studentProfileMapper.selectList(
                new QueryWrapper<StudentProfile>().select("user_id").eq("college_id", collegeId));
        for (StudentProfile p : students) {
            if (p.getUserId() != null) {
                ids.add(p.getUserId());
            }
        }

        List<TeacherProfile> teachers = teacherProfileMapper.selectList(
                new QueryWrapper<TeacherProfile>().select("user_id").eq("college_id", collegeId));
        for (TeacherProfile p : teachers) {
            if (p.getUserId() != null) {
                ids.add(p.getUserId());
            }
        }

        List<CounselorProfile> counselors = counselorProfileMapper.selectList(
                new QueryWrapper<CounselorProfile>().select("user_id").eq("college_id", collegeId));
        for (CounselorProfile p : counselors) {
            if (p.getUserId() != null) {
                ids.add(p.getUserId());
            }
        }

        List<CollegeAdminProfile> collegeAdmins = collegeAdminProfileMapper.selectList(
                new QueryWrapper<CollegeAdminProfile>().select("user_id").eq("college_id", collegeId));
        for (CollegeAdminProfile p : collegeAdmins) {
            if (p.getUserId() != null) {
                ids.add(p.getUserId());
            }
        }

        return ids;
    }

    private void enhanceUserProfile(User user, Map<String, Object> userMap) {
        if (user.getRole() == 1) {
            try {
                QueryWrapper<StudentProfile> studentQuery = new QueryWrapper<>();
                studentQuery.eq("user_id", user.getId());
                StudentProfile studentProfile = studentProfileMapper.selectOne(studentQuery);
                if (studentProfile != null) {
                    userMap.put("studentId", studentProfile.getStudentId());
                    userMap.put("collegeId", studentProfile.getCollegeId());
                    userMap.put("majorId", studentProfile.getMajorId());
                    if (studentProfile.getCollegeId() != null) {
                        College college = collegeService.getById(studentProfile.getCollegeId());
                        if (college != null) {
                            userMap.put("collegeName", college.getName());
                        }
                    }
                    if (studentProfile.getMajorId() != null) {
                        Major major = majorService.getById(studentProfile.getMajorId());
                        if (major != null) {
                            userMap.put("majorName", major.getName());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to get student profile for user {}: {}", user.getId(), e.getMessage());
            }
        } else if (user.getRole() == 2) {
            try {
                QueryWrapper<TeacherProfile> teacherQuery = new QueryWrapper<>();
                teacherQuery.eq("user_id", user.getId());
                TeacherProfile teacherProfile = teacherProfileMapper.selectOne(teacherQuery);
                if (teacherProfile != null) {
                    // 这里原来写的是 userMap.put("jobNumber", teacherProfile.getUserId())
                    // —— 把 users.id（于凤海 = 29）塞进了"工号"字段。teacher_profile
                    // 表里根本没有工号列，教师的工号就是 users.username（10001）。
                    // 不填这个字段，前端会落到 username（PC AdminUsers.vue 的
                    // `row.jobNumber || row.username`），也就是真实工号；而填了错值
                    // 反而更糟：PC 的编辑弹窗会把 29 预填进工号输入框，保存时
                    // 按 `jobNumber !== username` 把它写回 username，等于把账号改名。
                    userMap.put("collegeId", teacherProfile.getCollegeId());
                    if (teacherProfile.getCollegeId() != null) {
                        College college = collegeService.getById(teacherProfile.getCollegeId());
                        if (college != null) {
                            userMap.put("collegeName", college.getName());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to get teacher profile for user {}: {}", user.getId(), e.getMessage());
            }
        } else if (user.getRole() == 3) {
            try {
                // 原来这里走 Feign：counselorServiceClient.getCounselorById(user.getId())。
                // 两个问题叠在一起，导致这一支**从来没生效过**（静默 warn 掉）：
                //   1) counselor-service 的 /counselors/{id} 是按 `counselor_id` 列查的
                //      （'C53' 这种编号），传进去的却是 users.id（53）；
                //   2) 该路径参数是 String，Feign 传 Long 也只是换个写法。
                // 本地就有 CounselorProfileMapper，按 user_id 查是同一张表、同一个语义，
                // 和上面 role=1/2 两支完全一致，还省掉一次跨服务调用。
                QueryWrapper<CounselorProfile> counselorQuery = new QueryWrapper<>();
                counselorQuery.eq("user_id", user.getId());
                CounselorProfile counselorProfile = counselorProfileMapper.selectOne(counselorQuery);
                if (counselorProfile != null) {
                    userMap.put("jobNumber", counselorProfile.getStaffId());
                    userMap.put("collegeId", counselorProfile.getCollegeId());
                    if (counselorProfile.getCollegeId() != null) {
                        College college = collegeService.getById(counselorProfile.getCollegeId());
                        if (college != null) {
                            userMap.put("collegeName", college.getName());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to get counselor profile for user {}: {}", user.getId(), e.getMessage());
            }
        } else if (user.getRole() == 5) {
            try {
                // 学院管理员：补上学院与职务，否则这一类账号在列表里"学院"列是空的，
                // 而按学院过滤时又会被 userIdsInCollege 收进来 —— 过滤出来却看不到学院，
                // 会让人以为过滤错了。
                QueryWrapper<CollegeAdminProfile> collegeAdminQuery = new QueryWrapper<>();
                collegeAdminQuery.eq("user_id", user.getId());
                CollegeAdminProfile collegeAdminProfile = collegeAdminProfileMapper.selectOne(collegeAdminQuery);
                if (collegeAdminProfile != null) {
                    userMap.put("collegeId", collegeAdminProfile.getCollegeId());
                    userMap.put("title", collegeAdminProfile.getTitle());
                    if (collegeAdminProfile.getCollegeId() != null) {
                        College college = collegeService.getById(collegeAdminProfile.getCollegeId());
                        if (college != null) {
                            userMap.put("collegeName", college.getName());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to get college admin profile for user {}: {}", user.getId(), e.getMessage());
            }
        }
    }

    public ApiResponse<Map<String, Object>> getUsersByPageFallback(Integer page, Integer size, Long collegeId, Integer role, Throwable t) {
        Map<String, Object> fallbackData = new HashMap<>();
        fallbackData.put("total", 0);
        fallbackData.put("items", List.of());
        fallbackData.put("message", "adminApi:getUsersByPage API is degraded: " + t.getMessage());
        return ApiResponse.success(fallbackData);
    }

    @GetMapping("/colleges")
    public ApiResponse<List<Map<String, Object>>> getAllColleges() {
        try {
            log.info("Get all colleges request");
            List<College> colleges = collegeService.list();
            
            List<Map<String, Object>> resultList = new ArrayList<>();
            for (College college : colleges) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", college.getId());
                item.put("name", college.getName());
                item.put("code", college.getCode());
                item.put("description", college.getDescription());
                item.put("studentCount", getStudentCount(college.getId()));
                item.put("teacherCount", getTeacherCount(college.getId()));
                item.put("counselorCount", getCounselorCount(college.getId()));
                // 2026-10-01 第十九轮: AdminStatistics「学院数据明细」的"班级数"列原来恒 '--' —— 这里从前只给三个
                // 计数, 页面读的 classCount 根本没这个 key。补上(classes.college_id 是真实关联)。
                // 注意: **"课程数"没补, 因为 courses 表没有任何到学院的链路**(含 college_id 的表只有
                // classes/majors/student_profile/teacher_profile/counselor_profiles/college_admin_profile),
                // 按"本学院教师任教"间接算会得到 学院1=10/其余=0 的退化结果 ⇒ 该列已从页面删除。
                item.put("classCount", getClassCount(college.getId()));
                // 2026-10-01 第二十一轮: 小程序「学院详情」的「创建时间」读 createdAt,
                // 这里从前不给 ⇒ 那一行恒空。colleges.created_at 是真实列。
                item.put("createdAt", college.getCreatedAt());
                resultList.add(item);
            }
            
            return ApiResponse.success(resultList);
        } catch (Exception e) {
            log.error("Get all colleges failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    private Long getStudentCount(Long collegeId) {
        try {
            QueryWrapper<StudentProfile> query = new QueryWrapper<>();
            query.eq("college_id", collegeId);
            long count = studentProfileMapper.selectCount(query);
            log.info("Student count for college {}: {}", collegeId, count);
            return count;
        } catch (Exception e) {
            log.warn("Failed to get student count for college {}: {}", collegeId, e.getMessage());
        }
        return 0L;
    }

    private Long getTeacherCount(Long collegeId) {
        try {
            QueryWrapper<TeacherProfile> query = new QueryWrapper<>();
            query.eq("college_id", collegeId);
            long count = teacherProfileMapper.selectCount(query);
            log.info("Teacher count for college {}: {}", collegeId, count);
            return count;
        } catch (Exception e) {
            log.warn("Failed to get teacher count for college {}: {}", collegeId, e.getMessage());
        }
        return 0L;
    }

    private Long getCounselorCount(Long collegeId) {
        try {
            QueryWrapper<CounselorProfile> query = new QueryWrapper<>();
            query.eq("college_id", collegeId);
            long count = counselorProfileMapper.selectCount(query);
            log.info("Counselor count for college {}: {}", collegeId, count);
            return count;
        } catch (Exception e) {
            log.warn("Failed to get counselor count for college {}: {}", collegeId, e.getMessage());
        }
        return 0L;
    }

    /** 该学院下的班级数(classes.college_id)。2026-10-01 第十九轮随 AdminStatistics 的两列补上。 */
    private Long getClassCount(Long collegeId) {
        try {
            QueryWrapper<Classes> query = new QueryWrapper<>();
            query.eq("college_id", collegeId);
            long count = classesMapper.selectCount(query);
            log.info("Class count for college {}: {}", collegeId, count);
            return count;
        } catch (Exception e) {
            log.warn("Failed to get class count for college {}: {}", collegeId, e.getMessage());
        }
        return 0L;
    }

    @GetMapping("/colleges/{id}")
    public ApiResponse<College> getCollegeById(@PathVariable Long id) {
        College college = collegeService.getById(id);
        if (college != null) {
            return ApiResponse.success(college);
        }
        return ApiResponse.notFound("College not found");
    }

    @PostMapping("/colleges")
    public ApiResponse<String> createCollege(@RequestBody College college) {
        collegeService.save(college);
        return ApiResponse.success("College created successfully");
    }

    @PutMapping("/colleges/{id}")
    public ApiResponse<String> updateCollege(@PathVariable Long id, @RequestBody College college) {
        college.setId(id);
        // 2026-10-03（W8）：请求体全 null（客户端发 `{}`、或字段名与实体对不上被 Jackson
        // 静默忽略）时，MyBatis-Plus 会生成**没有 SET 子句**的 UPDATE ⇒ MySQL 语法错 ⇒ 500。
        // 那是客户端的错，如实回 400。判据见 UpdateBodyGuard。
        if (!UpdateBodyGuard.hasUpdatableField(college)) {
            return ApiResponse.badRequest("请求体里没有任何可更新的字段");
        }
        // 2026-10-03（W7）：updateById 对不存在的 id 返回 0 行、不抛异常；
        // 不读返回值就会对不存在的学院回"更新成功"。
        return collegeService.updateById(college)
                ? ApiResponse.success("College updated successfully")
                : ApiResponse.notFound("College not found");
    }

    @DeleteMapping("/colleges/{id}")
    public ApiResponse<String> deleteCollege(@PathVariable Long id) {
        return collegeService.removeById(id)
                ? ApiResponse.success("College deleted successfully")
                : ApiResponse.notFound("College not found");
    }

    @GetMapping("/courses")
    public ApiResponse<List<Map<String, Object>>> getCourses() {
        try {
            log.info("Get all courses request");
            
            // 优先使用本地服务（快速响应）
            List<Course> localCourses = courseService.list();
            if (localCourses != null && !localCourses.isEmpty()) {
                log.info("Returning {} courses from local database", localCourses.size());
                return ApiResponse.success(convertCoursesToMaps(localCourses));
            }
            
            // 本地没有数据时，尝试远程服务（添加超时保护）
            boolean shouldTryRemote = courseServiceAvailable || 
                (System.currentTimeMillis() - courseServiceLastFailedTime > SERVICE_RETRY_INTERVAL);
            
            if (shouldTryRemote) {
                try {
                    // 设置快速超时（2秒）
                    ApiResponse<List<Course>> courseResponse = courseServiceClient.getAllCourses();
                    if (courseResponse != null && courseResponse.getCode() == 200 && courseResponse.getData() != null) {
                        if (!courseServiceAvailable) {
                            log.info("course-service is available again");
                            courseServiceAvailable = true;
                        }
                        List<Course> courses = courseResponse.getData();
                        return ApiResponse.success(convertCoursesToMaps(courses));
                    }
                } catch (Exception e) {
                    log.warn("Failed to get courses from course-service: {}", e.getMessage());
                    courseServiceAvailable = false;
                    courseServiceLastFailedTime = System.currentTimeMillis();
                }
            }
            
            // 如果本地和远程都没有数据，返回空列表
            return ApiResponse.success(new ArrayList<>());
        } catch (Exception e) {
            log.error("Get courses failed: {}", e.getMessage());
            return ApiResponse.error("获取课程列表失败: " + e.getMessage());
        }
    }

    private List<Map<String, Object>> convertCoursesToMaps(List<Course> courses) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Course course : courses) {
            Map<String, Object> courseMap = new HashMap<>();
            courseMap.put("id", course.getId());
            courseMap.put("code", course.getCode());
            courseMap.put("name", course.getName());
            courseMap.put("credits", course.getCredits());
            courseMap.put("type", course.getType());
            courseMap.put("courseType", course.getType() != null ? course.getType() : "");
            courseMap.put("moduleId", course.getModuleId());
            courseMap.put("teacherId", course.getTeacherId());
            courseMap.put("createdAt", course.getCreatedAt());
            courseMap.put("updatedAt", course.getUpdatedAt());
            // 2026-10-01 第二十一轮: 小程序「课程详情」的「说明」读 description,
            // 这个 Map 里从前没有这个 key ⇒ 恒 '--'。courses.description 是真实列,
            // PC 的 AdminCourses 也一直在编辑它。
            courseMap.put("description", course.getDescription());
            result.add(courseMap);
        }
        return result;
    }

    @GetMapping("/majors")
    public ApiResponse<List<Map<String, Object>>> getAllMajors() {
        // 2026-10-01 第十九轮: 原先是 `return majorService.list()`(直接序列化 Major 实体),
        // 而实体上**没有** collegeName / studentCount ⇒ AdminStatistics「专业数据明细」的
        // "所属学院"和"学生数"两列恒为 '--'。改成 Map 并在原字段之外补这两个 key。
        // ⚠️ 所有原字段一个不少地带上 —— 调用方(PC AdminMajors/AdminColleges、小程序 admin/colleges、
        // admin/courses)全都按 key 读, 形状从实体换成 Map 对它们透明。
        List<Major> majors = majorService.list();

        Map<Long, String> collegeNames = new HashMap<>();
        for (College c : collegeService.list()) {
            collegeNames.put(c.getId(), c.getName());
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Major m : majors) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", m.getId());
            item.put("collegeId", m.getCollegeId());
            item.put("name", m.getName());
            item.put("code", m.getCode());
            item.put("shortName", m.getShortName());
            item.put("description", m.getDescription());
            item.put("status", m.getStatus());
            item.put("createdAt", m.getCreatedAt());
            item.put("updatedAt", m.getUpdatedAt());
            item.put("collegeName", collegeNames.get(m.getCollegeId()));
            item.put("studentCount", getMajorStudentCount(m.getId()));
            result.add(item);
        }
        return ApiResponse.success(result);
    }

    /** 该专业下的学生数(student_profile.major_id)。 */
    private Long getMajorStudentCount(Long majorId) {
        try {
            QueryWrapper<StudentProfile> query = new QueryWrapper<>();
            query.eq("major_id", majorId);
            long count = studentProfileMapper.selectCount(query);
            log.info("Student count for major {}: {}", majorId, count);
            return count;
        } catch (Exception e) {
            log.warn("Failed to get student count for major {}: {}", majorId, e.getMessage());
        }
        return 0L;
    }

    @GetMapping("/majors/{id}")
    public ApiResponse<Major> getMajorById(@PathVariable Long id) {
        Major major = majorService.getById(id);
        if (major != null) {
            return ApiResponse.success(major);
        }
        return ApiResponse.notFound("Major not found");
    }

    /**
     * 新建专业。
     *
     * <p>2026-10-02（W4.14）：{@code majors.code} 是 <b>NOT NULL + UNIQUE</b>，
     * 而 PC 端此前是**在前端自己编**：{@code String(maxId + 1).padStart(2, '0')}
     * —— 两个问题：①{@code maxId} 取自**当前列表**，而列表可能正被学院筛选过，
     * 于是生成出已存在的码，撞 UNIQUE 报错；②它拿的是 {@code id} 而不是 {@code code}，
     * 只是活库里两者恰好对齐（1→01、2→02…）才没出事。
     *
     * <p>现在改成**后端生成**：请求体不带 {@code code}（或留空）时，按全表
     * {@code code} 里最大的数字 +1 生成两位码。全表扫，不看任何筛选后的列表。
     * 显式传了 {@code code} 就照用（小程序端就是让管理员自己填的）。
     */
    @PostMapping("/majors")
    public ApiResponse<String> createMajor(@RequestBody Major major) {
        if (major.getCode() == null || major.getCode().trim().isEmpty()) {
            major.setCode(nextMajorCode());
        }
        majorService.save(major);
        return ApiResponse.success("Major created successfully");
    }

    /**
     * 生成下一个专业码：全表 {@code code} 中数字部分的最大值 +1，补足两位。
     *
     * <p>非纯数字的码（历史上可能有人填过 {@code CS01} 之类）不参与比较，
     * 不能解析就跳过 —— 静默当成 0 会让下一个码退回到 {@code 01} 撞唯一键。
     * 生成后仍可能与某个非数字码撞车，所以外面套一层存在性检查。
     */
    private String nextMajorCode() {
        java.util.Set<String> taken = new java.util.HashSet<>();
        int max = 0;
        for (Major m : majorService.list()) {
            String c = m.getCode() == null ? null : m.getCode().trim();
            if (c == null || c.isEmpty()) continue;
            taken.add(c);
            try {
                max = Math.max(max, Integer.parseInt(c));
            } catch (NumberFormatException ignore) {
                // 非纯数字码：只登记占用，不参与取最大值
            }
        }
        int next = max + 1;
        String code = String.format("%02d", next);
        while (taken.contains(code)) {
            next++;
            code = String.format("%02d", next);
        }
        return code;
    }

    @PutMapping("/majors/{id}")
    public ApiResponse<String> updateMajor(@PathVariable Long id, @RequestBody Major major) {
        major.setId(id);
        if (!UpdateBodyGuard.hasUpdatableField(major)) {   // W8：空请求体 ⇒ 无 SET 子句 ⇒ 500
            return ApiResponse.badRequest("请求体里没有任何可更新的字段");
        }
        return majorService.updateById(major)
                ? ApiResponse.success("Major updated successfully")
                : ApiResponse.notFound("Major not found");
    }

    @DeleteMapping("/majors/{id}")
    public ApiResponse<String> deleteMajor(@PathVariable Long id) {
        return majorService.removeById(id)
                ? ApiResponse.success("Major deleted successfully")
                : ApiResponse.notFound("Major not found");
    }

    

    @GetMapping("/courses/{id}")
    public ApiResponse<Course> getCourseById(@PathVariable Long id) {
        Course course = courseService.getById(id);
        if (course != null) {
            return ApiResponse.success(course);
        }
        return ApiResponse.notFound("Course not found");
    }

    /**
     * 新建课程。
     *
     * <p>2026-10-08（W31）：原来**忽略 {@code save()} 的返回值**，无论有没有写进去都回
     * {@code "Course created successfully"} —— 同一段里的 {@code updateCourse} /
     * {@code deleteCourse} 都检查了，只有这条没有。
     *
     * <p>⚠️ 如实记一句边界：MyBatis-Plus 的 {@code insert} 失败时**多半是抛异常**
     * （如编码重复撞 {@code uk_courses_code} → {@code DuplicateKeyException} → 全局 500），
     * 所以"0 行且不抛"这条路径**当前难以构造** ⇒ 这次属于**防御性对齐**，
     * 不是修好了一个能被点出来的假成功。写在这里是为了不让下一个人以为它检查过了。
     */
    @PostMapping("/courses")
    public ApiResponse<String> createCourse(@RequestBody Course course) {
        if (!courseService.save(course)) {
            return ApiResponse.error("Course created failed");
        }
        return ApiResponse.success("Course created successfully");
    }

    @PutMapping("/courses/{id}")
    public ApiResponse<String> updateCourse(@PathVariable Long id, @RequestBody Course course) {
        course.setId(id);
        if (!UpdateBodyGuard.hasUpdatableField(course)) {   // W8：空请求体 ⇒ 无 SET 子句 ⇒ 500
            return ApiResponse.badRequest("请求体里没有任何可更新的字段");
        }
        return courseService.updateById(course)
                ? ApiResponse.success("Course updated successfully")
                : ApiResponse.notFound("Course not found");
    }

    @DeleteMapping("/courses/{id}")
    public ApiResponse<String> deleteCourse(@PathVariable Long id) {
        return courseService.removeById(id)
                ? ApiResponse.success("Course deleted successfully")
                : ApiResponse.notFound("Course not found");
    }

    @GetMapping("/rules")
    public ApiResponse<List<WarningRule>> getAllRules() {
        return ApiResponse.success(warningRuleService.list());
    }

    @GetMapping("/rules/{id}")
    public ApiResponse<WarningRule> getRuleById(@PathVariable Long id) {
        WarningRule rule = warningRuleService.getById(id);
        if (rule != null) {
            return ApiResponse.success(rule);
        }
        return ApiResponse.notFound("Rule not found");
    }

    @PostMapping("/rules")
    public ApiResponse<String> createRule(@RequestBody WarningRule rule) {
        warningRuleService.save(rule);
        auditLogService.record("CREATE", "WARNING_RULE", rule.getId(),
                "新建预警规则 " + rule.getRuleName());
        return ApiResponse.success("Rule created successfully");
    }

    @PutMapping("/rules/{id}")
    public ApiResponse<String> updateRule(@PathVariable Long id, @RequestBody WarningRule rule) {
        rule.setId(id);
        // W8：空请求体（无 SET 子句）先判掉，别让它冒到 SQL 层变成 500。
        if (!UpdateBodyGuard.hasUpdatableField(rule)) {
            return ApiResponse.badRequest("请求体里没有任何可更新的字段");
        }
        // 2026-10-03（W7）：0 行 = 该规则不存在，回 404 且**不写审计** ——
        // 原来无论改没改到都"照写审计"，审计里于是有"修改了不存在的规则"。
        if (!warningRuleService.updateById(rule)) {
            return ApiResponse.notFound("Rule not found");
        }
        // 预警阈值被改动会直接影响哪些学生被判预警，是必须留痕的一类操作。
        auditLogService.record("UPDATE", "WARNING_RULE", id, "修改预警规则 " + rule.getRuleName());
        return ApiResponse.success("Rule updated successfully");
    }

    @DeleteMapping("/rules/{id}")
    public ApiResponse<String> deleteRule(@PathVariable Long id) {
        // 同上：删 0 行 = 该规则不存在，回 404 且不写审计。
        if (!warningRuleService.removeById(id)) {
            return ApiResponse.notFound("Rule not found");
        }
        auditLogService.record("DELETE", "WARNING_RULE", id, "删除预警规则 id=" + id);
        return ApiResponse.success("Rule deleted successfully");
    }

    @GetMapping("/classes")
    public ApiResponse<List<Map<String, Object>>> getAllClasses() {
        List<Map<String, Object>> result = classesMapper.selectAllWithStudentCount();
        return ApiResponse.success(result);
    }

    @GetMapping("/classes/{id}")
    public ApiResponse<Classes> getClassById(@PathVariable Long id) {
        Classes classes = classesMapper.selectById(id);
        if (classes != null) {
            return ApiResponse.success(classes);
        }
        return ApiResponse.notFound("Class not found");
    }

    @PostMapping("/classes")
    public ApiResponse<String> createClass(@RequestBody Classes classes) {
        classesMapper.insert(classes);
        return ApiResponse.success("Class created successfully");
    }

    @PutMapping("/classes/{id}")
    public ApiResponse<String> updateClass(@PathVariable Long id, @RequestBody Classes classes) {
        classes.setId(id);
        if (!UpdateBodyGuard.hasUpdatableField(classes)) {  // W8：空请求体 ⇒ 无 SET 子句 ⇒ 500
            return ApiResponse.badRequest("请求体里没有任何可更新的字段");
        }
        return classesMapper.updateById(classes) > 0
                ? ApiResponse.success("Class updated successfully")
                : ApiResponse.notFound("Class not found");
    }

    @DeleteMapping("/classes/{id}")
    public ApiResponse<String> deleteClass(@PathVariable Long id) {
        return classesMapper.deleteById(id) > 0
                ? ApiResponse.success("Class deleted successfully")
                : ApiResponse.notFound("Class not found");
    }

    @GetMapping("/students")
    @SentinelResource(value = "adminApi:getStudents", fallback = "getAllStudentsFallback")
    public ApiResponse<List<Student>> getAllStudents() {
        try {
            ApiResponse<List<Student>> response = studentServiceClient.getStudentsByCollege(null);
            if (response != null && response.getCode() == 200) {
                return ApiResponse.success(response.getData());
            }
            return ApiResponse.error("Failed to get students");
        } catch (Exception e) {
            log.error("Get all students failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    public ApiResponse<List<Student>> getAllStudentsFallback(Throwable t) {
        log.warn("adminApi:getStudents API is degraded: {}", t.getMessage());
        return ApiResponse.error("adminApi:getStudents API is degraded: " + t.getMessage());
    }

    @GetMapping("/students/{id}")
    public ApiResponse<Student> getStudentById(@PathVariable Long id) {
        try {
            ApiResponse<Student> response = studentServiceClient.getStudentById(id);
            if (response != null && response.getCode() == 200) {
                return ApiResponse.success(response.getData());
            }
            return ApiResponse.notFound("Student not found");
        } catch (Exception e) {
            log.error("Get student by id failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/students")
    public ApiResponse<String> createStudent(@RequestBody Student student) {
        try {
            studentService.save(student);
            return ApiResponse.success("Student created successfully");
        } catch (Exception e) {
            log.error("Create student failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PutMapping("/students/{id}")
    public ApiResponse<String> updateStudent(@PathVariable Long id, @RequestBody Student student) {
        try {
            student.setId(id);
            if (!UpdateBodyGuard.hasUpdatableField(student)) {  // W8：空请求体 ⇒ 无 SET 子句 ⇒ 500
                return ApiResponse.badRequest("请求体里没有任何可更新的字段");
            }
            return studentService.updateById(student)
                    ? ApiResponse.success("Student updated successfully")
                    : ApiResponse.notFound("Student not found");
        } catch (Exception e) {
            log.error("Update student failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @DeleteMapping("/students/{id}")
    public ApiResponse<String> deleteStudent(@PathVariable Long id) {
        try {
            return studentService.removeById(id)
                    ? ApiResponse.success("Student deleted successfully")
                    : ApiResponse.notFound("Student not found");
        } catch (Exception e) {
            log.error("Delete student failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/teachers")
    public ApiResponse<List<TeacherProfile>> getAllTeachers() {
        try {
            List<TeacherProfile> teachers = teacherProfileMapper.selectList(null);
            return ApiResponse.success(teachers);
        } catch (Exception e) {
            log.error("Get all teachers failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/teachers/{id}")
    public ApiResponse<TeacherProfile> getTeacherById(@PathVariable Long id) {
        TeacherProfile teacher = teacherProfileMapper.selectById(id);
        if (teacher != null) {
            return ApiResponse.success(teacher);
        }
        return ApiResponse.notFound("Teacher not found");
    }

    @PostMapping("/teachers")
    public ApiResponse<String> createTeacher(@RequestBody TeacherProfile teacher) {
        try {
            teacherProfileMapper.insert(teacher);
            return ApiResponse.success("Teacher created successfully");
        } catch (Exception e) {
            log.error("Create teacher failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PutMapping("/teachers/{id}")
    public ApiResponse<String> updateTeacher(@PathVariable Long id, @RequestBody TeacherProfile teacher) {
        try {
            teacher.setId(id);
            if (!UpdateBodyGuard.hasUpdatableField(teacher)) {  // W8：空请求体 ⇒ 无 SET 子句 ⇒ 500
                return ApiResponse.badRequest("请求体里没有任何可更新的字段");
            }
            return teacherProfileMapper.updateById(teacher) > 0
                    ? ApiResponse.success("Teacher updated successfully")
                    : ApiResponse.notFound("Teacher not found");
        } catch (Exception e) {
            log.error("Update teacher failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @DeleteMapping("/teachers/{id}")
    public ApiResponse<String> deleteTeacher(@PathVariable Long id) {
        try {
            // ⚠️ 两个 ID 空间不能混：路径里的 id 是 teacher_profile.id，
            //    而 users 表认的是 users.id，两者不是同一个数。
            //    原实现先 teacherProfileMapper.deleteById(id) 再 userService.removeById(id)，
            //    传 profile id=1（于凤海，其账号是 users#29）时，删掉的账号是
            //    **users#1 —— admin 账号**，而于凤海的账号还留在库里。
            //    这个接口目前没有页面在调，但它能在演示中把管理员删没，所以顺手修掉：
            //    先按 profile 查出 user_id，再删账号（顺序与 deleteUser 一致）。
            TeacherProfile teacher = teacherProfileMapper.selectById(id);
            if (teacher == null) {
                return ApiResponse.notFound("Teacher not found");
            }
            teacherProfileMapper.deleteById(id);
            if (teacher.getUserId() != null) {
                userService.removeById(teacher.getUserId());
            }
            return ApiResponse.success("Teacher deleted successfully");
        } catch (Exception e) {
            log.error("Delete teacher failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/counselors")
    @SentinelResource(value = "adminApi:getCounselors", fallback = "getAllCounselorsFallback")
    public ApiResponse<List<CounselorProfile>> getAllCounselors() {
        try {
            ApiResponse<List<CounselorProfile>> response = counselorServiceClient.getCounselorsByCollege(null);
            if (response != null && response.getCode() == 200) {
                return ApiResponse.success(response.getData());
            }
            return ApiResponse.error("Failed to get counselors");
        } catch (Exception e) {
            log.error("Get all counselors failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    public ApiResponse<List<CounselorProfile>> getAllCounselorsFallback(Throwable t) {
        log.warn("adminApi:getCounselors API is degraded: {}", t.getMessage());
        return ApiResponse.error("adminApi:getCounselors API is degraded: " + t.getMessage());
    }

    @GetMapping("/counselors/{id}")
    public ApiResponse<CounselorProfile> getCounselorById(@PathVariable Long id) {
        try {
            // ⚠️ 这里原来把 id 直接转给 counselor-service 的 /counselors/{counselorId}，
            //    而那条路径是按 **counselor_id 编号列**（'C53' 这种字符串）查的，
            //    不是按数字 id。从上表 /admin/counselors 拿到的 id 是 profile 主键，
            //    传过去必然查不到 —— 于是这个接口对任何真实 id 都返回 notFound。
            //    本地按主键查即正确；顺带兜一层 users.id，因为"辅导员管理"之外的
            //    调用方手里可能是账号 id（本工程两套 ID 混用是常见坑）。
            CounselorProfile counselor = counselorProfileMapper.selectById(id);
            if (counselor == null) {
                counselor = counselorProfileMapper.selectOne(
                        new QueryWrapper<CounselorProfile>().eq("user_id", id));
            }
            if (counselor != null) {
                return ApiResponse.success(counselor);
            }
            return ApiResponse.notFound("Counselor not found");
        } catch (Exception e) {
            log.error("Get counselor by id failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/counselors")
    public ApiResponse<String> createCounselor(@RequestBody CounselorProfile counselor) {
        try {
            counselorService.save(counselor);
            return ApiResponse.success("Counselor created successfully");
        } catch (Exception e) {
            log.error("Create counselor failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PutMapping("/counselors/{id}")
    public ApiResponse<String> updateCounselor(@PathVariable Long id, @RequestBody CounselorProfile counselor) {
        try {
            counselor.setId(id);
            if (!UpdateBodyGuard.hasUpdatableField(counselor)) {  // W8：空请求体 ⇒ 无 SET 子句 ⇒ 500
                return ApiResponse.badRequest("请求体里没有任何可更新的字段");
            }
            return counselorService.updateById(counselor)
                    ? ApiResponse.success("Counselor updated successfully")
                    : ApiResponse.notFound("Counselor not found");
        } catch (Exception e) {
            log.error("Update counselor failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @DeleteMapping("/counselors/{id}")
    public ApiResponse<String> deleteCounselor(@PathVariable Long id) {
        try {
            // ⚠️ 与上面 deleteTeacher 同一类 ID 空间问题：路径里的 id 是
            //    counselor_profile.id，而 users 表认的是 users.id，两者不是同一个数。
            //    原实现 `counselorService.removeById(id); userService.removeById(id);`
            //    用同一个 id 去删两张不相干的表 —— 删掉辅导员档案的同时，
            //    会把**另一个编号的账号**删掉，而该辅导员的账号还留在库里。
            //    先按 profile 查出 user_id，再删账号（顺序与 deleteTeacher 一致）。
            CounselorProfile counselor = counselorProfileMapper.selectById(id);
            if (counselor == null) {
                return ApiResponse.notFound("Counselor not found");
            }
            if (!counselorService.removeById(id)) {
                return ApiResponse.notFound("Counselor not found");
            }
            if (counselor.getUserId() != null) {
                userService.removeById(counselor.getUserId());
            }
            return ApiResponse.success("Counselor deleted successfully");
        } catch (Exception e) {
            log.error("Delete counselor failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/warnings")
    @SentinelResource(value = "adminApi:getWarnings", fallback = "getWarningsFallback")
    public ApiResponse<List<AcademicWarning>> getWarnings() {
        try {
            // 这里原来先走 Feign（warning-service 的 /warnings/list），失败再兜底查本地库。
            // 实测：Feign 那一支**从来没成功过** —— 它返回的 data 是
            // {warnings:[...], total:n} 这个 Map，而这里直接
            // (List<AcademicWarning>) response.getData() 强转，抛
            // "LinkedHashMap cannot be cast to List"，被 catch 吞掉后走兜底，
            // 所以列表看着是好的，实际数据一直来自兜底分支。
            // 表和实体本来就是同一张：admin-service 的 AcademicWarningMapper 与
            // warning-service 读的都是 academic_warning，实体同为
            // com.academic.common.entity.AcademicWarning，所以直连本地库不是"退而求其次"。
            // 要走 Feign 也不是不行，但得先把返回改成带 DTO 的类型化分页
            // （Map 里的 LocalDateTime 需要 Spring 的 ObjectMapper 才能转回实体），
            // 在那之前，留一个装样子的跨服务调用只会掩盖真实数据来源。
            List<AcademicWarning> warnings = academicWarningMapper.selectList(null);
            return ApiResponse.success(warnings);
        } catch (Exception e) {
            log.error("Get warnings failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    public ApiResponse<List<AcademicWarning>> getWarningsFallback(Throwable t) {
        log.warn("adminApi:getWarnings API is degraded: {}", t.getMessage());
        return ApiResponse.error("adminApi:getWarnings API is degraded: " + t.getMessage());
    }

    @GetMapping("/warnings/{id}")
    public ApiResponse<AcademicWarning> getWarningById(@PathVariable Long id) {
        try {
            // 原实现同上：把 /warnings/list 的 Map 载荷强转成 List 再循环找 id，
            // 因为这个接口**没有兜底分支**，强转异常直接变成 HTTP 200 + code:500
            // （"class java.util.LinkedHashMap cannot be cast to class java.util.List"）。
            // 按主键查本地库即可，与列表同一个数据源，天然一致。
            AcademicWarning warning = academicWarningMapper.selectById(id);
            if (warning != null) {
                return ApiResponse.success(warning);
            }
            return ApiResponse.notFound("Warning not found");
        } catch (Exception e) {
            log.error("Get warning by id failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PutMapping("/warnings/{id}/handle")
    public ApiResponse<String> handleWarning(@PathVariable Long id, @RequestBody Map<String, Object> handleData) {
        try {
            AcademicWarning warning = academicWarningMapper.selectById(id);
            if (warning == null) {
                return ApiResponse.notFound("Warning not found");
            }
            String handleResult = (String) handleData.getOrDefault("handleResult", "已处理");
            String remark = (String) handleData.getOrDefault("remark", "");
            // ⚠️ 原实现是 `warning.setHandledBy(remark)` —— 把**备注文本**写进了「处理人」列，
            // 而且**从不读**请求里的 `handledBy`（前端传了也丢掉）。结果预警详情里
            // 「处理人」显示的是"已电话联系学生"这类备注，处理人是谁反而没记。
            // 现在：处理人取请求里的 handledBy（前端没传就退回 remark 里的旧行为不再发生），
            // 备注若前端传了 remark 则并入 handleResult 一并保存，不占处理人的位置。
            String handledBy = (String) handleData.getOrDefault("handledBy", null);

            warning.setStatus(1);
            warning.setHandleResult(remark != null && !remark.isEmpty()
                    ? handleResult + "（备注：" + remark + "）" : handleResult);
            warning.setHandledBy(handledBy);
            warning.setHandledAt(LocalDateTime.now());
            academicWarningMapper.updateById(warning);

            log.info("Handled warning {}: result={}, remark={}, handledBy={}", id, handleResult, remark, handledBy);
            auditLogService.record("HANDLE", "WARNING", id,
                    "处理学业预警：" + handleResult
                            + (warning.getStudentName() == null ? "" : "（学生 " + warning.getStudentName() + "）"));
            return ApiResponse.success("Warning handled successfully");
        } catch (Exception e) {
            log.error("Handle warning failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/teacher-class-applications")
    public ApiResponse<Map<String, Object>> getTeacherClassApplications(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Integer status) {
        try {
            List<TeacherClassApplication> allApplications = applicationMapper.selectList(null);
            
            List<TeacherClassApplication> filteredApplications = allApplications;
            if (status != null) {
                filteredApplications = allApplications.stream()
                        .filter(app -> status.equals(app.getStatus()))
                        .collect(Collectors.toList());
            }

            int total = filteredApplications.size();
            int start = (page - 1) * size;
            int end = Math.min(start + size, total);

            List<TeacherClassApplication> pageItems = start < total ? filteredApplications.subList(start, end) : List.of();

            Map<String, Object> result = new HashMap<>();
            result.put("total", total);
            result.put("items", pageItems);

            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Get teacher class applications failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/teacher-class-applications/{id}")
    public ApiResponse<TeacherClassApplication> getTeacherClassApplication(@PathVariable Long id) {
        TeacherClassApplication application = applicationMapper.selectById(id);
        if (application != null) {
            return ApiResponse.success(application);
        }
        return ApiResponse.notFound("Application not found");
    }

    @PostMapping("/teacher-class-applications")
    public ApiResponse<String> createTeacherClassApplication(@RequestBody TeacherClassApplication application) {
        try {
            applicationMapper.insert(application);
            return ApiResponse.success("Application created successfully");
        } catch (Exception e) {
            log.error("Create teacher class application failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PutMapping("/teacher-class-applications/{id}/approve")
    public ApiResponse<String> approveApplication(@PathVariable Long id) {
        try {
            TeacherClassApplication application = applicationMapper.selectById(id);
            if (application != null) {
                application.setStatus(1);
                applicationMapper.updateById(application);
                return ApiResponse.success("Application approved successfully");
            }
            return ApiResponse.notFound("Application not found");
        } catch (Exception e) {
            log.error("Approve application failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PutMapping("/teacher-class-applications/{id}/reject")
    public ApiResponse<String> rejectApplication(@PathVariable Long id, @RequestBody(required = false) String reason) {
        try {
            TeacherClassApplication application = applicationMapper.selectById(id);
            if (application != null) {
                application.setStatus(2);
                application.setRejectReason(reason);
                applicationMapper.updateById(application);
                return ApiResponse.success("Application rejected successfully");
            }
            return ApiResponse.notFound("Application not found");
        } catch (Exception e) {
            log.error("Reject application failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @DeleteMapping("/teacher-class-applications/{id}")
    public ApiResponse<String> deleteTeacherClassApplication(@PathVariable Long id) {
        try {
            return applicationMapper.deleteById(id) > 0
                    ? ApiResponse.success("Application deleted successfully")
                    : ApiResponse.notFound("Application not found");
        } catch (Exception e) {
            log.error("Delete teacher class application failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/export/statistics")
    public void exportStatistics(HttpServletResponse response) throws IOException {
        try {
            String fileName = "statistics_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx";
            String filePath = exportDir + "/" + fileName;
            
            File exportDirFile = new File(exportDir);
            if (!exportDirFile.exists()) {
                exportDirFile.mkdirs();
            }

            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("统计数据");

            Row headerRow = sheet.createRow(0);
            String[] headers = {"统计项", "数值"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }

            Map<String, Object> statistics = getStatistics().getData();
            int rowNum = 1;
            for (Map.Entry<String, Object> entry : statistics.entrySet()) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(entry.getKey());
                row.createCell(1).setCellValue(entry.getValue() != null ? entry.getValue().toString() : "");
            }

            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                workbook.write(fos);
            }
            workbook.close();

            ExportHistory exportHistory = new ExportHistory();
            exportHistory.setDataType("statistics");
            exportHistory.setFileName(fileName);
            exportHistory.setRecordCount(1);
            exportHistory.setCreatedAt(LocalDateTime.now());
            exportHistoryMapper.insert(exportHistory);

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString()));
            
            Files.copy(Paths.get(filePath), response.getOutputStream());
            
            log.info("Export statistics successfully: {}", fileName);
        } catch (Exception e) {
            log.error("Export statistics failed: {}", e.getMessage());
            throw e;
        }
    }

    @GetMapping("/export/users")
    public void exportUsers(HttpServletResponse response) throws IOException {
        try {
            String fileName = "users_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx";
            String filePath = exportDir + "/" + fileName;
            
            File exportDirFile = new File(exportDir);
            if (!exportDirFile.exists()) {
                exportDirFile.mkdirs();
            }

            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("用户数据");

            Row headerRow = sheet.createRow(0);
            String[] headers = {"ID", "用户名", "姓名", "邮箱", "角色", "状态", "创建时间"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }

            List<User> users = userService.list();
            int rowNum = 1;
            for (User user : users) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(user.getId());
                row.createCell(1).setCellValue(user.getUsername());
                row.createCell(2).setCellValue(user.getName());
                row.createCell(3).setCellValue(user.getEmail());
                row.createCell(4).setCellValue(user.getRole());
                row.createCell(5).setCellValue(user.getStatus());
                row.createCell(6).setCellValue(user.getCreatedAt() != null ? user.getCreatedAt().toString() : "");
            }

            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                workbook.write(fos);
            }
            workbook.close();

            ExportHistory exportHistory = new ExportHistory();
            exportHistory.setDataType("users");
            exportHistory.setFileName(fileName);
            exportHistory.setRecordCount(users.size());
            exportHistory.setCreatedAt(LocalDateTime.now());
            exportHistoryMapper.insert(exportHistory);

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString()));
            
            Files.copy(Paths.get(filePath), response.getOutputStream());
            
            log.info("Export users successfully: {}", fileName);
        } catch (Exception e) {
            log.error("Export users failed: {}", e.getMessage());
            throw e;
        }
    }

    @GetMapping("/export/students")
    public void exportStudents(HttpServletResponse response) throws IOException {
        try {
            String fileName = "students_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx";
            String filePath = exportDir + "/" + fileName;
            
            File exportDirFile = new File(exportDir);
            if (!exportDirFile.exists()) {
                exportDirFile.mkdirs();
            }

            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("学生数据");

            Row headerRow = sheet.createRow(0);
            String[] headers = {"ID", "学号", "姓名", "学院ID", "班级ID", "创建时间"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }

            List<Student> students = studentService.getAllStudents();
            int rowNum = 1;
            for (Student student : students) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(student.getId());
                row.createCell(1).setCellValue(student.getStudentId());
                row.createCell(2).setCellValue(student.getName());
                row.createCell(3).setCellValue(student.getCollegeId() != null ? student.getCollegeId() : 0);
                row.createCell(4).setCellValue(student.getClassId() != null ? student.getClassId() : 0);
                row.createCell(5).setCellValue(student.getCreatedAt() != null ? student.getCreatedAt().toString() : "");
            }

            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                workbook.write(fos);
            }
            workbook.close();

            ExportHistory exportHistory = new ExportHistory();
            exportHistory.setDataType("students");
            exportHistory.setFileName(fileName);
            exportHistory.setRecordCount(students.size());
            exportHistory.setCreatedAt(LocalDateTime.now());
            exportHistoryMapper.insert(exportHistory);

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString()));
            
            Files.copy(Paths.get(filePath), response.getOutputStream());
            
            log.info("Export students successfully: {}", fileName);
        } catch (Exception e) {
            log.error("Export students failed: {}", e.getMessage());
            throw e;
        }
    }

    @GetMapping("/export/teachers")
    public void exportTeachers(HttpServletResponse response) throws IOException {
        try {
            String fileName = "teachers_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx";
            String filePath = exportDir + "/" + fileName;
            
            File exportDirFile = new File(exportDir);
            if (!exportDirFile.exists()) {
                exportDirFile.mkdirs();
            }

            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("教师数据");

            Row headerRow = sheet.createRow(0);
            String[] headers = {"ID", "用户ID", "姓名", "学院ID", "职称", "创建时间"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }

            List<TeacherProfile> teachers = teacherProfileMapper.selectList(null);
            int rowNum = 1;
            for (TeacherProfile teacher : teachers) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(teacher.getId());
                row.createCell(1).setCellValue(teacher.getUserId());
                row.createCell(2).setCellValue(teacher.getName());
                row.createCell(3).setCellValue(teacher.getCollegeId() != null ? teacher.getCollegeId() : 0);
                row.createCell(4).setCellValue(teacher.getTitle() != null ? teacher.getTitle() : "");
                row.createCell(5).setCellValue(teacher.getCreatedAt() != null ? teacher.getCreatedAt().toString() : "");
            }

            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                workbook.write(fos);
            }
            workbook.close();

            ExportHistory exportHistory = new ExportHistory();
            exportHistory.setDataType("teachers");
            exportHistory.setFileName(fileName);
            exportHistory.setRecordCount(teachers.size());
            exportHistory.setCreatedAt(LocalDateTime.now());
            exportHistoryMapper.insert(exportHistory);

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString()));
            
            Files.copy(Paths.get(filePath), response.getOutputStream());
            
            log.info("Export teachers successfully: {}", fileName);
        } catch (Exception e) {
            log.error("Export teachers failed: {}", e.getMessage());
            throw e;
        }
    }

    @GetMapping("/export/colleges")
    public void exportColleges(HttpServletResponse response) throws IOException {
        try {
            String fileName = "colleges_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx";
            String filePath = exportDir + "/" + fileName;
            
            File exportDirFile = new File(exportDir);
            if (!exportDirFile.exists()) {
                exportDirFile.mkdirs();
            }

            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("学院数据");

            Row headerRow = sheet.createRow(0);
            String[] headers = {"学院ID", "学院名称", "学院代码", "描述", "学生数量", "教师数量", "辅导员数量"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }

            List<College> colleges = collegeService.list();
            int rowNum = 1;
            for (College college : colleges) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(college.getId());
                row.createCell(1).setCellValue(college.getName());
                row.createCell(2).setCellValue(college.getCode());
                row.createCell(3).setCellValue(college.getDescription() != null ? college.getDescription() : "");
                row.createCell(4).setCellValue(getStudentCount(college.getId()));
                row.createCell(5).setCellValue(getTeacherCount(college.getId()));
                row.createCell(6).setCellValue(getCounselorCount(college.getId()));
            }

            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                workbook.write(fos);
            }
            workbook.close();

            ExportHistory exportHistory = new ExportHistory();
            exportHistory.setDataType("colleges");
            exportHistory.setFileName(fileName);
            exportHistory.setRecordCount(colleges.size());
            exportHistory.setCreatedAt(LocalDateTime.now());
            exportHistoryMapper.insert(exportHistory);

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString()));
            
            Files.copy(Paths.get(filePath), response.getOutputStream());
            
            log.info("Export colleges successfully: {}", fileName);
        } catch (Exception e) {
            log.error("Export colleges failed: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 批量导入学生（上传 Excel，服务端解析）。
     *
     * <p>⚠️ 契约在 2026-09-30 改过一次：原来是 {@code @RequestBody List<Map>}（JSON 数组），
     * 指望前端解析 Excel 后发 JSON 过来。但前端全仓**没有任何 xlsx 解析能力**
     * （{@code AdminDataExport.vue} 里的 {@code window.XLSX} 只在"客户端导出兜底"那一段
     * 从 CDN 懒加载，导入路径根本不用它），所以那个契约从来没能跑通一次。
     * 而模板端点 {@code /admin/import/template/{type}} 和 5 个导出端点都在本服务里、
     * 用的就是 POI —— **上传文件、服务端解析**才是本工程本来的口径。
     * 也没有别的调用方需要兼容（前端从来发不出合法 JSON），所以直接换契约。
     *
     * <p>报告口径与 {@code importScores} 对齐：逐行尽力而为，返回
     * {@code total/success/skipped/errors}。每行的 {@code users}+{@code student_profile}
     * 由 {@link StudentImportService} 放在**同一事务**里，所以失败的行不会留下
     * "账号建好了、学生档案没有"；一行的失败也不连坐其余行。
     */
    @PostMapping("/import/students")
    public ApiResponse<Map<String, Object>> importStudents(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> errors = new ArrayList<>();
        int successCount = 0, skipCount = 0;
        result.put("fileName", file == null ? null : file.getOriginalFilename());

        try {
            if (file == null || file.isEmpty()) {
                return ApiResponse.error("文件为空");
            }
            String fileName = file.getOriginalFilename();
            if (fileName == null || !fileName.matches("(?i).*\\.(xlsx|xls)$")) {
                return ApiResponse.error("仅支持 .xlsx 或 .xls 格式的 Excel 文件");
            }
            // 上限必须与 application.yml 的 spring.servlet.multipart 一致 ——
            // 容器在进这个方法之前就会拦，拦下来的报错是 MaxUploadSizeExceededException，
            // 这里再写一句"不能超过 10MB"就成了谎话。
            if (file.getSize() > MAX_IMPORT_BYTES) {
                return ApiResponse.error("文件大小不能超过 10MB");
            }

            // W22：`new XSSFWorkbook(...)` 只认 OOXML，**读不了 .xls** —— 而上面的扩展名闸
            // 明明写着"仅支持 .xlsx 或 .xls"。改用 WorkbookFactory（本仓 :5006 已在用），
            // 让那句承诺变成真的：真 .xls 走 HSSF 也能导（实测 4096 字节的 HSSF 文件可正常读）。
            //
            // 解析失败的翻译**放在这一层**，不放外层 catch：外层 catch 还包着逐行落库，
            // 那里抛的异常（比如 DB 报错）绝不能被说成"你的文件有问题"。分开以后，
            // 外层的中性文案就只在真正的服务端故障时才会出现。
            Workbook opened;
            try {
                opened = WorkbookFactory.create(file.getInputStream());
            } catch (Exception e) {
                log.error("Parse students excel failed: {}", file.getOriginalFilename(), e);
                String friendly = ExcelUploadErrors.describe(e);
                // 走到这里一定是"读不了这个文件"，认不出具体类型就按"认不出是什么文件"回。
                return ApiResponse.error(friendly != null ? friendly : ExcelUploadErrors.UNREADABLE);
            }
            try (Workbook workbook = opened) {
                Sheet sheet = workbook.getSheetAt(0);
                if (sheet == null || sheet.getLastRowNum() < 1) {
                    return ApiResponse.error("表里没有数据行（第 1 行应当是表头，数据从第 2 行开始）");
                }

                Map<String, Integer> col = mapColumns(sheet.getRow(0));
                if (!col.containsKey("studentId")) {
                    return ApiResponse.error("表头里找不到「学号」列，请先下载模板：/admin/import/template/students");
                }

                for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;
                    int rowNum = r + 1;   // 报给管理员的行号 = 他在 Excel 里看到的行号
                    try {
                        String studentId = cellText(cellAt(row, col, "studentId"));
                        String name = cellText(cellAt(row, col, "name"));
                        String className = cellText(cellAt(row, col, "className"));
                        if (studentId == null && name == null && className == null) {
                            continue;   // 整行空 —— 不算错，跳过
                        }
                        if (studentId == null) {
                            errors.add(rowError(rowNum, "学号为空")); skipCount++; continue;
                        }
                        if (studentId.length() > 20) {
                            errors.add(rowError(rowNum, "学号超过 20 位（student_profile.student_id 是 varchar(20)）"));
                            skipCount++; continue;
                        }
                        if (name == null) {
                            errors.add(rowError(rowNum, "姓名为空 —— 学生档案的姓名列不允许为空"));
                            skipCount++; continue;
                        }

                        String password = cellText(cellAt(row, col, "password"));
                        String problem = studentImportService.importOne(
                                studentId,
                                name,
                                (password == null || password.isEmpty()) ? DEFAULT_IMPORT_PASSWORD : password,
                                cellLong(cellAt(row, col, "collegeId"), "学院ID"),
                                cellLong(cellAt(row, col, "majorId"), "专业ID"),
                                className,
                                cellLong(cellAt(row, col, "classId"), "班级ID"));

                        if (problem != null) {
                            errors.add(rowError(rowNum, problem)); skipCount++;
                        } else {
                            successCount++;
                        }
                    } catch (Exception e) {
                        // 这一行的两笔写入已经被事务回滚，不会留下半截；下一行继续。
                        log.warn("导入学生第 {} 行失败", rowNum, e);
                        errors.add(rowError(rowNum, briefError(e)));
                        skipCount++;
                    }
                }
            }

            result.put("total", successCount + skipCount);
            result.put("success", successCount);
            result.put("skipped", skipCount);
            result.put("errors", errors);
            log.info("Import students finished: total={}, success={}, skip={}",
                    successCount + skipCount, successCount, skipCount);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Import students failed", e);
            // W22：不再把 POI 的英文原文回给客户端。是"文件本身的问题"就给可操作的中文，
            // 否则说明是落库等服务端故障 —— 那种情况回"文件损坏"是谎报原因，给中性文案。
            String friendly = ExcelUploadErrors.describe(e);
            return ApiResponse.error(friendly != null ? friendly
                    : "导入失败：服务器处理这个文件时出错，请稍后重试或联系管理员");
        }
    }

    /** 与 application.yml 的 spring.servlet.multipart.max-file-size 保持一致。 */
    private static final long MAX_IMPORT_BYTES = 10L * 1024 * 1024;

    /** 模板里写的"密码(默认123456)"，也是活库 35 个账号统一用的口令。 */
    private static final String DEFAULT_IMPORT_PASSWORD = "123456";

    /**
     * 按**表头文字**建列映射，而不是按列序。
     *
     * <p>模板的表头是 {@code 学号(必填)/姓名/密码(默认123456)/学院ID/专业ID/班级}，
     * 导出的表头是 {@code ID/学号/姓名/学院ID/班级ID/创建时间} —— 两个来源
     * 列名与列序都不同（模板给"班级"文字，导出给"班级ID"数字）。按序解析会在
     * 这两个来源之间整体错位一格，且是静默的；按"包含"匹配则括号后缀自然被忽略，
     * 导出的文件也能直接导回来。
     */
    private static Map<String, Integer> mapColumns(Row header) {
        Map<String, Integer> map = new HashMap<>();
        if (header == null || header.getLastCellNum() < 0) return map;
        for (int c = header.getFirstCellNum(); c < header.getLastCellNum(); c++) {
            String h = cellText(header.getCell(c));
            if (h == null) continue;
            String flat = h.replace(" ", "");
            if (flat.contains("学号")) map.putIfAbsent("studentId", c);
            else if (flat.contains("姓名")) map.putIfAbsent("name", c);
            else if (flat.contains("密码")) map.putIfAbsent("password", c);
            else if (flat.contains("学院")) map.putIfAbsent("collegeId", c);
            else if (flat.contains("专业")) map.putIfAbsent("majorId", c);
            // 「班级ID」必须先判：它同时包含「班级」
            else if (flat.toLowerCase().contains("班级id")) map.putIfAbsent("classId", c);
            else if (flat.contains("班级")) map.putIfAbsent("className", c);
        }
        return map;
    }

    private static Cell cellAt(Row row, Map<String, Integer> col, String field) {
        Integer c = col.get(field);
        return c == null ? null : row.getCell(c);
    }

    /**
     * 单元格取文本。Excel 里的学号常常是"数字格式"，POI 读出来是 double，
     * 直接 toString 会得到 {@code 2023020616.0} 甚至科学计数法 —— 所以整数一律按 long 输出。
     */
    private static String cellText(Cell cell) {
        if (cell == null) return null;
        try {
            CellType type = cell.getCellType();
            if (type == CellType.FORMULA) type = cell.getCachedFormulaResultType();
            if (type == CellType.NUMERIC) {
                double d = cell.getNumericCellValue();
                if (d == Math.rint(d) && Math.abs(d) < 1e15) return String.valueOf((long) d);
                return BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
            }
            if (type == CellType.BOOLEAN) return String.valueOf(cell.getBooleanCellValue());
            if (type == CellType.BLANK) return null;
            String s = cell.getStringCellValue();
            return s == null || s.trim().isEmpty() ? null : s.trim();
        } catch (Exception e) {
            return null;
        }
    }

    /** 学院/专业/班级 ID 列填了非数字时，如实报错，而不是当成"没填"悄悄放过。 */
    private static Long cellLong(Cell cell, String label) {
        String s = cellText(cell);
        if (s == null || s.isEmpty()) return null;
        try {
            return (long) Double.parseDouble(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " 不是数字：" + s);
        }
    }

    /** 用 HashMap 而不是 {@code Map.of} —— 后者的值不允许为 null，异常 message 恰好为 null 时会再抛一个 NPE。 */
    private static Map<String, Object> rowError(int row, String reason) {
        Map<String, Object> m = new HashMap<>();
        m.put("row", row);
        m.put("reason", reason);
        return m;
    }

    /**
     * 把异常压成一句给管理员看的话。
     *
     * <p>直接把 {@code e.getMessage()} 放出去的话，一行错误会是 MyBatis 包装的
     * {@code "### Error updating database. Cause: ... ### SQL: INSERT INTO student_profile ..."}
     * —— 完整 SQL 与"###"这种内部格式发给前端，既没有解释力（管理员看不懂）、
     * 又把实现细节漏了出去。往里找最内层的 cause，只取它那句话
     * （如 {@code "Data truncation: Data too long for column 'name' at row 1"}）；
     * 完整堆栈仍然进服务端日志（上面那条 log.warn 带上了 e）。
     */
    private static String briefError(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String msg = root.getMessage();
        return (msg == null || msg.trim().isEmpty()) ? root.getClass().getSimpleName() : msg.trim();
    }

    /**
     * 导入成绩（管理员端）—— **上传 Excel，服务端 POI 解析**。
     *
     * <p><b>2026-10-04（W19）换契约</b>：改之前这里收的是
     * {@code @RequestBody List<Map<String,Object>>}，而前端 {@code AdminDataExport.vue}
     * 发的是一个 {@code el-upload} 的 {@code UploadFile} 对象（{@code {name,url,status,raw}}
     * —— 那**不是**文件内容）。两边从第一天起就对不上，**这个按钮一次都没成功过**。
     *
     * <p>同页的「导入学生」（{@link #importStudents}）本来就是 multipart + POI，
     * 模板端点 {@link #downloadTemplate} 也一直产出真实 xlsx ——
     * 「上传文件、服务端解析」才是本工程本来的口径。也没有别的调用方需要兼容
     * （前端从来发不出合法 JSON），所以直接换契约，不再留一条 JSON 入口。
     *
     * <p>列口径见 {@link #scoreColumns}。⚠️ 模板里的「学分」列**不落库**：
     * {@code scores} 与 {@code score_record} 两张表都没有学分列（学分挂在
     * {@code courses.credits} 上），这一列只是给人参考 —— 如实写在注释里，
     * 免得日后又变成一处"模板上有、导进去静默消失"。
     *
     * <p>解析只负责"Excel → List&lt;Map&gt;"；真正的逐行处理（查学生、查课程、查重、
     * 同步 scores 表）仍在 {@link #applyScoreRows} 里，**只有一份实现**。
     */
    @PostMapping("/import/scores")
    public ApiResponse<Map<String, Object>> importScores(@RequestParam("file") MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                return ApiResponse.error("文件为空");
            }
            String fileName = file.getOriginalFilename();
            if (fileName == null || !fileName.matches("(?i).*\\.(xlsx|xls)$")) {
                return ApiResponse.error("仅支持 .xlsx 或 .xls 格式的 Excel 文件");
            }
            if (file.getSize() > MAX_IMPORT_BYTES) {
                return ApiResponse.error("文件大小不能超过 10MB");
            }

            List<Map<String, Object>> rows = new ArrayList<>();
            // W22：同 /import/students —— WorkbookFactory 才真的能把 .xls 读进来，
            // 解析失败的翻译也放在这一层（理由见 /import/students 的注释）。
            Workbook opened;
            try {
                opened = WorkbookFactory.create(file.getInputStream());
            } catch (Exception e) {
                log.error("Parse scores excel failed: {}", file.getOriginalFilename(), e);
                String friendly = ExcelUploadErrors.describe(e);
                return ApiResponse.error(friendly != null ? friendly : ExcelUploadErrors.UNREADABLE);
            }
            try (Workbook workbook = opened) {
                Sheet sheet = workbook.getSheetAt(0);
                if (sheet == null || sheet.getLastRowNum() < 1) {
                    return ApiResponse.error("表里没有数据行（第 1 行应当是表头，数据从第 2 行开始）");
                }
                Map<String, Integer> col = scoreColumns(sheet.getRow(0));
                if (!col.containsKey("studentId")) {
                    return ApiResponse.error("表头里找不到「学号」列，请先下载模板：/admin/import/template/scores");
                }
                if (!col.containsKey("courseName")) {
                    return ApiResponse.error("表头里找不到「课程名称」列，请先下载模板：/admin/import/template/scores");
                }

                for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;
                    Map<String, Object> item = new LinkedHashMap<>();
                    boolean blank = true;
                    for (Map.Entry<String, Integer> e : col.entrySet()) {
                        String v = cellText(row.getCell(e.getValue()));
                        if (v != null) blank = false;
                        item.put(e.getKey(), v);
                    }
                    if (blank) continue;   // 整行空 —— 不算错，跳过
                    // 带上 Excel 里的真实行号：中间有空行时，下标会与行号错开，
                    // 报给管理员的行号必须是他自己在 Excel 里看到的那一行。
                    item.put("__row", r + 1);
                    rows.add(item);
                }
            }
            if (rows.isEmpty()) {
                return ApiResponse.error("表里没有可导入的数据行");
            }
            return applyScoreRows(rows);
        } catch (Exception e) {
            log.error("Import scores failed", e);
            // W22：文案与 /import/students 逐字一致 —— 同一族接口对同一种失败说两句话是排查时的噪音。
            String friendly = ExcelUploadErrors.describe(e);
            return ApiResponse.error(friendly != null ? friendly
                    : "导入失败：服务器处理这个文件时出错，请稍后重试或联系管理员");
        }
    }

    /**
     * 按**表头文字**建成绩导入的列映射，与 {@link #mapColumns} 同一套"包含匹配"写法
     * （模板给「学号(必填)」、导出的文件给「学号」，按序解析会在两个来源之间整体错位）。
     *
     * <p>模板表头是 {@code 学号(必填)/课程名称(必填)/成绩/学分/学期}；这里顺带认管理员
     * 自己导出件里的列名（平时成绩/期末成绩/总评成绩/姓名/等级/绩点/备注/学年），
     * 于是「导出 → 改分 → 导回」也能走通。
     *
     * <p><b>判断顺序是这里的关键</b>：「平时成绩」「期末成绩」「总评成绩」**都包含「成绩」**。
     * 若把兜底的「成绩/分数」放在前面，模板里的那一列就会被"平时成绩"抢走 ——
     * 总评分数静默落空，成绩导进去了却没生效。所以先判最具体的三个，最后才轮到泛化的。
     *
     * <p>⚠️「学分」**刻意不建映射**：两张成绩表都没有这一列，建了也没有落点；
     * 与"静默消失"相比，宁可让它明确地不参与。
     */
    private static Map<String, Integer> scoreColumns(Row header) {
        Map<String, Integer> map = new LinkedHashMap<>();
        if (header == null || header.getLastCellNum() < 0) return map;
        for (int c = header.getFirstCellNum(); c < header.getLastCellNum(); c++) {
            String h = cellText(header.getCell(c));
            if (h == null) continue;
            String flat = h.replace(" ", "");
            if (flat.contains("平时")) map.putIfAbsent("regularScore", c);
            else if (flat.contains("期末")) map.putIfAbsent("finalScore", c);
            else if (flat.contains("总评")) map.putIfAbsent("score", c);
            else if (flat.contains("学号")) map.putIfAbsent("studentId", c);
            else if (flat.contains("姓名")) map.putIfAbsent("studentName", c);
            else if (flat.contains("课程")) map.putIfAbsent("courseName", c);
            else if (flat.contains("学期")) map.putIfAbsent("semester", c);
            else if (flat.contains("备注")) map.putIfAbsent("remark", c);
            else if (flat.contains("等级")) map.putIfAbsent("grade", c);
            else if (flat.contains("绩点")) map.putIfAbsent("gradePoint", c);
            else if (flat.contains("学年") || flat.contains("年份")) map.putIfAbsent("year", c);
            else if (flat.contains("学分")) { /* 无落点，明确不参与（见 javadoc） */ }
            else if (flat.contains("成绩") || flat.contains("分数")) map.putIfAbsent("score", c);
        }
        return map;
    }

    /** 逐行处理一批成绩行（键：studentId=学号、courseName、score、semester 及可选列）。 */
    private ApiResponse<Map<String, Object>> applyScoreRows(List<Map<String, Object>> data) {
        Map<String, Object> result = new HashMap<>();
        int successCount = 0, skipCount = 0, updateCount = 0, syncedCount = 0, syncFailCount = 0;
        List<Map<String, Object>> errors = new ArrayList<>();
        List<ScoreRecord> batchToInsert = new ArrayList<>();
        // 待同步到 scores 权威表的行；**插入成功之后**才同步（见循环后面的说明）。
        List<ScoreRecord> toSync = new ArrayList<>();

        try {
            log.info("Import scores request, total rows: {}", data.size());

            // 动态字段映射表：Excel列名 → ScoreRecord setter
            Map<String, java.util.function.BiConsumer<ScoreRecord, String>> fieldMapping = new HashMap<>();
            fieldMapping.put("studentName",   (sr, v) -> sr.setStudentName(v));
            fieldMapping.put("className",     (sr, v) -> sr.setClassName(v));
            fieldMapping.put("score",         (sr, v) -> { if (isNumber(v)) sr.setScore(Double.parseDouble(v)); });
            fieldMapping.put("scoreLevel",    (sr, v) -> { if (isNumber(v)) sr.setScoreLevel(Integer.parseInt(v)); });
            fieldMapping.put("semester",      (sr, v) -> sr.setSemester(v));
            fieldMapping.put("remark",        (sr, v) -> sr.setRemark(v));
            fieldMapping.put("regularScore",  (sr, v) -> { if (isNumber(v)) sr.setRegularScore(Double.parseDouble(v)); });
            fieldMapping.put("finalScore",    (sr, v) -> { if (isNumber(v)) sr.setFinalScore(Double.parseDouble(v)); });
            fieldMapping.put("grade",         (sr, v) -> sr.setGrade(v));
            fieldMapping.put("gradePoint",    (sr, v) -> { if (isNumber(v)) sr.setGradePoint(Double.parseDouble(v)); });
            fieldMapping.put("year",          (sr, v) -> { if (isNumber(v)) sr.setYear(Integer.parseInt(v)); });
            fieldMapping.put("reasonForChange",(sr, v) -> sr.setReasonForChange(v));

            // 缓存
            Map<String, com.academic.entity.StudentProfile> profileCache = new HashMap<>();
            Map<String, Course> courseCache = new HashMap<>();

            for (int i = 0; i < data.size(); i++) {
                Map<String, Object> item = data.get(i);
                // Excel 入口带了真实行号（见 importScores）；JSON 入口没有再按下标推。
                Object excelRow = item.get("__row");
                int rowNum = excelRow instanceof Number ? ((Number) excelRow).intValue() : i + 2;
                String studentNo = getStr(item, "studentId");
                String courseName = getStr(item, "courseName");
                Object scoreVal = item.get("score");

                if (studentNo == null || studentNo.isEmpty()) {
                    errors.add(Map.of("row", rowNum, "reason", "学号为空")); skipCount++; continue;
                }
                if (courseName == null || courseName.isEmpty()) {
                    errors.add(Map.of("row", rowNum, "reason", "课程名为空")); skipCount++; continue;
                }
                if (scoreVal == null) {
                    errors.add(Map.of("row", rowNum, "reason", "分数为空")); skipCount++; continue;
                }

                // 查学生（缓存）
                com.academic.entity.StudentProfile profile = profileCache.get(studentNo);
                if (profile == null) {
                    profile = studentProfileMapper.selectOne(
                        new QueryWrapper<com.academic.entity.StudentProfile>().eq("student_id", studentNo));
                    if (profile != null) profileCache.put(studentNo, profile);
                }
                if (profile == null) {
                    errors.add(Map.of("row", rowNum, "reason", "学号不存在: " + studentNo)); skipCount++; continue;
                }

                // 查课程（缓存 + 模糊回退）
                Course course = courseCache.get(courseName);
                if (course == null) {
                    course = courseService.getOne(new QueryWrapper<Course>().eq("name", courseName));
                    if (course == null) {
                        List<Course> like = courseService.list(new QueryWrapper<Course>().like("name", courseName));
                        course = like.isEmpty() ? null : like.get(0);
                    }
                    if (course != null) courseCache.put(courseName, course);
                }
                if (course == null) {
                    errors.add(Map.of("row", rowNum, "reason", "课程不存在: " + courseName)); skipCount++; continue;
                }

                // 查重：带上学期。不带的话重修会覆盖旧学期记录，
                // 使 score_record 与 scores 两表的行数对不上。
                String rowSemester = getStr(item, "semester");
                QueryWrapper<ScoreRecord> dupQuery = new QueryWrapper<ScoreRecord>()
                        .eq("student_id", profile.getId())
                        .eq("course_id", course.getId());
                if (rowSemester != null && !rowSemester.trim().isEmpty()) {
                    dupQuery.eq("semester", rowSemester.trim());
                }
                ScoreRecord existing = scoreRecordMapper.selectOne(dupQuery);

                ScoreRecord sr = existing != null ? existing : new ScoreRecord();
                sr.setStudentId(profile.getId());
                sr.setCourseId(course.getId());
                // score_record.teacher_id 是 NOT NULL 且**没有默认值** —— 不补的话整批 INSERT
                // 会以 "Field 'teacher_id' doesn't have a default value" 失败。
                // 这不是本轮换契约才引入的：JSON 那条入口同样从不设它，所以这个端点在
                // 两个意义上都是坏的（前端发不进来 + 就算发进来也写不进去）。W19 实测确认。
                // 记成**这门课的任课教师**，与写进 course_id 的成绩归属一致；
                // 已有行保留它原来的录入人，不覆盖。
                if (sr.getTeacherId() == null) {
                    sr.setTeacherId(course.getTeacherId());
                }
                // 默认值从profile补
                if (sr.getStudentName() == null) sr.setStudentName(profile.getName());
                if (sr.getClassName() == null) sr.setClassName(profile.getClassName());

                // 动态映射所有Excel列 → ScoreRecord字段
                for (Map.Entry<String, Object> col : item.entrySet()) {
                    String key = col.getKey();
                    Object value = col.getValue();
                    if (value == null) continue;
                    if ("studentId".equals(key) || "courseName".equals(key)) continue;
                    java.util.function.BiConsumer<ScoreRecord, String> setter = fieldMapping.get(key);
                    if (setter != null) setter.accept(sr, String.valueOf(value));
                }

                // 分数等级自动算（未手动指定时）
                //
                // 等级/绩点/总评原来只算 score_level 这一个 —— 实测导入出来的行
                // score_record.grade 是 NULL，而 scores.grade 是 "A-"：同一份成绩
                // 在暂存表和权威表里各说各话。折算口径统一走 ScoreSyncSupport，
                // 与 ScoreRecordService.applyDerived 是同一份实现（不另写一套）。
                if (sr.getScore() != null) {
                    double s = sr.getScore();
                    if (sr.getScoreLevel() == null) {
                        sr.setScoreLevel(com.academic.common.util.ScoreSyncSupport.scoreToLevel(s));
                    }
                    if (sr.getScoreTotal() == null) sr.setScoreTotal(s);
                    if (sr.getGrade() == null) {
                        sr.setGrade(com.academic.common.util.ScoreSyncSupport.scoreToGrade(s));
                    }
                    if (sr.getGradePoint() == null) {
                        sr.setGradePoint(com.academic.common.util.ScoreSyncSupport.scoreToGpa(s).doubleValue());
                    }
                }

                if (existing != null) {
                    sr.setUpdatedAt(LocalDateTime.now());
                    scoreRecordService.updateById(sr);
                    updateCount++;
                } else {
                    sr.setCreatedAt(LocalDateTime.now());
                    sr.setUpdatedAt(LocalDateTime.now());
                    batchToInsert.add(sr);
                }
                successCount++;
                toSync.add(sr);
            }

            if (!batchToInsert.isEmpty()) scoreRecordService.saveBatch(batchToInsert);

            // 同步到 scores 表（权威成绩源）。不写这一步，导入的成绩在预警引擎和
            // 学生端统计里就等于不存在。
            //
            // ⚠️ **必须放在 saveBatch 之后**（W19 改）。原实现把它放在循环里、saveBatch 之前，
            // 于是批量 INSERT 一旦整体失败（实测正是 teacher_id 那次），scores 里已经留下
            // 一批权威成绩行、score_record 却一行都没有 —— 半截写：学生的成绩单上凭空多出
            // 几门课，而"录入记录"里查无此行。顺序倒过来，插入失败时两表都干净。
            for (ScoreRecord sr : toSync) {
                try {
                    syncToScores(sr);
                    syncedCount++;
                } catch (Exception e) {
                    log.warn("同步成绩到 scores 表失败: studentId={}, courseId={}: {}",
                            sr.getStudentId(), sr.getCourseId(), e.getMessage());
                    syncFailCount++;
                }
            }

            result.put("synced", syncedCount);
            result.put("syncFailed", syncFailCount);
            result.put("total", data.size());
            result.put("success", successCount);
            result.put("inserted", batchToInsert.size());
            result.put("updated", updateCount);
            result.put("skipped", skipCount);
            result.put("errors", errors);
            log.info("Import finished: total={}, insert={}, update={}, skip={}", data.size(), batchToInsert.size(), updateCount, skipCount);
            return ApiResponse.success(result);

        } catch (Exception e) {
            log.error("Import scores failed", e);
            result.put("success", successCount);
            return ApiResponse.error("导入异常: " + e.getMessage());
        }
    }

    private static String getStr(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString().trim() : null;
    }
    private static boolean isNumber(String v) { try { Double.parseDouble(v); return true; } catch (Exception e) { return false; } }

    @GetMapping("/import/template/{type}")
    public void downloadTemplate(@PathVariable String type, HttpServletResponse response) throws IOException {
        try {
            String fileName = type.equals("students") ? "学生导入模板.xlsx" : "成绩导入模板.xlsx";
            String filePath = exportDir + "/" + fileName;

            File exportDirFile = new File(exportDir);
            if (!exportDirFile.exists()) {
                exportDirFile.mkdirs();
            }

            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("导入模板");

            if (type.equals("students")) {
                Row headerRow = sheet.createRow(0);
                String[] headers = {"学号(必填)", "姓名", "密码(默认123456)", "学院ID", "专业ID", "班级"};
                for (int i = 0; i < headers.length; i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(headers[i]);
                }
                
                Row exampleRow = sheet.createRow(1);
                exampleRow.createCell(0).setCellValue("2023000001");
                exampleRow.createCell(1).setCellValue("张三");
                exampleRow.createCell(2).setCellValue("123456");
                exampleRow.createCell(3).setCellValue(1);
                exampleRow.createCell(4).setCellValue(1);
                exampleRow.createCell(5).setCellValue("软件工程2301班");
            } else {
                Row headerRow = sheet.createRow(0);
                String[] headers = {"学号(必填)", "课程名称(必填)", "成绩", "学分", "学期"};
                for (int i = 0; i < headers.length; i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(headers[i]);
                }
                
                Row exampleRow = sheet.createRow(1);
                exampleRow.createCell(0).setCellValue("2023000001");
                exampleRow.createCell(1).setCellValue("高等数学");
                exampleRow.createCell(2).setCellValue(85.5);
                exampleRow.createCell(3).setCellValue(4);
                exampleRow.createCell(4).setCellValue("2023-2024第一学期");
            }

            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                workbook.write(fos);
            }
            workbook.close();

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString()));
            
            Files.copy(Paths.get(filePath), response.getOutputStream());
            
            log.info("Download template successfully: {}", fileName);
        } catch (Exception e) {
            log.error("Download template failed: {}", e.getMessage());
            writeFileEndpointError(response, "下载模板失败: " + e.getMessage());
        }
    }

    @GetMapping("/export/insufficient-students")
    public void exportInsufficientStudents(HttpServletResponse response) throws IOException {
        try {
            String fileName = "insufficient_students_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx";
            String filePath = exportDir + "/" + fileName;

            File exportDirFile = new File(exportDir);
            if (!exportDirFile.exists()) {
                exportDirFile.mkdirs();
            }

            // 口径走 ScoreCaliber（权威源 scores、按课程去重、期末未录入不计）。
            // 原实现读 score_record 整表并手写 `scoreTotal < 60`，与其余页面口径分叉 ——
            // 同一名学生的"不及格科目数"在导出件里和页面上对不上（W26）。
            // 顺带消掉了原来的 N+1（每行查 student_profile + users）。
            List<Map<String, Object>> insufficientStudents = scoreStatMapper.selectInsufficientStudentsAll();

            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("成绩不足学生");

            Row headerRow = sheet.createRow(0);
            String[] headers = {"学号", "姓名", "不及格科目数", "最低分"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }

            int rowNum = 1;
            for (Map<String, Object> student : insufficientStudents) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(String.valueOf(student.get("student_no")));
                row.createCell(1).setCellValue(String.valueOf(student.get("student_name")));
                row.createCell(2).setCellValue(((Number) student.get("failed_courses")).intValue());
                // 只统计挂科课程里的最低分（SQL 侧 MIN(...) 忽略 NULL），不会是 null：
                // HAVING failed_courses > 0 保证至少有一行不及格参与 MIN。
                row.createCell(3).setCellValue(((Number) student.get("lowest_score")).doubleValue());
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                workbook.write(fos);
            }
            workbook.close();

            ExportHistory exportHistory = new ExportHistory();
            exportHistory.setDataType("insufficient-students");
            exportHistory.setFileName(fileName);
            exportHistory.setRecordCount(insufficientStudents.size());
            exportHistory.setCreatedAt(LocalDateTime.now());
            exportHistoryMapper.insert(exportHistory);

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString()));
            
            Files.copy(Paths.get(filePath), response.getOutputStream());
            
            log.info("Export insufficient students successfully: {}", fileName);
        } catch (Exception e) {
            log.error("Export insufficient students failed: {}", e.getMessage());
            writeFileEndpointError(response, "导出失败: " + e.getMessage());
        }
    }

    @GetMapping("/export/scores")
    public void exportScores(HttpServletResponse response) throws IOException {
        try {
            String fileName = "scores_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx";
            String filePath = exportDir + "/" + fileName;

            File exportDirFile = new File(exportDir);
            if (!exportDirFile.exists()) {
                exportDirFile.mkdirs();
            }

            List<ScoreRecord> scores = scoreRecordMapper.selectList(null);

            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("成绩数据");

            Row headerRow = sheet.createRow(0);
            String[] headers = {"ID", "学号", "姓名", "课程ID", "课程名称", "成绩", "学分", "学期", "备注"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }

            int rowNum = 1;
            for (ScoreRecord score : scores) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(score.getId());
                
                String studentId = "";
                String studentName = "";
                // 通过 student_profile 查找学生
                QueryWrapper<com.academic.common.entity.Student> studentQuery = new QueryWrapper<>();
                studentQuery.eq("id", score.getStudentId());
                com.academic.common.entity.Student studentProfile = studentService.getOne(studentQuery);
                if (studentProfile != null) {
                    studentId = studentProfile.getStudentId();
                    studentName = studentProfile.getName();
                }
                row.createCell(1).setCellValue(studentId);
                row.createCell(2).setCellValue(studentName);
                
                String courseName = "";
                Integer credit = 0;
                if (score.getCourseId() != null) {
                    Course course = courseService.getById(score.getCourseId());
                    if (course != null) {
                        courseName = course.getName();
                        credit = course.getCredits() != null ? course.getCredits().intValue() : 0;
                    }
                }
                row.createCell(3).setCellValue(score.getCourseId() != null ? score.getCourseId() : 0);
                row.createCell(4).setCellValue(courseName);
                row.createCell(5).setCellValue(score.getScore() != null ? score.getScore() : 0);
                row.createCell(6).setCellValue(credit);
                row.createCell(7).setCellValue(score.getSemester() != null ? score.getSemester() : "");
                row.createCell(8).setCellValue("");
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                workbook.write(fos);
            }
            workbook.close();

            ExportHistory exportHistory = new ExportHistory();
            exportHistory.setDataType("scores");
            exportHistory.setFileName(fileName);
            exportHistory.setRecordCount(scores.size());
            exportHistory.setCreatedAt(LocalDateTime.now());
            exportHistoryMapper.insert(exportHistory);

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString()));
            
            Files.copy(Paths.get(filePath), response.getOutputStream());
            
            log.info("Export scores successfully: {}", fileName);
        } catch (Exception e) {
            log.error("Export scores failed: {}", e.getMessage());
            writeFileEndpointError(response, "导出失败: " + e.getMessage());
        }
    }

    @GetMapping("/export/warnings")
    public void exportWarnings(HttpServletResponse response) throws IOException {
        try {
            String fileName = "warnings_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx";
            String filePath = exportDir + "/" + fileName;
            
            File exportDirFile = new File(exportDir);
            if (!exportDirFile.exists()) {
                exportDirFile.mkdirs();
            }

            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("预警数据");

            Row headerRow = sheet.createRow(0);
            String[] headers = {"ID", "学生ID", "预警等级", "预警原因", "创建时间", "状态"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }

            // 同一个 Map 强转问题（见 /warnings 的注释）：这里原来是
            // try { (List<AcademicWarning>) responseData.getData() } catch { warn }
            // —— 抛异常被吞掉后 warnings 保持空列表，于是导出的 xlsx **只有表头没有数据行**，
            // 而且还会把 recordCount=0 写进导出历史。导出是 PC 端在用的功能。
            List<AcademicWarning> warnings = academicWarningMapper.selectList(null);

            int rowNum = 1;
            for (AcademicWarning warning : warnings) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(warning.getId() != null ? warning.getId() : 0L);
                row.createCell(1).setCellValue(warning.getStudentId() != null ? warning.getStudentId() : 0L);
                row.createCell(2).setCellValue(warning.getWarningLevel() != null ? warning.getWarningLevel() : 0);
                row.createCell(3).setCellValue(warning.getDescription() != null ? warning.getDescription() : "");
                row.createCell(4).setCellValue(warning.getCreatedAt() != null ? warning.getCreatedAt().toString() : "");
                row.createCell(5).setCellValue(warning.getStatus() != null ? warning.getStatus().toString() : "");
            }

            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                workbook.write(fos);
            }
            workbook.close();

            ExportHistory exportHistory = new ExportHistory();
            exportHistory.setDataType("warnings");
            exportHistory.setFileName(fileName);
            exportHistory.setRecordCount(warnings.size());
            exportHistory.setCreatedAt(LocalDateTime.now());
            exportHistoryMapper.insert(exportHistory);

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString()));
            
            Files.copy(Paths.get(filePath), response.getOutputStream());
            
            log.info("Export warnings successfully: {}", fileName);
        } catch (Exception e) {
            log.error("Export warnings failed: {}", e.getMessage());
            throw e;
        }
    }

    @GetMapping("/messages")
    public ApiResponse<Map<String, Object>> getMessages(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        try {
            ApiResponse<List<com.academic.common.entity.Message>> response = messageServiceClient.getMessages(null, null, false);
            if (response != null && response.getCode() == 200) {
                List<com.academic.common.entity.Message> messages = response.getData();
                int total = messages != null ? messages.size() : 0;
                int start = (page - 1) * size;
                int end = Math.min(start + size, total);
                List<?> pageItems = start < total && messages != null ? messages.subList(start, end) : List.of();
                
                Map<String, Object> result = new HashMap<>();
                result.put("total", total);
                result.put("items", pageItems);
                return ApiResponse.success(result);
            }
            return ApiResponse.error("Failed to get messages");
        } catch (Exception e) {
            log.error("Get messages failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/messages/list")
    public ApiResponse<Map<String, Object>> getMessagesList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        try {
            ApiResponse<List<com.academic.common.entity.Message>> response = messageServiceClient.getMessages(null, null, false);
            if (response != null && response.getCode() == 200) {
                List<com.academic.common.entity.Message> messages = response.getData();
                int total = messages != null ? messages.size() : 0;
                int start = (page - 1) * size;
                int end = Math.min(start + size, total);
                List<?> pageItems = start < total && messages != null ? messages.subList(start, end) : List.of();
                
                Map<String, Object> result = new HashMap<>();
                result.put("total", total);
                result.put("items", pageItems);
                return ApiResponse.success(result);
            }
            return ApiResponse.error("Failed to get messages");
        } catch (Exception e) {
            log.error("Get messages list failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/messages/broadcast")
    public ApiResponse<Map<String, Object>> sendBroadcast(@RequestBody Map<String, Object> broadcastData) {
        try {
            ApiResponse<Map<String, Object>> response = messageServiceClient.sendBroadcast(broadcastData);
            if (response != null && response.getCode() == 200) {
                return ApiResponse.success(response.getData());
            }
            // 把下游的原话带上去。原来一律回 "Failed to send broadcast"，
            // 于是"广播确实失败了（0/2 条写入成功）"这种可追问的原因被抹平成一句英文。
            return ApiResponse.error(response != null ? response.getMessage() : "Failed to send broadcast");
        } catch (Exception e) {
            log.error("Send broadcast failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/messages/send")
    public ApiResponse<String> sendMessage(@RequestBody Map<String, Object> messageData) {
        try {
            ApiResponse<String> response = messageServiceClient.sendMessage(messageData);
            if (response != null && response.getCode() == 200) {
                return ApiResponse.success(response.getData());
            }
            return ApiResponse.error(response != null ? response.getMessage() : "Failed to send message");
        } catch (Exception e) {
            log.error("Send message failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    // ============= 任务管理接口 =============
    //
    // ⚠️ 2026-10-02（W1.11）：这四个端点原来是**整组桩** ——
    //   · `getTasksList` 里写死 `List<Map<String,Object>> tasks = new ArrayList<>();`
    //     （自带注释"任务列表（目前为空，后续可扩展）"）⇒ 永远回空列表；
    //   · 其余三个只 `log.info` 一行就回 "…successfully"，**一个字节都不落库**。
    //   而 PC `AdminMessages.vue` 的整个「任务管理」tab（创建表单 + 四个统计卡 +
    //   状态筛选 + 行内改状态 + 单条/批量删除 + 分页）**早就写好也接对了**，
    //   只是对着一个永远空的列表渲染 —— 于是页面看着"能用"，点了什么也不会发生。
    //   现在落到 `admin_tasks` 表上（迁移 `db_backups/admin_tasks_20261002_apply.sql`）。

    /** 任务状态的合法取值，与页面三个筛选按钮 / 行内下拉一一对应。 */
    private static final Set<String> TASK_STATUSES = Set.of("pending", "in_progress", "completed");
    /** 优先级的合法取值，与页面三个标签一一对应。 */
    private static final Set<String> TASK_PRIORITIES = Set.of("low", "medium", "high");

    @GetMapping("/tasks/list")
    public ApiResponse<Map<String, Object>> getTasksList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String status) {
        try {
            int safePage = page == null || page < 1 ? 1 : page;
            int safeSize = size == null || size < 1 ? 20 : Math.min(size, 200);
            String filter = (status == null || status.isBlank() || "all".equals(status)) ? null : status;

            long total = adminTaskMapper.countWithFilter(filter);
            List<Map<String, Object>> items =
                    adminTaskMapper.selectPage(filter, (safePage - 1) * safeSize, safeSize);

            Map<String, Object> result = new HashMap<>();
            result.put("total", total);
            result.put("items", items);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Get tasks list failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/tasks/create")
    public ApiResponse<Map<String, Object>> createTask(@RequestBody Map<String, Object> taskData) {
        try {
            String title = str(taskData.get("title"));
            if (title == null || title.isBlank()) {
                // 页面自己也挡了一道，但接口不能只靠前端挡（小程序/curl 也打得到）
                return ApiResponse.error("任务标题不能为空");
            }
            String priority = str(taskData.get("priority"));
            if (priority == null || priority.isBlank()) {
                priority = "medium";
            }
            if (!TASK_PRIORITIES.contains(priority)) {
                return ApiResponse.error("优先级只能是 low / medium / high，收到: " + priority);
            }

            AdminTask task = new AdminTask();
            task.setTitle(title.length() > 200 ? title.substring(0, 200) : title);
            task.setDescription(str(taskData.get("description")));
            task.setPriority(priority);
            task.setStatus("pending"); // 新建的一律是"待处理"，与页面上「待处理」卡片对得上
            // created_by 有指向 users.id 的真外键 ⇒ 用 AuditLogService 那份"回查 users"
            // 的解析（挡伪造/已删用户），解析不出来就写 NULL，**不编一个 0**
            task.setCreatedBy(auditLogService.currentActorId());
            task.setCreatedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            adminTaskMapper.insert(task);

            auditLogService.record("CREATE", "ADMIN_TASK", task.getId(), "创建任务「" + task.getTitle() + "」");

            Map<String, Object> result = new HashMap<>();
            result.put("id", task.getId());
            result.put("title", task.getTitle());
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Create task failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/tasks/{taskId}/status")
    public ApiResponse<Map<String, Object>> updateTaskStatus(
            @PathVariable Long taskId,
            @RequestBody Map<String, Object> statusData) {
        try {
            String status = str(statusData.get("status"));
            if (status == null || !TASK_STATUSES.contains(status)) {
                return ApiResponse.error("状态只能是 pending / in_progress / completed，收到: " + status);
            }
            // 用受影响行数判定，不用"没抛异常"判定：改一个不存在的 id 时
            // MyBatis-Plus 的 updateById 同样会"成功"，只有行数能区分。
            int rows = adminTaskMapper.updateStatus(taskId, status);
            if (rows == 0) {
                return ApiResponse.error("任务不存在: " + taskId);
            }
            auditLogService.record("UPDATE", "ADMIN_TASK", taskId, "任务状态改为 " + status);

            Map<String, Object> result = new HashMap<>();
            result.put("id", taskId);
            result.put("status", status);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Update task status failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @DeleteMapping("/tasks/{taskId}")
    public ApiResponse<String> deleteTask(@PathVariable Long taskId) {
        try {
            int rows = adminTaskMapper.deleteById(taskId);
            if (rows == 0) {
                return ApiResponse.error("任务不存在: " + taskId);
            }
            auditLogService.record("DELETE", "ADMIN_TASK", taskId, "删除任务 id=" + taskId);
            return ApiResponse.success("任务已删除");
        } catch (Exception e) {
            log.error("Delete task failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /** 请求体里取字符串（前端可能传 null / 数字 / 带空格）。 */
    private static String str(Object v) {
        return v == null ? null : String.valueOf(v).trim();
    }

    // ============= 班级管理申请接口 =============
    @GetMapping("/class-management/pending-requests")
    public ApiResponse<Map<String, Object>> getPendingClassRequests() {
        try {
            List<Map<String, Object>> pendingList = new ArrayList<>();
            
            // 查询教师申请
            List<TeacherClassApplication> teacherApps = applicationMapper.selectList(null);
            for (TeacherClassApplication app : teacherApps) {
                if (app.getStatus() == 0) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", app.getId());
                    item.put("type", "teacher");
                    item.put("teacherId", app.getTeacherId());
                    item.put("classId", app.getClassId());
                    item.put("className", app.getClassName());
                    item.put("teacherName", app.getTeacherName());
                    item.put("teacherUsername", app.getTeacherUsername());
                    item.put("status", app.getStatus());
                    item.put("createTime", app.getCreateTime());
                    item.put("reason", app.getReason());
                    // 待审核的行 rejectReason 必然是 null —— 但两个端点返回的是**同一种行形状**，
                    // 少一个 key 就会让"同一个列表换了个入口"的前端读到 undefined（本工程踩过多次）。
                    item.put("rejectReason", app.getRejectReason());
                    item.put("applicationType", app.getApplicationType());
                    pendingList.add(item);
                }
            }
            
            // 查询辅导员申请
            List<com.academic.entity.CounselorClassApplication> counselorApps = counselorClassApplicationMapper.selectList(null);
            for (com.academic.entity.CounselorClassApplication app : counselorApps) {
                if (app.getStatus() == 0) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", app.getId());
                    item.put("type", "counselor");
                    item.put("counselorId", app.getCounselorId());
                    item.put("classId", app.getClassId());
                    item.put("className", app.getClassName());
                    item.put("counselorName", app.getCounselorName());
                    item.put("status", app.getStatus());
                    item.put("createTime", app.getCreateTime());
                    item.put("reason", app.getReason());
                    item.put("rejectReason", app.getRejectReason());
                    item.put("applicationType", app.getApplicationType());
                    pendingList.add(item);
                }
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("total", pendingList.size());
            result.put("items", pendingList);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Get pending class requests failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/class-management/applications")
    public ApiResponse<Map<String, Object>> getClassManagementApplications(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        try {
            List<Map<String, Object>> resultList = new ArrayList<>();
            
            // 教师申请
            List<TeacherClassApplication> teacherApps = applicationMapper.selectList(null);
            for (TeacherClassApplication app : teacherApps) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", app.getId());
                item.put("type", "teacher");
                item.put("teacherId", app.getTeacherId());
                item.put("classId", app.getClassId());
                item.put("className", app.getClassName());
                item.put("teacherName", app.getTeacherName());
                item.put("teacherUsername", app.getTeacherUsername());
                item.put("status", app.getStatus());
                item.put("createTime", app.getCreateTime());
                item.put("updateTime", app.getUpdateTime());
                item.put("reason", app.getReason());
                // 2026-10-02（W1.14）：拒绝理由此前不在这张表的返回里，
                // 于是管理端「全部申请」里 `status=2` 的行只显示一个「已拒绝」标签，
                // 看不到自己当初填的理由。
                item.put("rejectReason", app.getRejectReason());
                item.put("applicationType", app.getApplicationType());
                resultList.add(item);
            }
            
            // 辅导员申请
            List<com.academic.entity.CounselorClassApplication> counselorApps = counselorClassApplicationMapper.selectList(null);
            if (counselorApps == null || counselorApps.isEmpty()) {
                // selectList 空时尝试原生 SQL
                List<Map<String, Object>> rawApps = counselorClassApplicationMapper.getAllApplicationsRaw();
                if (rawApps != null) {
                    for (Map<String, Object> row : rawApps) {
                        Map<String, Object> item = new HashMap<>();
                        item.put("id", row.get("id"));
                        item.put("type", "counselor");
                        item.put("counselorId", row.get("counselor_id"));
                        item.put("classId", row.get("class_id"));
                        item.put("className", row.get("class_name"));
                        item.put("counselorName", row.get("counselor_name"));
                        item.put("status", row.get("status"));
                        item.put("createTime", row.get("create_time") != null ? row.get("create_time").toString() : null);
                        item.put("updateTime", row.get("update_time") != null ? row.get("update_time").toString() : null);
                        item.put("reason", row.get("reason"));
                        item.put("rejectReason", row.get("reject_reason"));
                        item.put("applicationType", row.get("application_type"));
                        resultList.add(item);
                    }
                }
            } else {
                for (com.academic.entity.CounselorClassApplication app : counselorApps) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", app.getId());
                    item.put("type", "counselor");
                    item.put("counselorId", app.getCounselorId());
                    item.put("classId", app.getClassId());
                    item.put("className", app.getClassName());
                    item.put("counselorName", app.getCounselorName());
                    item.put("status", app.getStatus());
                    item.put("createTime", app.getCreateTime());
                    item.put("updateTime", app.getUpdateTime());
                    item.put("reason", app.getReason());
                    item.put("rejectReason", app.getRejectReason());
                    item.put("applicationType", app.getApplicationType());
                    resultList.add(item);
                }
            }
            
            // 按创建时间倒序
            resultList.sort((a, b) -> {
                Object ta = a.get("createTime");
                Object tb = b.get("createTime");
                if (ta == null || tb == null) return 0;
                return tb.toString().compareTo(ta.toString());
            });
            
            int total = resultList.size();
            int start = (page - 1) * size;
            int end = Math.min(start + size, total);
            List<?> pageItems = start < total ? resultList.subList(start, end) : List.of();
            
            Map<String, Object> result = new HashMap<>();
            result.put("total", total);
            result.put("items", pageItems);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Get class management applications failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/class-management/approve/{requestId}")
    public ApiResponse<String> approveClassRequest(@PathVariable Long requestId, @RequestParam(defaultValue = "teacher") String type) {
        try {
            if ("counselor".equals(type)) {
                com.academic.entity.CounselorClassApplication app = counselorClassApplicationMapper.selectById(requestId);
                if (app != null) {
                    app.setStatus(1);
                    counselorClassApplicationMapper.updateById(app);
                    // 更新班级的辅导员ID
                    if (app.getClassId() != null) {
                        Classes cls = classesMapper.selectById(app.getClassId());
                        if (cls != null) {
                            cls.setCounselorId(app.getCounselorId());
                            classesMapper.updateById(cls);
                        }
                    }
                    return ApiResponse.success("已批准辅导员班级申请");
                }
            } else {
                TeacherClassApplication application = applicationMapper.selectById(requestId);
                if (application != null) {
                    application.setStatus(1);
                    applicationMapper.updateById(application);
                    return ApiResponse.success("Application approved successfully");
                }
            }
            return ApiResponse.error("Application not found");
        } catch (Exception e) {
            log.error("Approve class request failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 拒绝一条班级管理申请（教师 / 辅导员两张表，靠 {@code type} 区分）。
     *
     * <p>⚠️ 两张表**都有 id=1**，所以 {@code type} 传错就会改到另一张表里同号的那条上 ——
     * 前端一律从数据行本身取 {@code type}，不由筛选器决定（见小程序 `class-requests.vue` 的注释）。
     *
     * <p>2026-10-02（W1.14）：{@code rejectData} 这个参数**此前收下后一次都没读**，
     * 于是「拒绝理由」从页面上发出来就没了 —— 而 PC 与小程序两个前端**都收了它、
     * 也真的发了出去**（PC 那个还做了非空校验）。现在把 {@code reason} 落到
     * `teacher_class_application.reject_reason` / `counselor_class_application.reject_reason`
     * （后者的列是同批新加的）。理由为空时**不写**（保持 NULL），不编一个空串。
     */
    @PostMapping("/class-management/reject/{requestId}")
    public ApiResponse<String> rejectClassRequest(
            @PathVariable Long requestId,
            @RequestBody(required = false) Map<String, String> rejectData,
            @RequestParam(defaultValue = "teacher") String type) {
        try {
            // 前端发的是 `{reason}`；空串与空白一律归成 null，别把 "" 当成"有理由"
            String reason = rejectData == null ? null : rejectData.get("reason");
            if (reason != null && reason.trim().isEmpty()) {
                reason = null;
            } else if (reason != null) {
                reason = reason.trim();
            }

            if ("counselor".equals(type)) {
                com.academic.entity.CounselorClassApplication app = counselorClassApplicationMapper.selectById(requestId);
                if (app != null) {
                    app.setStatus(2);
                    app.setRejectReason(reason);
                    counselorClassApplicationMapper.updateById(app);
                    return ApiResponse.success("已拒绝辅导员班级申请");
                }
            } else {
                TeacherClassApplication application = applicationMapper.selectById(requestId);
                if (application != null) {
                    application.setStatus(2);
                    application.setRejectReason(reason);
                    applicationMapper.updateById(application);
                    return ApiResponse.success("Application rejected successfully");
                }
            }
            return ApiResponse.error("Application not found");
        } catch (Exception e) {
            log.error("Reject class request failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    private LocalDate parseDate(String dateStr) {
        try {
            // 处理 ISO 8601 格式（如 2026-05-28T16:00:00.000Z）
            if (dateStr.contains("T")) {
                dateStr = dateStr.substring(0, dateStr.indexOf("T"));
            }
            return LocalDate.parse(dateStr);
        } catch (Exception e) {
            log.warn("Failed to parse date: {}", dateStr);
            return null;
        }
    }
    
    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }
    
    private Map<String, String> dataTypeMap = new HashMap<>() {{
        put("students", "学生数据");
        put("teachers", "教师数据");
        put("colleges", "学院数据");
        put("majors", "专业数据");
        put("courses", "课程数据");
        put("scores", "成绩数据");
        put("warnings", "预警数据");
        put("users", "用户数据");
        put("classes", "班级数据");
    }};
    
    // ============= 导出历史接口 =============
    @GetMapping("/export/history")
    public ApiResponse<Map<String, Object>> getExportHistory(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        try {
            List<ExportHistory> histories = exportHistoryMapper.selectList(null);
            List<Map<String, Object>> resultList = new ArrayList<>();
            
            for (ExportHistory history : histories) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", history.getId());
                item.put("dataType", history.getDataType());
                item.put("dataTypeName", dataTypeMap.getOrDefault(history.getDataType(), history.getDataType()));
                item.put("fileName", history.getFileName());
                item.put("recordCount", history.getRecordCount());
                item.put("exportedBy", history.getExportedBy());
                item.put("createdAt", history.getCreatedAt());
                
                String filePath = "exports/" + history.getFileName();
                File file = new File(filePath);
                if (file.exists()) {
                    item.put("fileSize", formatFileSize(file.length()));
                } else {
                    item.put("fileSize", "文件不存在");
                }
                
                resultList.add(item);
            }
            
            int total = resultList.size();
            int start = (page - 1) * size;
            int end = Math.min(start + size, total);
            List<?> pageItems = start < total ? resultList.subList(start, end) : List.of();
            
            Map<String, Object> result = new HashMap<>();
            result.put("total", total);
            result.put("items", pageItems);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Get export history failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @DeleteMapping("/export/{exportId}")
    public ApiResponse<String> deleteExport(@PathVariable Long exportId) {
        try {
            return exportHistoryMapper.deleteById(exportId) > 0
                    ? ApiResponse.success("Export deleted successfully")
                    : ApiResponse.notFound("Export not found");
        } catch (Exception e) {
            log.error("Delete export failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    // ============= 报表管理接口 =============

    /** 四张内置报表的模板 ID。与 {@code getReportHeaders}/{@code getReportData} 的 switch 分支一一对应。*/
    private static final Set<String> REPORT_TEMPLATE_IDS = Set.of("1", "2", "3", "4");

    @GetMapping("/reports/templates")
    public ApiResponse<List<Map<String, Object>>> getReportTemplates() {
        try {
            List<Map<String, Object>> templates = new ArrayList<>();
            
            Map<String, Object> template1 = new HashMap<>();
            template1.put("id", "1");
            template1.put("name", "学生预警明细报表");
            // 说明改成与 getReportHeaders("1") 真正产出的列一致。原标题写的是
            // "学生人数、专业分布、预警情况"，而报表实际输出的是**逐条预警明细**
            // （学号/姓名/专业/预警等级/预警时间），没有"人数"也没有"专业分布" ——
            // 描述与产物不符，点下载的人会以为拿到的是一张汇总表。
            template1.put("description", "逐条列出学生预警记录：学号、姓名、专业、预警等级、预警时间");
            template1.put("category", "student");
            templates.add(template1);
            
            Map<String, Object> template2 = new HashMap<>();
            template2.put("id", "2");
            template2.put("name", "成绩分析报表");
            template2.put("description", "包含课程成绩分布、不及格率、平均分等分析");
            template2.put("category", "score");
            templates.add(template2);
            
            Map<String, Object> template3 = new HashMap<>();
            template3.put("id", "3");
            template3.put("name", "预警统计报表");
            template3.put("description", "包含预警等级分布、处理进度等统计信息");
            template3.put("category", "warning");
            templates.add(template3);
            
            Map<String, Object> template4 = new HashMap<>();
            template4.put("id", "4");
            template4.put("name", "学院对比报表");
            template4.put("description", "各学院数据对比分析");
            template4.put("category", "college");
            templates.add(template4);
            
            return ApiResponse.success(templates);
        } catch (Exception e) {
            log.error("Get report templates failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/reports/generate")
    public ApiResponse<Map<String, Object>> generateReport(@RequestBody Map<String, Object> reportData) {
        try {
            String templateId = (String) reportData.get("template_id");
            String startDate = (String) reportData.get("start_date");
            String endDate = (String) reportData.get("end_date");
            
            log.info("Generate report request: templateId={}, startDate={}, endDate={}", templateId, startDate, endDate);

            if (!REPORT_TEMPLATE_IDS.contains(templateId)) {
                // 走 400 而不是让 generateReportFile 抛出去翻成 500：前端拦截器只认
                // code===200，这里给的是一句能读懂的话，不是"服务器内部错误"。
                return ApiResponse.error(400, "不支持的报表模板: " + templateId);
            }

            // 日期校验
            LocalDate today = LocalDate.now();
            
            // 解析开始日期
            LocalDate start = null;
            if (startDate != null && !startDate.isEmpty()) {
                start = parseDate(startDate);
                if (start == null) {
                    return ApiResponse.error(400, "开始日期格式不正确");
                }
            }
            
            // 解析结束日期
            LocalDate end = null;
            if (endDate != null && !endDate.isEmpty()) {
                end = parseDate(endDate);
                if (end == null) {
                    return ApiResponse.error(400, "结束日期格式不正确");
                }
                
                // 检查结束日期是否超过当前日期
                if (end.isAfter(today)) {
                    return ApiResponse.error(400, "结束日期不能超过当前日期");
                }
            }
            
            // 检查开始日期是否大于结束日期
            if (start != null && end != null && start.isAfter(end)) {
                return ApiResponse.error(400, "开始日期不能大于结束日期");
            }
            
            // 根据模板ID生成报表
            GeneratedReport report = generateReportFile(templateId, startDate, endDate);

            Map<String, Object> result = new HashMap<>();
            result.put("fileName", report.fileName());
            result.put("downloadUrl", "/admin/export/reports/" + report.fileName());
            // W23：把"这份报表实际写了几行数据"回给前端。此前数据源为空时生成的是一份
            // **只有表头**的 xlsx（服务端只 log.warn），而前端一律弹「报表已生成」——
            // 与"恒 0 的 total"同族：拿到了文件，却不知道里面是空的。rows 让前端能如实说。
            result.put("rows", report.rows());

            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Generate report failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 一次报表生成的结果。
     *
     * @param fileName 生成的文件名（下载 URL 由它拼出）
     * @param rows     真正写进 xlsx 的数据行数（不含表头）
     * @param matched  表头是否逐字匹配上了某张内置报表。**只对自定义模板路径有意义** ——
     *                 系统模板用的就是内置报表自己的表头，这里恒为 {@code true}。
     *                 它把"表头没认出来"与"认出报表了但它当前没有数据行"分开，
     *                 这两种对用户的说法完全不同。
     */
    private record GeneratedReport(String fileName, int rows, boolean matched) {}

    private GeneratedReport generateReportFile(String templateId, String startDate, String endDate) throws Exception {
        if (!REPORT_TEMPLATE_IDS.contains(templateId)) {
            // 原来是 default 分支：表头写成"序号/数据1/数据2/数据3"、数据行空，
            // 然后照样回 success + 一个可下载的 xlsx。调用方拿到一份全是占位列的文件，
            // 却以为报表生成成功了。缺参数就是缺参数。
            throw new IllegalArgumentException("不支持的报表模板: " + templateId);
        }
        String fileName = "report_" + templateId + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx";
        // 2026-10-01 第二十四轮：原先是写死的 "exports/" 且**从不 mkdirs**，
        // 而同一个类里其余 9 个导出方法全都是 `exportDir + mkdirs()`。后果：
        //  - 首次点「生成报表」必然 500 `exports\report_1_....xlsx (系统找不到指定的路径。)`；
        //  - 即使目录恰好存在，下载端 downloadReport 也读的是另一处写死的 "exports/"，
        //    一旦 export.dir 配成别的目录，生成与下载就会各写各的。
        // 两处（这里 + downloadReport）必须一起改成同一个 exportDir。
        String filePath = exportDir + "/" + fileName;
        File exportDirFile = new File(exportDir);
        if (!exportDirFile.exists()) {
            exportDirFile.mkdirs();
        }

        // 创建工作簿
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("报表数据");

        // 根据模板ID设置表头
        String[] headers = getReportHeaders(templateId);
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }

        List<List<String>> data = getReportData(templateId);
        for (int i = 0; i < data.size(); i++) {
            Row row = sheet.createRow(i + 1);
            List<String> rowData = data.get(i);
            for (int j = 0; j < rowData.size(); j++) {
                row.createCell(j).setCellValue(rowData.get(j));
            }
        }

        // 自动调整列宽
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        // 保存文件
        try (FileOutputStream outputStream = new FileOutputStream(filePath)) {
            workbook.write(outputStream);
        }
        workbook.close();

        if (data.isEmpty()) {
            // 数据源为空（例如库里还没有成绩/预警）时输出的是"只有表头"的报表。
            // 这是真实状态，不是错误 —— 但不能静默，留一条日志便于追问。
            log.warn("Report generated with NO data rows: {} (模板 {})", fileName, templateId);
        }
        log.info("Report generated: {} ({} rows)", fileName, data.size());
        return new GeneratedReport(fileName, data.size(), true);
    }

    /**
     * 四张内置报表的表头。**这里是列名契约**：{@link #generateReportFile} 按它写 xlsx 表头，
     * 自定义模板路径（{@link #generateReportWithCustomTemplate}）也按它做列名匹配取数，
     * 所以改列名要连着看 {@link #getReportData}。
     */
    private String[] getReportHeaders(String templateId) {
        return switch (templateId) {
            // 「学生ID」改成「学号」：这一列给的是 student_profile.student_id（学号字符串），
            // 不是 student_profile.id（主键）。两个 id 空间在本工程里已经踩过三次，
            // 表头写错一个词就会让人按主键去查，永远查不到。
            case "1" -> new String[]{"学号", "姓名", "专业", "预警等级", "预警时间"};
            case "2" -> new String[]{"课程名称", "总人数", "及格人数", "不及格率", "平均分"};
            // 原表头是「预警等级 / 数量 / 处理状态 / 处理时间」，一行一个"等级×状态"的
            // 组合，但数据却是手写的三行假话，且"处理时间"对一个聚合行没有意义。
            // 改成交叉表：一级一行，按状态分列 —— 这正好是 academic_warning 真实能算出来的形状。
            // 2026-10-02（W1.6）：状态是三态，所以"待处理/已处理"两列不够 ——
            // `status=2`（已解除）原来被并进「待处理」列（`status == 1 ? 1 : 0`），
            // 报表上"待处理"因此比实际多、且永远看不到"已解除"。补第三列。
            case "3" -> new String[]{"预警等级", "待处理", "已处理", "已解除", "合计"};
            case "4" -> new String[]{"学院名称", "学生人数", "教师人数", "辅导员人数"};
            default -> new String[]{"序号", "数据1", "数据2", "数据3"};
        };
    }

    /**
     * 按模板取数。
     *
     * <p>2026-10-01 第二十四轮：这里整块原来是**写死的假数据** —— 张三/李四/王五、
     * 「高等数学 50 人 42 及格 16% 78.5」、「计算机学院 1200 人 85 教师」，
     * 学院的数字还是页面里根本没出现过的编造学院名。它不会报错、会稳定产出 xlsx，
     * 所以从症状上看不出来。演示数据是自编的合成数据，标准是"自洽、经得起追问"，
     * 「代码绕开了所以没事」在这里不成立 —— 报表必须对着活库真算。
     *
     * <p>现在的数据源：
     * <ul>
     *   <li>模板1 学生预警明细 → {@code academic_warning} + {@code student_profile} + {@code majors}</li>
     *   <li>模板2 课程成绩分析 → {@link ScoreStatMapper#selectCourseStatsAll()}（走 ScoreCaliber）</li>
     *   <li>模板3 预警等级统计 → {@code academic_warning} 按 等级×状态 现算</li>
     *   <li>模板4 学院对比 → {@code colleges} + 三个既有 count 助手（与 {@code exportColleges} 同源）</li>
     * </ul>
     */
    private List<List<String>> getReportData(String templateId) {
        return switch (templateId) {
            case "1" -> buildStudentWarningRows();
            case "2" -> buildCourseScoreRows();
            case "3" -> buildWarningStatRows();
            case "4" -> buildCollegeCompareRows();
            default -> new ArrayList<>();
        };
    }

    /** 模板1：学生预警明细（学号 / 姓名 / 专业 / 预警等级 / 预警时间）。*/
    private List<List<String>> buildStudentWarningRows() {
        List<List<String>> rows = new ArrayList<>();
        List<AcademicWarning> warnings = academicWarningMapper.selectList(null);
        if (warnings.isEmpty()) {
            return rows;
        }
        // 批量取学生档案与专业，不做逐行查询。
        //
        // ⚠️ academic_warning.student_id 是 bigint，存的是 student_profile.**id**（主键），
        // 不是学号字符串。这里按主键批量查；输出那一列才换成 studentId（学号）。
        // 两个 id 空间是同一个坑（本工程已踩过三次）。
        Set<Long> profileIds = warnings.stream()
                .map(AcademicWarning::getStudentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, StudentProfile> profileById = profileIds.isEmpty()
                ? Collections.emptyMap()
                : studentProfileMapper.selectBatchIds(profileIds).stream()
                        .collect(Collectors.toMap(StudentProfile::getId, p -> p, (a, b) -> a));
        Map<Long, String> majorNameById = majorService.list().stream()
                .filter(m -> m.getId() != null)
                .collect(Collectors.toMap(Major::getId, m -> nz(m.getName()), (a, b) -> a));

        // 时间倒序，与预警列表页口径一致。
        warnings.sort(Comparator.comparing(AcademicWarning::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        for (AcademicWarning w : warnings) {
            StudentProfile p = w.getStudentId() == null ? null : profileById.get(w.getStudentId());
            String studentNo = (p != null && p.getStudentId() != null)
                    ? p.getStudentId()
                    : (w.getStudentId() == null ? "" : String.valueOf(w.getStudentId()));
            String name = (p != null && p.getName() != null) ? p.getName() : nz(w.getStudentName());
            String majorName = (p != null && p.getMajorId() != null)
                    ? majorNameById.getOrDefault(p.getMajorId(), "")
                    : "";
            rows.add(List.of(
                    studentNo,
                    name,
                    majorName,
                    warningLevelText(w.getWarningLevel()),
                    w.getCreatedAt() == null ? "" : w.getCreatedAt().toLocalDate().toString()));
        }
        return rows;
    }

    /** 模板2：课程成绩分析（课程名称 / 总人数 / 及格人数 / 不及格率 / 平均分）。*/
    private List<List<String>> buildCourseScoreRows() {
        List<List<String>> rows = new ArrayList<>();
        for (Map<String, Object> c : scoreStatMapper.selectCourseStatsAll()) {
            long total = asLong(c.get("record_count"));
            long pass = asLong(c.get("pass_count"));
            // 及格线口径来自 ScoreCaliber.PASS_LINE（mapper 里做的判断），这里只算比例。
            // 显式 Locale.ROOT：String.format 会跟 JVM 默认区域走，德/法区域下
            // "%.2f" 的小数点是逗号，报表里的数字会在别人的机器上变成 78,50。
            String failRate = total == 0
                    ? "0.00%"
                    : String.format(Locale.ROOT, "%.2f%%", (total - pass) * 100.0 / total);
            String avg = c.get("avg_score") == null
                    ? ""
                    : String.format(Locale.ROOT, "%.2f", asDouble(c.get("avg_score")));
            rows.add(List.of(
                    nz(asString(c.get("course_name"))),
                    String.valueOf(total),
                    String.valueOf(pass),
                    failRate,
                    avg));
        }
        return rows;
    }

    /** 模板3：预警等级统计（等级 / 待处理 / 已处理 / 已解除 / 合计）。*/
    private List<List<String>> buildWarningStatRows() {
        List<List<String>> rows = new ArrayList<>();
        // 固定 1/2/3 三档，保证"某一档一条都没有"时也留一行 0（否则报表会缺档，
        // 看起来像漏了数据）。等级口径与 warning_rules.name 一致：低级/中级/高级预警。
        Map<Integer, long[]> byLevel = new LinkedHashMap<>();
        byLevel.put(1, new long[3]);
        byLevel.put(2, new long[3]);
        byLevel.put(3, new long[3]);
        for (AcademicWarning w : academicWarningMapper.selectList(null)) {
            Integer level = w.getWarningLevel();
            if (level == null || !byLevel.containsKey(level)) {
                // 不在 1/2/3 之内的等级（历史脏数据）不归入任何一行，也不新增行，
                // 与前端 buildWarningTrend「未识别等级不归入任何一条」同一口径。
                continue;
            }
            // 2026-10-02（W1.6）：原来是 `status == 1 ? 1 : 0` 两桶 —— status=2（已解除）
            // 被算进"待处理"。三态各占一格，认不出的（含 null）按待处理，与
            // {@link com.academic.common.constant.WarningStatus} 同一口径。
            int idx;
            if (w.getStatus() == null) {
                idx = 0;
            } else {
                idx = switch (w.getStatus()) {
                    case com.academic.common.constant.WarningStatus.HANDLED -> 1;
                    case com.academic.common.constant.WarningStatus.DISPELLED -> 2;
                    default -> 0;
                };
            }
            byLevel.get(level)[idx]++;
        }
        for (Map.Entry<Integer, long[]> e : byLevel.entrySet()) {
            long[] s = e.getValue();
            rows.add(List.of(
                    warningLevelText(e.getKey()),
                    String.valueOf(s[0]),
                    String.valueOf(s[1]),
                    String.valueOf(s[2]),
                    String.valueOf(s[0] + s[1] + s[2])));
        }
        return rows;
    }

    /** 模板4：学院对比（学院名称 / 学生人数 / 教师人数 / 辅导员人数）。*/
    private List<List<String>> buildCollegeCompareRows() {
        List<List<String>> rows = new ArrayList<>();
        for (College college : collegeService.list()) {
            rows.add(List.of(
                    nz(college.getName()),
                    String.valueOf(getStudentCount(college.getId())),
                    String.valueOf(getTeacherCount(college.getId())),
                    String.valueOf(getCounselorCount(college.getId()))));
        }
        return rows;
    }

    /** 预警等级 → 中文。口径与 {@code warning_rules.name}、前端 warningLevelColor 一致。*/
    private static String warningLevelText(Integer level) {
        if (level == null) {
            return "";
        }
        return switch (level) {
            case 1 -> "低级预警";
            case 2 -> "中级预警";
            case 3 -> "高级预警";
            default -> "未知等级";
        };
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }

    private static long asLong(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }

    private static double asDouble(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0.0;
    }

    // ============= 报表下载接口 =============
    @GetMapping("/export/reports/{fileName}")
    public void downloadReport(@PathVariable String fileName, HttpServletResponse response) throws IOException {
        try {
            // 必须与 generateReportFile 用同一个 exportDir：两边都写死 "exports/" 时
            // 一旦 export.dir 被配成别的目录，生成写 A、下载读 B，表现为"生成成功但下载 404"。
            String filePath = exportDir + "/" + fileName;
            File file = new File(filePath);
            
            if (!file.exists()) {
                log.error("Report file not found: {}", filePath);
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "报表文件不存在");
                return;
            }
            
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString()));
            
            Files.copy(file.toPath(), response.getOutputStream());
            
            log.info("Download report successfully: {}", fileName);
        } catch (Exception e) {
            log.error("Download report failed: {}", e.getMessage());
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "下载报表失败");
        }
    }

    // ============= 数据备份接口 =============
    //
    // ⚠️ 2026-09-30：这一组接口原来是**编造的** ——
    //   · `backupData` 只 log 一行、只在内存里拼了个文件名，**磁盘上什么也没有**，却回
    //     "Backup created successfully: backup_xxx.sql"；
    //   · `getBackupList` 返回**三条写死的假备份**（backup_20240101_120000.sql 等，
    //     连大小 2.5/3.2/2.8 MB 和 2024 年的日期都是编的）；
    //   · `restoreBackup` 直接回成功，不做任何恢复。
    //   这是最典型的"报告与事实不符"：页面上列着三条备份历史，磁盘和库里却一条都不存在，
    //   答辩时被问一句"这几个 sql 文件在哪"就答不上来。
    //
    // ⚠️ 2026-10-02（W1.10）：**真的实现了** —— 见 `BackupService`。
    //   备份 = 真起 `mysqldump` 子进程写 .sql；列表 = 扫目录里的真文件；
    //   恢复 = 在**临时库**上真灌一遍再删掉（**绝不覆盖演示库**，理由写在 BackupService 类注释里）。
    //   工具路径取不到时**如实报错**，不再回"成功"。

    @Autowired
    private BackupService backupService;

    @PostMapping("/backup")
    public ApiResponse<Map<String, Object>> backupData() {
        try {
            return ApiResponse.success(backupService.createBackup());
        } catch (Exception e) {
            // 抛出来的是"工具缺失 / 命令失败 / 产物为空"这类真原因，如实透给页面
            log.error("Backup failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/backups/list")
    public ApiResponse<Map<String, Object>> getBackupList() {
        try {
            List<Map<String, Object>> items = backupService.listBackups();
            Map<String, Object> result = new HashMap<>();
            result.put("total", items.size());
            result.put("items", items);
            result.put("implemented", true);
            result.put("dir", backupService.backupDirPath());
            result.put("note", items.isEmpty()
                    ? "备份目录 " + backupService.backupDirPath() + " 里还没有 .sql 文件，点「立即备份数据」即可生成一份"
                    : "备份文件在 " + backupService.backupDirPath() + "，可直接拷走留存");
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("List backups failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 恢复**演练**：把备份文件恢复到一个临时库、数一遍表、再删掉临时库。
     *
     * <p>刻意**不覆盖当前演示库** —— 理由见 {@code BackupService} 的类注释。
     * 返回体里的 {@code note} 会把这件事写清楚，页面照原话显示。
     */
    @PostMapping("/backup/{backupId}/restore")
    public ApiResponse<Map<String, Object>> restoreBackup(@PathVariable String backupId) {
        try {
            return ApiResponse.success(backupService.restoreIntoScratchDb(backupId));
        } catch (Exception e) {
            log.error("Restore backup failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /** 删除一个备份文件。 */
    @DeleteMapping("/backup/{backupId}")
    public ApiResponse<String> deleteBackup(@PathVariable String backupId) {
        try {
            boolean deleted = backupService.deleteBackup(backupId);
            return deleted
                    ? ApiResponse.success("备份已删除")
                    : ApiResponse.error("备份文件不存在: " + backupId);
        } catch (Exception e) {
            log.error("Delete backup failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    // ============= 消息管理补充接口 =============
    @PostMapping("/messages/targeted")
    public ApiResponse<String> sendTargetedMessage(@RequestBody Map<String, Object> messageData) {
        try {
            return messageServiceClient.sendMessage(messageData);
        } catch (Exception e) {
            log.error("Send targeted message failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @DeleteMapping("/messages/{messageId}")
    public ApiResponse<String> deleteMessage(@PathVariable Long messageId) {
        try {
            return messageServiceClient.deleteMessageById(messageId);
        } catch (Exception e) {
            log.error("Delete message failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    // ============= 报表模板管理接口 =============
    @GetMapping("/reports/templates/list")
    public ApiResponse<List<Map<String, Object>>> getAllReportTemplates() {
        try {
            List<ReportTemplate> templates = reportTemplateService.getAllTemplates();
            List<Map<String, Object>> result = new ArrayList<>();
            
            for (ReportTemplate template : templates) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", template.getId());
                item.put("name", template.getName());
                item.put("description", template.getDescription());
                item.put("fileName", template.getFileName());
                item.put("fileSize", formatFileSize(template.getFileSize() != null ? template.getFileSize() : 0));
                item.put("status", template.getStatus());
                item.put("createdAt", template.getCreatedAt());
                item.put("type", "custom");
                result.add(item);
            }
            
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Get report templates failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/reports/templates/upload")
    public ApiResponse<Map<String, Object>> uploadReportTemplate(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description) {
        try {
            if (file.isEmpty()) {
                return ApiResponse.error(400, "请选择要上传的文件");
            }
            
            if (file.getSize() > 10 * 1024 * 1024) {
                return ApiResponse.error(400, "文件大小不能超过10MB");
            }

            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || (!originalFilename.endsWith(".xlsx") && !originalFilename.endsWith(".xls"))) {
                return ApiResponse.error(400, "只支持 Excel 文件格式（.xlsx, .xls）");
            }

            File templateDirFile = new File(templateDir);
            if (!templateDirFile.exists()) {
                templateDirFile.mkdirs();
            }

            String fileName = "template_" + System.currentTimeMillis() + "_" + originalFilename;
            String filePath = templateDir + "/" + fileName;
            
            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                fos.write(file.getBytes());
            }

            ReportTemplate template = reportTemplateService.uploadTemplate(
                    name,
                    description != null ? description : "",
                    filePath,
                    fileName,
                    file.getSize(),
                    "xlsx"
            );

            Map<String, Object> result = new HashMap<>();
            result.put("id", template.getId());
            result.put("name", template.getName());
            result.put("message", "模板上传成功");
            
            log.info("Report template uploaded: {}, size: {} bytes", name, file.getSize());
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Upload report template failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @DeleteMapping("/reports/templates/{templateId}")
    public ApiResponse<String> deleteReportTemplate(@PathVariable Long templateId) {
        try {
            // 2026-10-03（W7）：读返回值 —— 模板不存在时不再回"删除成功"。
            return reportTemplateService.deleteTemplate(templateId)
                    ? ApiResponse.success("模板删除成功")
                    : ApiResponse.notFound("模板不存在");
        } catch (Exception e) {
            log.error("Delete report template failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/reports/generate-with-template")
    public ApiResponse<Map<String, Object>> generateReportWithTemplate(@RequestBody Map<String, Object> reportData) {
        try {
            String templateId = (String) reportData.get("template_id");
            String customTemplateId = reportData.get("custom_template_id") != null ? 
                String.valueOf(reportData.get("custom_template_id")) : null;
            String startDate = (String) reportData.get("start_date");
            String endDate = (String) reportData.get("end_date");

            log.info("Generate report request: templateId={}, customTemplateId={}, startDate={}, endDate={}", 
                    templateId, customTemplateId, startDate, endDate);

            LocalDate today = LocalDate.now();
            LocalDate start = startDate != null && !startDate.isEmpty() ? parseDate(startDate) : null;
            LocalDate end = endDate != null && !endDate.isEmpty() ? parseDate(endDate) : null;

            if (end != null && end.isAfter(today)) {
                return ApiResponse.error(400, "结束日期不能超过当前日期");
            }
            if (start != null && end != null && start.isAfter(end)) {
                return ApiResponse.error(400, "开始日期不能大于结束日期");
            }

            boolean isCustom = customTemplateId != null && !customTemplateId.isEmpty();
            GeneratedReport report = isCustom
                    ? generateReportWithCustomTemplate(customTemplateId, startDate, endDate)
                    : generateReportFile(templateId, startDate, endDate);
            String fileName = report.fileName();

            Map<String, Object> result = new HashMap<>();
            result.put("fileName", fileName);
            result.put("downloadUrl", "/admin/export/reports/" + fileName);
            result.put("rows", report.rows());
            // W23：`matched` 只在自定义模板这条路上有意义（系统模板的表头就是内置报表自己的）。
            // 系统模板路径**不放这个键** —— 不是 `false`，是"不适用"，前端据此也能分辨。
            if (isCustom) {
                result.put("matched", report.matched());
            }

            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Generate report failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    // ==================== P0新增：权限管理 ====================
    @Autowired
    private com.academic.mapper.RoleMapper roleMapper;

    @Autowired
    private com.academic.mapper.PermissionMapper permissionMapper;

    @Autowired
    private com.academic.mapper.RolePermissionMapper rolePermissionMapper;

    /**
     * 角色列表（含每个角色已绑定的权限码）。
     *
     * <p>2026-10-01 第二十四轮：原来直接回 {@code roleMapper.selectList(null)}，
     * 而 {@code Role} 实体上**没有 permissions 字段** ⇒ 前端「权限数」「权限列表」两列
     * 恒为空（模板里用 {@code row.permissions || []} 兜着，所以显示的是 0，不报错，
     * 但活库 {@code role_permissions} 明明有 21 行绑定）。现在按角色把权限码一并查出来。
     *
     * <p>字段只增不减：{@code id/name/code/description/status/createdAt/updatedAt} 原样保留，
     * 新增 {@code permissions}（权限码数组）与 {@code permissionIds}。
     */
    @GetMapping("/permissions/roles")
    public ApiResponse<List<Map<String, Object>>> getRoles() {
        try {
            List<com.academic.entity.Role> roles = roleMapper.selectList(null);
            Map<Long, List<Permission>> permsByRole = permissionsByRole();
            List<Map<String, Object>> result = new ArrayList<>();
            for (com.academic.entity.Role role : roles) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", role.getId());
                item.put("name", role.getName());
                item.put("code", role.getCode());
                item.put("description", role.getDescription());
                item.put("status", role.getStatus());
                item.put("createdAt", role.getCreatedAt());
                item.put("updatedAt", role.getUpdatedAt());
                List<Permission> perms = permsByRole.getOrDefault(role.getId(), List.of());
                item.put("permissions", perms.stream().map(Permission::getCode).toList());
                item.put("permissionIds", perms.stream().map(Permission::getId).toList());
                result.add(item);
            }
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Get roles failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 每个角色绑定了哪些权限。
     *
     * <p>两次查询在内存里拼，不做 N+1：{@code role_permissions} 全表（21 行）+
     * {@code permissions} 全表（9 行），按 id 建映射即可。角色/权限都是小表且不随时间增长。
     */
    private Map<Long, List<Permission>> permissionsByRole() {
        Map<Long, Permission> permById = permissionMapper.selectList(null).stream()
                .filter(p -> p.getId() != null)
                .collect(Collectors.toMap(Permission::getId, p -> p, (a, b) -> a));
        Map<Long, List<Permission>> byRole = new HashMap<>();
        for (RolePermission rp : rolePermissionMapper.selectList(null)) {
            Permission p = permById.get(rp.getPermissionId());
            if (p != null) {
                byRole.computeIfAbsent(rp.getRoleId(), k -> new ArrayList<>()).add(p);
            }
        }
        return byRole;
    }

    /**
     * {@code users.role} 的数值 → 中文角色名。
     *
     * <p>这是**系统里唯一权威的那套角色词表**（`users.role` 是固定枚举 1..5，
     * 见 {@code GatewayAuthFilter.roleMapping} 与前端各处的 roleMap）。
     * {@code roles} 表只是它的"展示副本"，两者不要互相推导。
     */
    private static String getRoleNameText(Integer role) {
        if (role == null) {
            return "未知";
        }
        return switch (role) {
            case 1 -> "学生";
            case 2 -> "教师";
            case 3 -> "辅导员";
            case 4 -> "管理员";
            case 5 -> "学院管理员";
            default -> "未知";
        };
    }

    /**
     * 内置角色：这 5 个 code 是系统里**固定的一套角色词表**，删了会断。
     *
     * <p>1–4 的 code 被 {@code PermissionMapper.selectByRoleCode} 按名认人；
     * 5（{@code ROLE_COLLEGE_ADMIN}）与人数的对应关系见 {@code GatewayAuthFilter.roleMapping}
     * 的 {@code case "5"}。**`roles` 表的行数与 `users.role` 的取值集合必须相等** ——
     * 2026-10-01 第二十四轮发现活库少了 5（活库 4 行、种子 5 行），已按种子补回。
     */
    private static final Set<String> BUILTIN_ROLE_CODES =
            Set.of("ROLE_STUDENT", "ROLE_TEACHER", "ROLE_COUNSELOR", "ROLE_ADMIN", "ROLE_COLLEGE_ADMIN");

    /**
     * 新建角色。
     *
     * <p>2026-10-01 第二十四轮：前端 {@code AdminPermissions.vue} 的「创建」此前是
     * {@code ElMessage.success('角色已创建')} 然后**什么都不做** —— 一个假成功，
     * 弹窗关了、列表刷新了，库里一行没多。这里补上真写入。
     *
     * <p>请求体：{@code {name, description?, code?, permissions?: [权限码...]}}。
     * {@code code} 不给就按 name 生成（纯中文名会退化成 {@code ROLE_CUSTOM_<时间戳>}）；
     * {@code roles.code} 是 UNIQUE NOT NULL，所以生成后还要去重。
     *
     * <p>权限码里有一个查不到就**报错**，不静默丢掉 —— 静默丢字段正是本工程在扫的缺陷类型。
     *
     * <p>{@code @Transactional}：写 {@code roles} + {@code role_permissions} 两张表，
     * 必须同一事务；方法体内**不 catch**（catch 了就成了假事务）。
     */
    @PostMapping("/permissions/roles")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Map<String, Object>> createRole(@RequestBody Map<String, Object> data) {
        String name = bodyText(data, "name");
        if (name == null) {
            throw new IllegalArgumentException("角色名称不能为空");
        }
        if (roleMapper.selectCount(new QueryWrapper<Role>().eq("name", name)) > 0) {
            throw new IllegalArgumentException("角色名称已存在: " + name);
        }
        String code = bodyText(data, "code");
        code = code == null ? generateRoleCode(name) : code.toUpperCase(Locale.ROOT);
        if (roleMapper.selectCount(new QueryWrapper<Role>().eq("code", code)) > 0) {
            throw new IllegalArgumentException("角色编码已存在: " + code);
        }
        List<Permission> perms = resolvePermissions(data.get("permissions"));

        Role role = new Role();
        role.setName(name);
        role.setCode(code);
        role.setDescription(bodyText(data, "description"));
        role.setStatus(1);
        role.setCreatedAt(LocalDateTime.now());
        role.setUpdatedAt(LocalDateTime.now());
        roleMapper.insert(role);

        bindPermissions(role.getId(), perms);

        Map<String, Object> result = new HashMap<>();
        result.put("id", role.getId());
        result.put("name", role.getName());
        result.put("code", role.getCode());
        result.put("permissionCount", perms.size());
        result.put("message", "角色已创建");
        return ApiResponse.success(result);
    }

    /**
     * 修改角色（名称 / 描述 / 权限绑定）。
     *
     * <p>请求体里**带了哪个字段就改哪个**：{@code permissions} 带空数组表示"清空该角色的权限"，
     * 不带则不动绑定 —— 与"带空串 = 清空输入框"是同一套口径。
     */
    @PutMapping("/permissions/roles/{roleId}")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Map<String, Object>> updateRole(@PathVariable Long roleId,
                                                       @RequestBody Map<String, Object> data) {
        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new ResourceNotFoundException("角色不存在: " + roleId);
        }
        String name = bodyText(data, "name");
        if (name != null && !name.equals(role.getName())) {
            if (roleMapper.selectCount(new QueryWrapper<Role>().eq("name", name).ne("id", roleId)) > 0) {
                throw new IllegalArgumentException("角色名称已存在: " + name);
            }
            role.setName(name);
        }
        if (data != null && data.containsKey("description")) {
            role.setDescription(bodyText(data, "description"));
        }
        role.setUpdatedAt(LocalDateTime.now());
        roleMapper.updateById(role);

        int permCount = -1;
        if (data != null && data.containsKey("permissions")) {
            List<Permission> perms = resolvePermissions(data.get("permissions"));
            rolePermissionMapper.delete(new QueryWrapper<RolePermission>().eq("role_id", roleId));
            bindPermissions(roleId, perms);
            permCount = perms.size();
        }
        // 内置角色的 code 不允许改：RoleMapper/PermissionMapper.selectByRoleCode 是按 code 认人的，
        // 改了 code 等于把该角色从权限体系里摘出去（users.role 还指着它）。请求体里带 code 一律忽略。

        Map<String, Object> result = new HashMap<>();
        result.put("id", roleId);
        result.put("name", role.getName());
        result.put("permissionCount", permCount);
        result.put("message", "角色已更新");
        return ApiResponse.success(result);
    }

    /**
     * 删除角色。
     *
     * <p>两条拒绝理由，都是**真校验**而不是走过场：
     * <ol>
     *   <li>内置角色（ROLE_STUDENT/TEACHER/COUNSELOR/ADMIN）不可删 —— 它们是权限体系按 code 认人的锚点；</li>
     *   <li>{@code users.role} 还指着这个 id 时不可删 —— 否则那些账号会指向一个不存在的角色。
     *       顺带说明：活库 {@code users.role=5}（学院管理员）在 {@code roles} 表里<b>本来就没有对应行</b>，
     *       所以这个外键关系本来就是"弱"的；这里只保证**不再新增**悬空。</li>
     * </ol>
     */
    @DeleteMapping("/permissions/roles/{roleId}")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<String> deleteRole(@PathVariable Long roleId) {
        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new ResourceNotFoundException("角色不存在: " + roleId);
        }
        if (BUILTIN_ROLE_CODES.contains(role.getCode())) {
            throw new IllegalArgumentException("内置角色不可删除: " + role.getCode());
        }
        long inUse = userService.count(new QueryWrapper<User>().eq("role", roleId));
        if (inUse > 0) {
            throw new IllegalArgumentException("仍有 " + inUse + " 个账号使用该角色，不能删除");
        }
        rolePermissionMapper.delete(new QueryWrapper<RolePermission>().eq("role_id", roleId));
        roleMapper.deleteById(roleId);
        return ApiResponse.success("角色删除成功");
    }

    /** 请求体里的字符串：null / 全空白都算没填。*/
    private static String bodyText(Map<String, Object> body, String key) {
        Object v = body == null ? null : body.get(key);
        if (v == null) {
            return null;
        }
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    /**
     * 权限码数组 → {@code Permission} 列表。
     *
     * <p>空/缺省 ⇒ 返回空列表（允许建一个暂时没有权限的角色）。
     * 有任何一个码在 {@code permissions} 表里查不到 ⇒ 抛 {@code IllegalArgumentException}（400），
     * 而不是把那一个悄悄丢掉。
     */
    private List<Permission> resolvePermissions(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> list)) {
            throw new IllegalArgumentException("permissions 必须是权限码数组");
        }
        List<String> codes = new ArrayList<>();
        for (Object o : list) {
            if (o == null) {
                continue;
            }
            String c = o.toString().trim();
            if (!c.isEmpty() && !codes.contains(c)) {
                codes.add(c);
            }
        }
        if (codes.isEmpty()) {
            return List.of();
        }
        List<Permission> found = permissionMapper.selectList(new QueryWrapper<Permission>().in("code", codes));
        Set<String> foundCodes = found.stream().map(Permission::getCode).collect(Collectors.toSet());
        for (String c : codes) {
            if (!foundCodes.contains(c)) {
                throw new IllegalArgumentException("权限不存在: " + c);
            }
        }
        return found;
    }

    /** 绑定角色与权限（只插 {@code role_permissions}，不删旧行；调用方负责先清）。*/
    private void bindPermissions(Long roleId, List<Permission> perms) {
        for (Permission p : perms) {
            RolePermission rp = new RolePermission();
            rp.setRoleId(roleId);
            rp.setPermissionId(p.getId());
            rp.setCreatedAt(LocalDateTime.now());
            rp.setUpdatedAt(LocalDateTime.now());
            rolePermissionMapper.insert(rp);
        }
    }

    /** 由角色名生成一个 UNIQUE 的 {@code roles.code}。名字里没有 ASCII 字母数字时退化成带时间戳的兜底码。*/
    private String generateRoleCode(String name) {
        StringBuilder sb = new StringBuilder();
        for (char ch : name.toUpperCase(Locale.ROOT).toCharArray()) {
            sb.append((ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') ? ch : '_');
        }
        String slug = sb.toString().replaceAll("_+", "_").replaceAll("^_+|_+$", "");
        String base = "ROLE_" + (slug.isEmpty() ? "CUSTOM_" + System.currentTimeMillis() : slug);
        String code = base;
        int n = 2;
        while (roleMapper.selectCount(new QueryWrapper<Role>().eq("code", code)) > 0) {
            code = base + "_" + n++;
        }
        return code;
    }

    /**
     * 权限列表（含每个权限被哪些角色持有）。
     *
     * <p>2026-10-01 第二十四轮：前端那一列「适用角色」本来读的是一个**写死的映射表**
     * （键是 {@code view_scores / manage_students / manage_all …}），而活库 {@code permissions}
     * 的 9 个码全是 {@code PERM_*} 前缀 ⇒ 两边**一个都对不上**，那一列 9 行全是空的。
     * 现在改成从 {@code role_permissions} 真查：{@code roleCodes} = 持有该权限的角色码。
     *
     * <p>字段只增不减：{@code id/code/name/description} 原样保留，
     * 2026-10-02（W5.6）补 {@code status}（此前漏回，导致小程序那列恒显示「停用」）。
     */
    @GetMapping("/permissions/list")
    public ApiResponse<List<Map<String, Object>>> getPermissions() {
        try {
            Map<Long, String> roleCodeById = roleMapper.selectList(null).stream()
                    .filter(r -> r.getId() != null)
                    .collect(Collectors.toMap(com.academic.entity.Role::getId,
                            com.academic.entity.Role::getCode, (a, b) -> a));
            Map<Long, List<String>> roleCodesByPerm = new HashMap<>();
            for (RolePermission rp : rolePermissionMapper.selectList(null)) {
                String roleCode = roleCodeById.get(rp.getRoleId());
                if (roleCode != null) {
                    roleCodesByPerm.computeIfAbsent(rp.getPermissionId(), k -> new ArrayList<>()).add(roleCode);
                }
            }
            List<Map<String, Object>> result = new ArrayList<>();
            for (com.academic.entity.Permission p : permissionMapper.selectList(null)) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", p.getId());
                item.put("code", p.getCode());
                item.put("name", p.getName());
                item.put("description", p.getDescription());
                // 2026-10-02（W5.6）：此前**没有回 status**，而小程序 admin/permissions.vue
                // 的权限点列表写的是 `p.status === 1 ? '启用' : '停用'` ⇒ 恒 undefined ≠ 1
                // ⇒ 活库 9 个权限点（status 全是 1）在页面上**全部显示「停用」**。
                // 与角色列表（/permissions/roles 一直有 status）对齐，补上这一个 key。
                item.put("status", p.getStatus());
                item.put("roleCodes", roleCodesByPerm.getOrDefault(p.getId(), List.of()));
                result.add(item);
            }
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Get permissions failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 修改一条权限的名称 / 描述（W1.16，2026-10-02）。
     *
     * <p>前端 `AdminPermissions.vue` 的「系统权限配置」表每行有一个「编辑」按钮，此前是
     * `ElMessage.info('编辑功能开发中...')` —— 一个明确的未实现桩（同文件里角色的
     * 新建/编辑/删除都在第二十四轮做掉了，这个被落下了）。
     *
     * <p><b>为什么 {@code code} 不可改</b>：它是**被别处按字面引用的标识符** ——
     * 前端角色弹窗的权限多选框 `v-model="newRole.permissions"` 存的就是权限码数组，
     * `resolvePermissions()` 也按码查表。让它可改等于给"绑定关系还在、码却变了"留一个口子。
     * 传了 `code` 就明确报 400，而不是**静默忽略**（静默忽略会让调用方以为改成功了）。
     *
     * <p>⚠️ 用 {@code UpdateWrapper.set(...)} 而不是 {@code updateById(entity)}：
     * MyBatis-Plus 默认更新策略是 NOT_NULL，**值为 null 的字段不进 UPDATE 语句** ⇒
     * "把描述清空"用 `updateById` + `setDescription(null)` 是一句空话（本工程第三十二轮
     * 刚在班级申请上踩过同一个坑）。
     */
    @PutMapping("/permissions/{permissionId}")
    public ApiResponse<Map<String, Object>> updatePermission(@PathVariable Long permissionId,
                                                             @RequestBody Map<String, Object> data) {
        try {
            com.academic.entity.Permission existing = permissionMapper.selectById(permissionId);
            if (existing == null) {
                return ApiResponse.notFound("权限不存在");
            }
            if (data == null) {
                return ApiResponse.badRequest("请求体不能为空");
            }
            if (data.containsKey("code")) {
                return ApiResponse.badRequest("权限码不可修改（前端与角色绑定都按权限码字面引用）");
            }
            if (!data.containsKey("name") && !data.containsKey("description")) {
                return ApiResponse.badRequest("没有可修改的字段（只支持 name / description）");
            }

            UpdateWrapper<com.academic.entity.Permission> update = new UpdateWrapper<>();
            update.eq("id", permissionId);

            if (data.containsKey("name")) {
                Object raw = data.get("name");
                String name = raw == null ? null : String.valueOf(raw).trim();
                if (name == null || name.isEmpty()) {
                    return ApiResponse.badRequest("权限名称不能为空");
                }
                if (name.length() > 100) {
                    return ApiResponse.badRequest("权限名称不能超过 100 个字符");
                }
                update.set("name", name);
            }
            if (data.containsKey("description")) {
                Object raw = data.get("description");
                // 空串与纯空白一律归成 null：这个字段是"有没有说明"，空串会被前端当成"有"
                String desc = raw == null ? null : String.valueOf(raw).trim();
                if (desc != null && desc.isEmpty()) {
                    desc = null;
                }
                if (desc != null && desc.length() > 500) {
                    return ApiResponse.badRequest("权限描述不能超过 500 个字符");
                }
                update.set("description", desc);
            }
            // 该列本身有 ON UPDATE CURRENT_TIMESTAMP，这里显式写一次与 admin-service 其余写路径一致
            update.set("updated_at", LocalDateTime.now());

            permissionMapper.update(null, update);

            com.academic.entity.Permission saved = permissionMapper.selectById(permissionId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", saved.getId());
            result.put("code", saved.getCode());
            result.put("name", saved.getName());
            result.put("description", saved.getDescription());
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Update permission failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/permissions/user/{userId}")
    public ApiResponse<Map<String, Object>> getUserPermissions(@PathVariable Long userId) {
        try {
            User user = userService.getById(userId);
            if (user == null) {
                // 原来是 `if (user != null) {...}` 然后照样 `success(空 Map)` ——
                // 账号不存在时回的是 **HTTP 200 + body code 200 + data:{}**，
                // 前端 `if (response)` 判空对象为真 ⇒ 照样弹出「用户权限详情」，
                // 里面每个字段都是空的。改为明确的 404。
                throw new ResourceNotFoundException("用户不存在: " + userId);
            }
            Map<String, Object> result = new HashMap<>();
            {
                result.put("userId", userId);
                result.put("username", user.getUsername());
                // 通过角色获取权限
                //
                // ⚠️ 2026-10-01 第二十四轮：这里原来把角色码拼成 "STUDENT"/"ADMIN"，
                //    而 `role_permissions` 的 JOIN 条件是 `r.code = #{roleCode}`，
                //    活库 `roles.code` 存的是 **`ROLE_STUDENT`/`ROLE_ADMIN`**（带 ROLE_ 前缀）
                //    ⇒ 两边一个字都对不上，`selectByRoleCode` 恒返回空表。
                //    实测：`/admin/permissions/user/27`（role=1）→ permissions=[]，
                //    `/admin/permissions/user/1`（role=4，库里 9 条绑定）→ permissions=[]。
                //    也就是说权限管理页的「查看权限」弹窗里「拥有权限」**永远是空的**。
                //    这是与网关那段死 RBAC 同源的缺陷（库 ROLE_* vs 代码 *）。
                Integer roleCode = user.getRole();
                String roleCodeStr = switch (roleCode != null ? roleCode : 0) {
                    case 1 -> "ROLE_STUDENT";
                    case 2 -> "ROLE_TEACHER";
                    case 3 -> "ROLE_COUNSELOR";
                    case 4 -> "ROLE_ADMIN";
                    // 5 = 学院管理员。种子 academic_warning_system.sql:1437 里**没有**给
                    // ROLE_COLLEGE_ADMIN 绑任何权限（role_permissions 只有角色 1–4 的 21 行），
                    // 所以这条查出来仍是空表 —— 那是数据本身的实情，不是查询写错。
                    case 5 -> "ROLE_COLLEGE_ADMIN";
                    default -> "UNKNOWN";
                };
                List<com.academic.entity.Permission> permissions = permissionMapper.selectByRoleCode(roleCodeStr);
                result.put("role", roleCode);
                // roleName 是**给弹窗直接显示**的（弹窗用的是 getRoleName(selectedUser.role)，
                // 但这里也不该回 "ROLE_STUDENT" 这种英文码）—— 改成中文名。
                result.put("roleName", getRoleNameText(roleCode));
                result.put("roleCode", roleCodeStr);
                // 前端「拥有权限」渲染的是 `perm` 本身（`v-for="perm in selectedUser.permissions"`），
                // 而 Permission 是对象 ⇒ 原来即使查到了也会显示成 [object Object]。
                // 回**权限码字符串数组**，并另存一份完整对象给以后要用字段的地方。
                result.put("permissions", permissions.stream().map(com.academic.entity.Permission::getCode).toList());
                result.put("permissionDetails", permissions);
            }
            return ApiResponse.success(result);
        } catch (ResourceNotFoundException e) {
            // 别被下面那个 catch 压成 body code 500，交给 GlobalExceptionHandler 翻成真 404。
            throw e;
        } catch (Exception e) {
            log.error("Get user permissions failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 给账号改角色。请求体：{@code {userId, role}}（{@code role} 是 {@code users.role} 的数值）。
     *
     * <p>2026-10-01 第二十四轮：**契约本来是 {@code userId/role}，而唯一的调用方
     * {@code AdminPermissions.vue} 一直发的是 {@code {user_id, role_id}}** ⇒
     * {@code data.get("userId")} 是 null，`.toString()` 抛 NPE，被下面的 catch 吞成
     * {@code ApiResponse.error(...)}（HTTP 200 + body code 500）。前端拦截器认
     * body.code！=200 就 reject，所以那一格「当前角色」下拉**从来就没保存成功过**，
     * 用户看到的是「更新角色失败」。
     *
     * <p>修的是调用方（改发 {@code userId/role}），契约保持单一；这里顺带把"缺字段"
     * 变成明确的 400 而不是把 NPE 的原话抛给前端。
     */
    @PostMapping("/permissions/assign-role")
    public ApiResponse<String> assignRole(@RequestBody Map<String, Object> data) {
        try {
            Object rawUserId = data == null ? null : data.get("userId");
            Object rawRole = data == null ? null : data.get("role");
            if (rawUserId == null) {
                throw new IllegalArgumentException("缺少 userId");
            }
            if (rawRole == null) {
                throw new IllegalArgumentException("缺少 role");
            }
            Long userId = Long.valueOf(rawUserId.toString());
            Integer role = Integer.valueOf(rawRole.toString());
            if (role < 1 || role > 5) {
                throw new IllegalArgumentException("非法的角色值: " + role);
            }
            User user = userService.getById(userId);
            if (user == null) {
                // 原来是 `return ApiResponse.notFound("用户不存在")` —— 它回的是
                // **HTTP 200 + body code 404**（`ApiResponse.notFound` 只是设了 body 里的 code）。
                // 同一个方法里"参数错"已经走 400 了，"找不到"却给 200，两套口径。
                // 抛出去由 GlobalExceptionHandler 翻成真正的 404（HTTP + body 都是 404）。
                throw new ResourceNotFoundException("用户不存在: " + userId);
            }
            user.setRole(role);
            userService.updateById(user);
            return ApiResponse.success("角色分配成功");
        } catch (IllegalArgumentException | ResourceNotFoundException e) {
            // 参数错 / 找不到，都不该被下面那个 catch 压成 body code 500：
            // 抛出去由 GlobalExceptionHandler 翻成 HTTP 400 / 404（body 的 code 也对得上）。
            throw e;
        } catch (Exception e) {
            log.error("Assign role failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    // ==================== P0新增：班级管理remove-teacher ====================
    @PostMapping("/class-management/remove-teacher/{classId}")
    public ApiResponse<String> removeTeacherFromClass(@PathVariable Long classId) {
        try {
            Classes clazz = classesMapper.selectById(classId);
            if (clazz == null) return ApiResponse.notFound("班级不存在");
            clazz.setTeacherId(null);
            classesMapper.updateById(clazz);
            log.info("Removed teacher from class: {}", classId);
            return ApiResponse.success("教师已从班级移除");
        } catch (Exception e) {
            log.error("Remove teacher from class failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    // ==================== P0新增：学院下属专业 ====================
    @GetMapping("/colleges/{id}/majors")
    public ApiResponse<List<Major>> getMajorsByCollege(@PathVariable Long id) {
        try {
            QueryWrapper<Major> query = new QueryWrapper<>();
            query.eq("college_id", id);
            return ApiResponse.success(majorService.list(query));
        } catch (Exception e) {
            log.error("Get majors by college failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    // ==================== 用户密码管理 ====================
    // 密码查看接口已移除（安全原因：不应暴露密码哈希）
    // 密码重置通过 /users/{id}/reset-password 接口，新密码仅通过安全渠道告知

    // ==================== 管理员建号 ====================
    // ⚠️ 2026-10-01 删掉了 `POST /admin/register`。它当初的注释写着"转发到 auth-service",
    //    实际是**自己 new 一个 User 存库**、`userService.save(user)` 直写 users 表:
    //      - 全仓**零调用方**(前端 `adminAPI.register` 这个封装都没人用);
    //      - 绕过了注册路径的重复名校验与事务;
    //      - 和 `/users/add`、`/auth/register/admin` 构成"同一件事三种做法"。
    //    管理员建号的唯一活路径是前端 AdminUsers.vue → `POST /users/add`
    //    (user-service `UserController.addUser`, 已加管理员闸)。

    private GeneratedReport generateReportWithCustomTemplate(String templateId, String startDate, String endDate) throws Exception {
        ReportTemplate template = reportTemplateService.getTemplateById(Long.parseLong(templateId));
        if (template == null) {
            throw new RuntimeException("模板不存在");
        }

        String fileName = "report_custom_" + templateId + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx";
        // 同 generateReportFile：原先是写死的 "exports/" 且从不 mkdirs（首次必 500）。
        String filePath = exportDir + "/" + fileName;
        File exportDirFile = new File(exportDir);
        if (!exportDirFile.exists()) {
            exportDirFile.mkdirs();
        }

        File templateFile = new File(template.getFilePath());
        if (!templateFile.exists()) {
            throw new RuntimeException("模板文件不存在");
        }

        int writtenRows;
        String matchedReportId;
        try (Workbook workbook = WorkbookFactory.create(templateFile)) {
            Sheet sheet = workbook.getSheetAt(0);

            // 2026-10-01 第二十四轮：这里原先把三格字面量 "数据行1"/"数据行2"/"数据行3"
            // 追加到用户上传的模板后面 —— 就是把假数据写进一份看起来像真报表的文件里，
            // 与模板 1-4 那套硬编假数据是同一个病。现在改成按**表头列名**匹配内置报表取数：
            // 表头与某张内置报表逐字一致时填真实行，认不出来就只输出表头（模板原样），
            // 绝不编行。用户上传的是一份"表格样式"，本系统没有任何"任意模板 → 数据源"
            // 的绑定关系，凭空造行只会让下载下来的人以为那是查询结果。
            List<String> templateHeaders = readHeaderRow(sheet.getRow(0));
            // W23：先问"表头认出了哪张内置报表"，再取数 —— 这两步分开，是为了能把
            // "表头没认出来"（matched=false）与"认出报表 2 了但它当前没有数据行"
            // （matched=true, rows=0）分开回给前端。此前 `matchBuiltinReportRows` 两种情况
            // 都只返回一个空 List，调用方无从区分，只能笼统 log 一句。
            matchedReportId = matchBuiltinReportId(templateHeaders);
            List<List<String>> data = matchedReportId == null
                    ? new ArrayList<>() : getReportData(matchedReportId);
            int startRow = sheet.getLastRowNum() + 1;
            for (int i = 0; i < data.size(); i++) {
                Row row = sheet.createRow(startRow + i);
                List<String> rowData = data.get(i);
                for (int j = 0; j < rowData.size(); j++) {
                    row.createCell(j).setCellValue(rowData.get(j));
                }
            }
            writtenRows = data.size();
            if (matchedReportId == null) {
                log.warn("Custom report template {} headers matched no built-in report, output headers only: {}",
                        templateId, templateHeaders);
            } else if (writtenRows == 0) {
                log.warn("Custom report template {} matched built-in report {}, which currently has no data rows",
                        templateId, matchedReportId);
            }

            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                workbook.write(fos);
            }
        }

        log.info("Report generated with custom template: {} ({} rows, matched={})",
                fileName, writtenRows, matchedReportId);
        return new GeneratedReport(fileName, writtenRows, matchedReportId != null);
    }

    /** 取一行里的字符串单元格，用于自定义模板的表头匹配。null 单元格按空串处理。*/
    private static List<String> readHeaderRow(Row headerRow) {
        List<String> cells = new ArrayList<>();
        if (headerRow == null) {
            return cells;
        }
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            cells.add(cell == null ? "" : new DataFormatter().formatCellValue(cell).trim());
        }
        return cells;
    }

    /**
     * 自定义模板的表头若能**逐字**匹配上某张内置报表的表头，返回那张报表的 id；匹配不上返回 null。
     *
     * <p>刻意用"完全相等"而不是"列名包含"：宽松匹配会把「学号」和「学生ID」这类
     * 语义不同的列凑到一张表里，产出一份列义对不上的报表 —— 那比不给数据更糟。
     *
     * <p><b>W23</b>：原来这个方法叫 {@code matchBuiltinReportRows}、直接返回数据行，
     * "没匹配上"与"匹配上了但那报表没有数据"因此都是**一个空 List**，调用方分不开。
     * 改成只回答"是哪一张报表"，取数交给调用方 —— 它才有机会把这两种情况分别回给用户。
     */
    private String matchBuiltinReportId(List<String> templateHeaders) {
        if (templateHeaders.isEmpty()) {
            return null;
        }
        for (String templateId : new String[]{"1", "2", "3", "4"}) {
            if (Arrays.asList(getReportHeaders(templateId)).equals(templateHeaders)) {
                return templateId;
            }
        }
        return null;
    }

    /** 宽松地把请求体里的值转成 Integer；无法识别返回 null（用于申诉审核 status）。 */
    private static Integer toInt(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return (int) Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 宽松地把请求体里的值转成 Long；无法识别返回 null。 */
    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return (long) Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
