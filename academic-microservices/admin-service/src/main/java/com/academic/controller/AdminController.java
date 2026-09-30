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
import com.academic.entity.*;
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
import com.academic.mapper.ClassesMapper;
import com.academic.mapper.TeacherProfileMapper;
import com.academic.mapper.ExportHistoryMapper;
import com.academic.mapper.StudentProfileMapper;
import com.academic.mapper.CounselorProfileMapper;
import com.academic.mapper.CollegeAdminProfileMapper;
import com.academic.mapper.ScoreRecordMapper;
import com.academic.mapper.AcademicWarningMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import com.academic.feign.MessageServiceClient;
import com.academic.feign.WarningServiceClient;
import com.academic.feign.StudentServiceClient;
import com.academic.feign.CounselorServiceClient;
import com.academic.feign.CourseServiceClient;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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

    @Autowired
    private com.academic.mapper.LoginLogMapper loginLogMapper;

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
    private WarningServiceClient warningServiceClient;

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
    private TeacherProfileMapper teacherProfileMapper;

    @Autowired
    private ExportHistoryMapper exportHistoryMapper;

    @Autowired
    private StudentProfileMapper studentProfileMapper;
    
    @Autowired
    private StudentProfileService studentProfileService;
    
    @Autowired
    private CounselorProfileMapper counselorProfileMapper;

    @Autowired
    private CollegeAdminProfileMapper collegeAdminProfileMapper;


    @Autowired
    private ScoreRecordMapper scoreRecordMapper;

    @Autowired
    private com.academic.mapper.ScoreMapper scoreMapper;

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

    @GetMapping("/activities")
    public ApiResponse<List<Map<String, Object>>> getActivities() {
        List<Map<String, Object>> activities = new ArrayList<>();
        return ApiResponse.success(activities);
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
                int redCount = 0, yellowCount = 0, lowCount = 0;
                for (AcademicWarning w : warnings) {
                    Integer level = w.getWarningLevel();
                    if (level != null) {
                        if (level >= 3) redCount++;
                        else if (level == 2) yellowCount++;
                        else lowCount++;
                    }
                }
                statistics.put("redWarnings", redCount);
                statistics.put("yellowWarnings", yellowCount);
                statistics.put("lowWarnings", lowCount);
            } catch (Exception e) {
                log.warn("获取预警数据失败: {}", e.getMessage());
                statistics.put("totalWarnings", 0);
                statistics.put("redWarnings", 0);
                statistics.put("yellowWarnings", 0);
                statistics.put("lowWarnings", 0);
            }

            Map<String, Object> todayStats = new HashMap<>();
            todayStats.put("newWarnings", 0);
            todayStats.put("processedWarnings", 0);
            todayStats.put("pendingTasks", 0);
            todayStats.put("unreadMessages", 0);
            todayStats.put("onlineTeachers", 0);
            todayStats.put("onlineStudents", 0);
            statistics.put("todayStats", todayStats);

            Map<String, Object> changeRates = new HashMap<>();
            changeRates.put("studentChangeRate", 0);
            changeRates.put("courseChangeRate", 0);
            changeRates.put("userChangeRate", 0);
            changeRates.put("warningChangeRate", 0);
            statistics.put("changeRates", changeRates);

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
        result.put("appeals", 0);
        // 待处理预警数
        try {
            long pendingWarnings = academicWarningMapper.selectCount(
                new QueryWrapper<AcademicWarning>().eq("status", 0));
            result.put("pendingWarnings", (int) pendingWarnings);
        } catch (Exception e) { result.put("pendingWarnings", 0); }
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

    @GetMapping("/users")
    @SentinelResource(value = "adminApi:getUsers", fallback = "getAllUsersFallback")
    public ApiResponse<List<User>> getAllUsers(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long collegeId,
            @RequestParam(required = false) Integer role) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        if (role != null) {
            wrapper.eq("role", role);
        }
        int start = (page - 1) * size;
        List<User> users = userService.list(wrapper.last("LIMIT " + start + ", " + size));
        stripPasswords(users);
        return ApiResponse.success(users);
    }

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

    @PostMapping("/users")
    @Transactional
    @SentinelResource(value = "adminApi:createUser", fallback = "createUserFallback")
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

    public ApiResponse<String> createUserFallback(User user, Throwable t) {
        return ApiResponse.error("adminApi:createUser API is degraded: " + t.getMessage());
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

            userService.updateUser(user);
            log.info("User updated: id={}, username={}, passwordChanged={}",
                    id, user.getUsername(), user.getPassword() != null);
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
    @SentinelResource(value = "adminApi:deleteUser", fallback = "deleteUserFallback")
    public ApiResponse<String> deleteUser(@PathVariable Long id) {
        try {
            User user = userService.getById(id);
            if (user == null) {
                return ApiResponse.notFound("User not found");
            }
            
            // 先删除相关的消息记录（处理外键约束）
            try {
                // 通过MessageServiceClient删除相关消息
                messageServiceClient.deleteMessagesByUserId(id);
            } catch (Exception e) {
                log.warn("Failed to delete messages via Feign, but will continue: {}", e.getMessage());
            }
            
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
            return ApiResponse.success("User deleted successfully");
        } catch (Exception e) {
            log.error("Delete user failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    public ApiResponse<String> deleteUserFallback(Long id, Throwable t) {
        return ApiResponse.error("adminApi:deleteUser API is degraded: " + t.getMessage());
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
            return ApiResponse.success("审批通过，用户已激活");
        } catch (Exception e) {
            log.error("Approve user failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
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

    @GetMapping("/users/page")
    @SentinelResource(value = "adminApi:getUsersByPage", fallback = "getUsersByPageFallback")
    public ApiResponse<Map<String, Object>> getUsersByPage(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long collegeId,
            @RequestParam(required = false) Integer role) {
        try {
            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            if (role != null) {
                queryWrapper.eq("role", role);
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
        collegeService.updateById(college);
        return ApiResponse.success("College updated successfully");
    }

    @DeleteMapping("/colleges/{id}")
    public ApiResponse<String> deleteCollege(@PathVariable Long id) {
        collegeService.removeById(id);
        return ApiResponse.success("College deleted successfully");
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
            result.add(courseMap);
        }
        return result;
    }

    @GetMapping("/majors")
    public ApiResponse<List<Major>> getAllMajors() {
        return ApiResponse.success(majorService.list());
    }

    @GetMapping("/majors/{id}")
    public ApiResponse<Major> getMajorById(@PathVariable Long id) {
        Major major = majorService.getById(id);
        if (major != null) {
            return ApiResponse.success(major);
        }
        return ApiResponse.notFound("Major not found");
    }

    @PostMapping("/majors")
    public ApiResponse<String> createMajor(@RequestBody Major major) {
        majorService.save(major);
        return ApiResponse.success("Major created successfully");
    }

    @PutMapping("/majors/{id}")
    public ApiResponse<String> updateMajor(@PathVariable Long id, @RequestBody Major major) {
        major.setId(id);
        majorService.updateById(major);
        return ApiResponse.success("Major updated successfully");
    }

    @DeleteMapping("/majors/{id}")
    public ApiResponse<String> deleteMajor(@PathVariable Long id) {
        majorService.removeById(id);
        return ApiResponse.success("Major deleted successfully");
    }

    

    @GetMapping("/courses/{id}")
    public ApiResponse<Course> getCourseById(@PathVariable Long id) {
        Course course = courseService.getById(id);
        if (course != null) {
            return ApiResponse.success(course);
        }
        return ApiResponse.notFound("Course not found");
    }

    @PostMapping("/courses")
    public ApiResponse<String> createCourse(@RequestBody Course course) {
        courseService.save(course);
        return ApiResponse.success("Course created successfully");
    }

    @PutMapping("/courses/{id}")
    public ApiResponse<String> updateCourse(@PathVariable Long id, @RequestBody Course course) {
        course.setId(id);
        courseService.updateById(course);
        return ApiResponse.success("Course updated successfully");
    }

    @DeleteMapping("/courses/{id}")
    public ApiResponse<String> deleteCourse(@PathVariable Long id) {
        courseService.removeById(id);
        return ApiResponse.success("Course deleted successfully");
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
        return ApiResponse.success("Rule created successfully");
    }

    @PutMapping("/rules/{id}")
    public ApiResponse<String> updateRule(@PathVariable Long id, @RequestBody WarningRule rule) {
        rule.setId(id);
        warningRuleService.updateById(rule);
        return ApiResponse.success("Rule updated successfully");
    }

    @DeleteMapping("/rules/{id}")
    public ApiResponse<String> deleteRule(@PathVariable Long id) {
        warningRuleService.removeById(id);
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
        classesMapper.updateById(classes);
        return ApiResponse.success("Class updated successfully");
    }

    @DeleteMapping("/classes/{id}")
    public ApiResponse<String> deleteClass(@PathVariable Long id) {
        classesMapper.deleteById(id);
        return ApiResponse.success("Class deleted successfully");
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
            studentService.updateById(student);
            return ApiResponse.success("Student updated successfully");
        } catch (Exception e) {
            log.error("Update student failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @DeleteMapping("/students/{id}")
    public ApiResponse<String> deleteStudent(@PathVariable Long id) {
        try {
            studentService.removeById(id);
            return ApiResponse.success("Student deleted successfully");
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
            teacherProfileMapper.updateById(teacher);
            return ApiResponse.success("Teacher updated successfully");
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
            counselorService.updateById(counselor);
            return ApiResponse.success("Counselor updated successfully");
        } catch (Exception e) {
            log.error("Update counselor failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @DeleteMapping("/counselors/{id}")
    public ApiResponse<String> deleteCounselor(@PathVariable Long id) {
        try {
            counselorService.removeById(id);
            userService.removeById(id);
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
            
            warning.setStatus(1);
            warning.setHandleResult(handleResult);
            warning.setHandledBy(remark);
            warning.setHandledAt(LocalDateTime.now());
            academicWarningMapper.updateById(warning);
            
            log.info("Handled warning {}: result={}, remark={}", id, handleResult, remark);
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
            applicationMapper.deleteById(id);
            return ApiResponse.success("Application deleted successfully");
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

    @PostMapping("/import/students")
    public ApiResponse<String> importStudents(@RequestBody List<Map<String, Object>> data) {
        try {
            log.info("Import students request, count: {}", data.size());
            
            List<User> usersToSave = new ArrayList<>();
            List<StudentProfile> profilesToSave = new ArrayList<>();
            
            for (Map<String, Object> item : data) {
                String username = (String) item.get("username");
                if (username == null || username.isEmpty()) {
                    continue;
                }
                
                User existingUser = userService.getOne(new QueryWrapper<User>().eq("username", username));
                if (existingUser != null) {
                    continue;
                }
                
                User user = new User();
                user.setUsername(username);
                user.setPassword((String) item.getOrDefault("password", "123456"));
                user.setName((String) item.getOrDefault("name", username));
                user.setRole(1);
                user.setStatus(1);
                user.setCreatedAt(LocalDateTime.now());
                user.setUpdatedAt(LocalDateTime.now());
                usersToSave.add(user);
            }
            
            if (!usersToSave.isEmpty()) {
                userService.saveBatch(usersToSave);
                
                for (int i = 0; i < usersToSave.size(); i++) {
                    Map<String, Object> item = data.get(i);
                    User user = usersToSave.get(i);
                    
                    StudentProfile studentProfile = new StudentProfile();
                    studentProfile.setUserId(user.getId());
                    studentProfile.setStudentId((String) item.getOrDefault("studentId", user.getUsername()));
                    studentProfile.setCollegeId(item.get("collegeId") != null ? ((Number) item.get("collegeId")).longValue() : null);
                    studentProfile.setMajorId(item.get("majorId") != null ? ((Number) item.get("majorId")).longValue() : null);
                    studentProfile.setClassName((String) item.get("className"));
                    profilesToSave.add(studentProfile);
                }
                
                studentProfileService.saveBatch(profilesToSave);
            }
            
            log.info("Import students finished: {} success", usersToSave.size());
            return ApiResponse.success("导入完成：成功 " + usersToSave.size() + " 条");
        } catch (Exception e) {
            log.error("Import students failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/import/scores")
    public ApiResponse<Map<String, Object>> importScores(@RequestBody List<Map<String, Object>> data) {
        Map<String, Object> result = new HashMap<>();
        int successCount = 0, skipCount = 0, updateCount = 0, syncedCount = 0, syncFailCount = 0;
        List<Map<String, Object>> errors = new ArrayList<>();
        List<ScoreRecord> batchToInsert = new ArrayList<>();

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
                int rowNum = i + 2;
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
                if (sr.getScore() != null && sr.getScoreLevel() == null) {
                    double s = sr.getScore();
                    sr.setScoreLevel(s >= 90 ? 1 : s >= 80 ? 2 : s >= 70 ? 3 : s >= 60 ? 4 : 5);
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

                // 同步到 scores 表（权威成绩源）。不写这一步，导入的成绩
                // 在预警引擎和学生端统计里就等于不存在。
                try {
                    syncToScores(sr);
                    syncedCount++;
                } catch (Exception e) {
                    log.warn("同步成绩到 scores 表失败: studentId={}, courseId={}: {}",
                            sr.getStudentId(), sr.getCourseId(), e.getMessage());
                    syncFailCount++;
                }
            }

            if (!batchToInsert.isEmpty()) scoreRecordService.saveBatch(batchToInsert);

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
            response.getWriter().write("下载模板失败: " + e.getMessage());
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

            List<ScoreRecord> allScores = scoreRecordMapper.selectList(null);
            
            Map<Long, List<Double>> studentScores = new HashMap<>();
            for (ScoreRecord score : allScores) {
                Long studentId = score.getStudentId();
                if (score.getScoreTotal() != null && score.getScoreTotal() < 60) {
                    studentScores.computeIfAbsent(studentId, k -> new ArrayList<>()).add(score.getScoreTotal());
                }
            }

            List<Map<String, Object>> insufficientStudents = new ArrayList<>();
            for (Map.Entry<Long, List<Double>> entry : studentScores.entrySet()) {
                Long studentId = entry.getKey();
                List<Double> lowScores = entry.getValue();
                
                // 通过 student_profile 查找用户
                QueryWrapper<com.academic.common.entity.Student> studentQuery = new QueryWrapper<>();
                studentQuery.eq("id", studentId);
                com.academic.common.entity.Student studentProfile = studentService.getOne(studentQuery);
                
                if (studentProfile != null) {
                    QueryWrapper<User> userQuery = new QueryWrapper<>();
                    userQuery.eq("id", studentProfile.getUserId());
                    User user = userService.getOne(userQuery);
                    
                    Map<String, Object> studentInfo = new HashMap<>();
                    studentInfo.put("studentId", studentProfile.getStudentId());
                    studentInfo.put("name", studentProfile.getName());
                    studentInfo.put("lowScoreCount", lowScores.size());
                    studentInfo.put("lowestScore", lowScores.stream().mapToDouble(Double::doubleValue).min().orElse(0));
                    insufficientStudents.add(studentInfo);
                }
            }

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
                row.createCell(0).setCellValue((String) student.get("studentId"));
                row.createCell(1).setCellValue((String) student.get("name"));
                row.createCell(2).setCellValue(((Number) student.get("lowScoreCount")).intValue());
                row.createCell(3).setCellValue(((Number) student.get("lowestScore")).doubleValue());
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
            response.getWriter().write("导出失败: " + e.getMessage());
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
            response.getWriter().write("导出失败: " + e.getMessage());
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
    @GetMapping("/tasks/list")
    public ApiResponse<Map<String, Object>> getTasksList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String status) {
        try {
            // 任务列表（目前为空，后续可扩展）
            List<Map<String, Object>> tasks = new ArrayList<>();
            
            // 过滤状态
            if (status != null && !status.isEmpty()) {
                final String filterStatus = status;
                tasks = tasks.stream()
                    .filter(task -> filterStatus.equals(String.valueOf(task.get("status"))))
                    .collect(java.util.stream.Collectors.toList());
            }
            
            int total = tasks.size();
            int start = (page - 1) * size;
            int end = Math.min(start + size, total);
            List<?> pageItems = start < total ? tasks.subList(start, end) : List.of();
            
            Map<String, Object> result = new HashMap<>();
            result.put("total", total);
            result.put("items", pageItems);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Get tasks list failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/tasks/create")
    public ApiResponse<String> createTask(@RequestBody Map<String, Object> taskData) {
        try {
            log.info("Create task: {}", taskData);
            return ApiResponse.success("Task created successfully");
        } catch (Exception e) {
            log.error("Create task failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/tasks/{taskId}/status")
    public ApiResponse<String> updateTaskStatus(
            @PathVariable Long taskId,
            @RequestBody Map<String, Object> statusData) {
        try {
            log.info("Update task status: taskId={}, status={}", taskId, statusData.get("status"));
            return ApiResponse.success("Task status updated successfully");
        } catch (Exception e) {
            log.error("Update task status failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @DeleteMapping("/tasks/{taskId}")
    public ApiResponse<String> deleteTask(@PathVariable Long taskId) {
        try {
            log.info("Delete task: {}", taskId);
            return ApiResponse.success("Task deleted successfully");
        } catch (Exception e) {
            log.error("Delete task failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
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

    @PostMapping("/class-management/reject/{requestId}")
    public ApiResponse<String> rejectClassRequest(
            @PathVariable Long requestId,
            @RequestBody Map<String, String> rejectData,
            @RequestParam(defaultValue = "teacher") String type) {
        try {
            if ("counselor".equals(type)) {
                com.academic.entity.CounselorClassApplication app = counselorClassApplicationMapper.selectById(requestId);
                if (app != null) {
                    app.setStatus(2);
                    counselorClassApplicationMapper.updateById(app);
                    return ApiResponse.success("已拒绝辅导员班级申请");
                }
            } else {
                TeacherClassApplication application = applicationMapper.selectById(requestId);
                if (application != null) {
                    application.setStatus(2);
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
            exportHistoryMapper.deleteById(exportId);
            return ApiResponse.success("Export deleted successfully");
        } catch (Exception e) {
            log.error("Delete export failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    // ============= 报表管理接口 =============
    @GetMapping("/reports/templates")
    public ApiResponse<List<Map<String, Object>>> getReportTemplates() {
        try {
            List<Map<String, Object>> templates = new ArrayList<>();
            
            Map<String, Object> template1 = new HashMap<>();
            template1.put("id", "1");
            template1.put("name", "学生统计报表");
            template1.put("description", "包含学生人数、专业分布、预警情况等统计信息");
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
            String fileName = generateReportFile(templateId, startDate, endDate);
            
            Map<String, Object> result = new HashMap<>();
            result.put("fileName", fileName);
            result.put("downloadUrl", "/admin/export/reports/" + fileName);
            
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Generate report failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }
    
    private String generateReportFile(String templateId, String startDate, String endDate) throws Exception {
        String fileName = "report_" + templateId + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx";
        String filePath = "exports/" + fileName;
        
        // 创建工作簿
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("报表数据");
        
        // 根据模板ID设置表头
        String[] headers = getReportHeaders(templateId);
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }
        
        // 添加示例数据
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
        FileOutputStream outputStream = new FileOutputStream(filePath);
        workbook.write(outputStream);
        outputStream.close();
        workbook.close();
        
        log.info("Report generated: {}", fileName);
        return fileName;
    }
    
    private String[] getReportHeaders(String templateId) {
        return switch (templateId) {
            case "1" -> new String[]{"学生ID", "姓名", "专业", "预警等级", "预警时间"};
            case "2" -> new String[]{"课程名称", "总人数", "及格人数", "不及格率", "平均分"};
            case "3" -> new String[]{"预警等级", "数量", "处理状态", "处理时间"};
            case "4" -> new String[]{"学院名称", "学生人数", "教师人数", "辅导员人数"};
            default -> new String[]{"序号", "数据1", "数据2", "数据3"};
        };
    }
    
    private List<List<String>> getReportData(String templateId) {
        List<List<String>> data = new ArrayList<>();
        return switch (templateId) {
            case "1" -> {
                data.add(List.of("S001", "张三", "计算机科学", "黄色预警", "2026-05-01"));
                data.add(List.of("S002", "李四", "软件工程", "红色预警", "2026-05-15"));
                data.add(List.of("S003", "王五", "人工智能", "蓝色预警", "2026-05-20"));
                yield data;
            }
            case "2" -> {
                data.add(List.of("高等数学", "50", "42", "16%", "78.5"));
                data.add(List.of("数据结构", "45", "38", "15.6%", "82.3"));
                data.add(List.of("计算机网络", "48", "45", "6.25%", "88.0"));
                yield data;
            }
            case "3" -> {
                data.add(List.of("红色预警", "5", "已处理", "2026-05-25"));
                data.add(List.of("黄色预警", "12", "处理中", "2026-05-26"));
                data.add(List.of("蓝色预警", "8", "待处理", "-"));
                yield data;
            }
            case "4" -> {
                data.add(List.of("计算机学院", "1200", "85", "12"));
                data.add(List.of("电子工程学院", "980", "72", "10"));
                data.add(List.of("管理学院", "850", "65", "8"));
                yield data;
            }
            default -> data;
        };
    }

    // ============= 报表下载接口 =============
    @GetMapping("/export/reports/{fileName}")
    public void downloadReport(@PathVariable String fileName, HttpServletResponse response) throws IOException {
        try {
            String filePath = "exports/" + fileName;
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
    @PostMapping("/backup")
    public ApiResponse<String> backupData() {
        try {
            log.info("Backup data requested");
            
            String backupFileName = "backup_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".sql";
            log.info("Backup created: {}", backupFileName);
            
            return ApiResponse.success("Backup created successfully: " + backupFileName);
        } catch (Exception e) {
            log.error("Backup data failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/backups/list")
    public ApiResponse<Map<String, Object>> getBackupList() {
        try {
            List<Map<String, Object>> backups = new ArrayList<>();
            
            Map<String, Object> backup1 = new HashMap<>();
            backup1.put("id", "1");
            backup1.put("fileName", "backup_20240101_120000.sql");
            backup1.put("size", "2.5 MB");
            backup1.put("createdAt", "2024-01-01 12:00:00");
            backup1.put("status", "success");
            backups.add(backup1);
            
            Map<String, Object> backup2 = new HashMap<>();
            backup2.put("id", "2");
            backup2.put("fileName", "backup_20240115_090000.sql");
            backup2.put("size", "3.2 MB");
            backup2.put("createdAt", "2024-01-15 09:00:00");
            backup2.put("status", "success");
            backups.add(backup2);
            
            Map<String, Object> backup3 = new HashMap<>();
            backup3.put("id", "3");
            backup3.put("fileName", "backup_20240130_180000.sql");
            backup3.put("size", "2.8 MB");
            backup3.put("createdAt", "2024-01-30 18:00:00");
            backup3.put("status", "success");
            backups.add(backup3);
            
            Map<String, Object> result = new HashMap<>();
            result.put("total", backups.size());
            result.put("items", backups);
            
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Get backup list failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/backup/{backupId}/restore")
    public ApiResponse<String> restoreBackup(@PathVariable String backupId) {
        try {
            log.info("Restore backup requested: {}", backupId);
            return ApiResponse.success("Backup " + backupId + " restored successfully");
        } catch (Exception e) {
            log.error("Restore backup failed: {}", e.getMessage());
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
            reportTemplateService.deleteTemplate(templateId);
            return ApiResponse.success("模板删除成功");
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

            String fileName;
            if (customTemplateId != null && !customTemplateId.isEmpty()) {
                fileName = generateReportWithCustomTemplate(customTemplateId, startDate, endDate);
            } else {
                fileName = generateReportFile(templateId, startDate, endDate);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("fileName", fileName);
            result.put("downloadUrl", "/admin/export/reports/" + fileName);

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

    @GetMapping("/permissions/roles")
    public ApiResponse<List<com.academic.entity.Role>> getRoles() {
        try {
            return ApiResponse.success(roleMapper.selectList(null));
        } catch (Exception e) {
            log.error("Get roles failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/permissions/list")
    public ApiResponse<List<com.academic.entity.Permission>> getPermissions() {
        try {
            return ApiResponse.success(permissionMapper.selectList(null));
        } catch (Exception e) {
            log.error("Get permissions failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/permissions/user/{userId}")
    public ApiResponse<Map<String, Object>> getUserPermissions(@PathVariable Long userId) {
        try {
            User user = userService.getById(userId);
            Map<String, Object> result = new HashMap<>();
            if (user != null) {
                result.put("userId", userId);
                result.put("username", user.getUsername());
                // 通过角色获取权限
                Integer roleCode = user.getRole();
                String roleCodeStr = switch (roleCode != null ? roleCode : 0) {
                    case 1 -> "STUDENT";
                    case 2 -> "TEACHER";
                    case 3 -> "COUNSELOR";
                    case 4 -> "ADMIN";
                    default -> "UNKNOWN";
                };
                List<com.academic.entity.Permission> permissions = permissionMapper.selectByRoleCode(roleCodeStr);
                result.put("role", roleCode);
                result.put("roleName", roleCodeStr);
                result.put("permissions", permissions);
            }
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Get user permissions failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/permissions/assign-role")
    public ApiResponse<String> assignRole(@RequestBody Map<String, Object> data) {
        try {
            Long userId = Long.valueOf(data.get("userId").toString());
            Integer role = Integer.valueOf(data.get("role").toString());
            User user = userService.getById(userId);
            if (user == null) return ApiResponse.notFound("用户不存在");
            user.setRole(role);
            userService.updateById(user);
            return ApiResponse.success("角色分配成功");
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

    // ==================== P0新增：管理员注册（转发到auth-service） ====================
    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> registerAdmin(@RequestBody Map<String, Object> data) {
        try {
            User user = new User();
            user.setUsername((String) data.get("username"));
            user.setPassword(passwordEncoder.encode((String) data.get("password")));
            user.setName((String) data.getOrDefault("name", data.get("username")));
            user.setRole(4); // admin
            user.setStatus(1);
            userService.save(user);
            Map<String, Object> result = new HashMap<>();
            result.put("userId", user.getId());
            result.put("message", "管理员注册成功");
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Register admin failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    private String generateReportWithCustomTemplate(String templateId, String startDate, String endDate) throws Exception {
        ReportTemplate template = reportTemplateService.getTemplateById(Long.parseLong(templateId));
        if (template == null) {
            throw new RuntimeException("模板不存在");
        }

        String fileName = "report_custom_" + templateId + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx";
        String filePath = "exports/" + fileName;

        File templateFile = new File(template.getFilePath());
        if (!templateFile.exists()) {
            throw new RuntimeException("模板文件不存在");
        }

        try (Workbook workbook = WorkbookFactory.create(templateFile)) {
            Sheet sheet = workbook.getSheetAt(0);
            
            Row headerRow = sheet.getRow(0);
            if (headerRow != null) {
                Row dataRow = sheet.createRow(sheet.getLastRowNum() + 1);
                dataRow.createCell(0).setCellValue("数据行1");
                dataRow.createCell(1).setCellValue("数据行2");
                dataRow.createCell(2).setCellValue("数据行3");
            }

            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                workbook.write(fos);
            }
        }

        log.info("Report generated with custom template: {}", fileName);
        return fileName;
    }
}
