package com.academic.controller;

import com.academic.common.constant.ScoreCaliber;
import com.academic.common.constant.WarningStatus;
import com.academic.common.dto.ApiResponse;
import com.academic.common.util.ExcelUploadErrors;
import com.academic.common.util.ScoreSyncSupport;
import com.academic.common.entity.TeacherProfile;
import com.academic.common.entity.Course;
import com.academic.common.entity.CourseRecommendation;
import com.academic.common.entity.TeacherCourse;
import com.academic.entity.ClassInfo;
import com.academic.common.entity.ScoreRecord;
import com.academic.service.TeacherService;
import com.academic.service.CourseService;
import com.academic.service.TeacherCourseService;
import com.academic.service.ClassInfoService;
import com.academic.service.TeacherClassApplicationService;
import com.academic.service.ScoreRecordService;
import com.academic.service.FeedbackService;
import com.academic.service.CommunicationRecordService;
import com.academic.service.ScoreAuditLogService;
import com.academic.common.entity.ScoreAuditLog;
import com.academic.entity.Feedback;
import com.academic.entity.TeacherClassApplication;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 教师控制器
 */
@Slf4j
@RestController
@RequestMapping("/teachers")
public class TeacherController {

    @Autowired
    private TeacherService teacherService;

    @Autowired
    private CourseService courseService;

    @Autowired
    private TeacherCourseService teacherCourseService;

    @Autowired
    private ClassInfoService classInfoService;

    @Autowired
    private TeacherClassApplicationService applicationService;

    @Autowired
    private ScoreRecordService scoreRecordService;

    @Autowired
    private FeedbackService feedbackService;

    @Autowired
    private CommunicationRecordService communicationRecordService;

    @Autowired
    private ScoreAuditLogService scoreAuditLogService;

    @Autowired
    private com.academic.mapper.AcademicWarningMapper academicWarningMapper;

    // 教师处理预警后通知学生。共用实现（id 空间转换 + 偏好闸 + 落库），见 academic-common。
    @Autowired
    private com.academic.common.notify.StudentNotifier studentNotifier;

    @Autowired
    private com.academic.mapper.ScoreRecordMapper scoreRecordMapper;

    // 导入学生用**mapper 直连**，不走 ClassInfoService：那个 service 的方法清一色
    // `catch (Exception e) { return new ArrayList<>(); }`，写操作经它会被吞成"成功"。
    @Autowired
    private com.academic.mapper.ClassInfoMapper classInfoMapper;

    // 只用来把 teacher_profile.college_id 翻成学院名（dashboard 的「学院」格）。
    @Autowired
    private com.academic.mapper.CollegeMapper collegeMapper;

    // 教师「推荐选课」落库 + 回看（W14）。此前那条端点一行都不写。
    @Autowired
    private com.academic.mapper.CourseRecommendationMapper courseRecommendationMapper;

    // ==================== 注册（已删，2026-10-08 W27） ====================
    // 这里原来有一条 `POST /teachers/register`：它只往 `teacher_profile` 插一行档案，
    // **不建账号、不设口令、不经 `userService.register`** —— 而 `teacher_profile.user_id`
    // 是 NOT NULL 且有外键指向 `users.id`，所以对真实用户只会插出**第二份档案**，对陌生 id 直接失败。
    // 全仓零调用方（PC/小程序两边封装都不是调用方），真实的注册路径是
    // `POST /api/auth/register/teacher`（`TeacherRegister.vue` 走的就是它），直接删。
    /**
     * 获取教师信息
     */
    @GetMapping("/{id}")
    public ApiResponse<TeacherProfile> getTeacherInfo(@PathVariable Long id) {
        try {
            TeacherProfile teacher = teacherService.getById(id);
            if (teacher != null) {
                return ApiResponse.success(teacher);
            } else {
                return ApiResponse.notFound("教师不存在");
            }
        } catch (Exception e) {
            return ApiResponse.serverError("获取教师信息失败: " + e.getMessage());
        }
    }

    /**
     * 更新教师信息
     */
    @PutMapping("/{id}")
    public ApiResponse<String> updateTeacherInfo(@PathVariable Long id, @RequestBody TeacherProfile teacherProfile) {
        try {
            teacherProfile.setId(id);
            boolean success = teacherService.updateProfile(teacherProfile);
            if (success) {
                return ApiResponse.success("教师信息更新成功");
            } else {
                return ApiResponse.notFound("教师不存在");
            }
        } catch (Exception e) {
            return ApiResponse.serverError("更新教师信息失败: " + e.getMessage());
        }
    }

    /**
     * 删除教师信息（仅限本人或管理员）
     */
    @DeleteMapping("/{id}")
    public ApiResponse<String> deleteTeacherInfo(@PathVariable Long id,
                                                  HttpServletRequest request) {
        Long requestUserId = getUserIdFromGateway(request);
        TeacherProfile profile = teacherService.getByUserId(id);
        if (profile == null || profile.getUserId() == null) {
            return ApiResponse.error("教师不存在");
        }
        if (!profile.getUserId().equals(requestUserId) && !isAdmin(request)) {
            return ApiResponse.error(403, "无权删除其他教师的信息");
        }
        try {
            boolean success = teacherService.deleteProfile(id);
            if (success) {
                return ApiResponse.success("教师信息删除成功");
            } else {
                return ApiResponse.notFound("教师不存在");
            }
        } catch (Exception e) {
            return ApiResponse.serverError("删除教师信息失败: " + e.getMessage());
        }
    }

    /**
     * 获取教师课程列表
     */
    @GetMapping("/{teacherId}/courses")
    public ApiResponse<List<TeacherCourse>> getTeacherCourses(@PathVariable Long teacherId, HttpServletRequest request) {
        // W11 读侧归属闸 —— ⚠️ 本端点是全仓**唯一**收 teacher_profile.id 的教师端读接口
        // （查的是 teacher_course.teacher_id，见 TeacherCourseServiceImpl）。用它去比 X-User-Id
        // 会**永远不等**：于凤海 users.id=29 而 profile.id=1。所以走 isSelfProfile。
        if (!isSelfProfile(teacherId, request)) {
            return forbidden("只能查看本人的任课关系");
        }
        try {
            List<TeacherCourse> courses = teacherCourseService.getCoursesByTeacherId(teacherId);
            return ApiResponse.success(courses);
        } catch (Exception e) {
            return ApiResponse.serverError("获取教师课程列表失败: " + e.getMessage());
        }
    }

    /**
     * 分配课程给教师
     */
    @PostMapping("/courses/assign")
    public ApiResponse<TeacherCourse> assignCourse(@RequestBody TeacherCourse teacherCourse) {
        try {
            boolean success = teacherCourseService.assignCourse(teacherCourse);
            if (success) {
                return ApiResponse.success(teacherCourse);
            } else {
                return ApiResponse.badRequest("课程分配失败");
            }
        } catch (Exception e) {
            return ApiResponse.serverError("课程分配失败: " + e.getMessage());
        }
    }

    /**
     * 创建课程。
     *
     * <p><b>2026-10-04（W12）</b>：请求体自带 {@code teacherId}，此前原样落库 ——
     * 可以建一门**挂在别人名下**的课，或建一门无主的课再靠 PUT 认领。
     * 现在只允许"挂在自己名下"（或不填），管理员不受限。
     */
    @PostMapping("/courses")
    public ApiResponse<Course> createCourse(@RequestBody Course course, HttpServletRequest request) {
        if (!isAdmin(request) && course.getTeacherId() != null
                && !course.getTeacherId().equals(getUserIdFromGateway(request))) {
            return forbidden("只能创建挂在自己名下的课程");
        }
        try {
            boolean success = courseService.createCourse(course);
            if (success) {
                return ApiResponse.success(course);
            } else {
                return ApiResponse.badRequest("课程代码已存在");
            }
        } catch (Exception e) {
            return ApiResponse.serverError("课程创建失败: " + e.getMessage());
        }
    }

    /**
     * 更新课程信息。
     *
     * <p><b>2026-10-04（W12）补归属闸</b>：紧挨着的 {@code DELETE /courses/{id}}（见下）
     * 一直写着"仅限课程所属教师或管理员"，而这一个更新入口一道闸都没有 —— 同一个资源、
     * 两个动词、两套口径。请求体里就带着 {@code teacherId}，所以改一名教师名下的课、
     * 或者把课**改成自己名下的**（连带把这门课的全部读写面都接管过来）都是一次 PUT 的事。
     *
     * <p>口径：课程已经属于本人、或**还没有归属人**（{@code teacher_id IS NULL}，此时
     * "关联课程"是正当动作）、或管理员 ⇒ 放行；课程属于别人 ⇒ 拒。
     * ⚠️ 因此 PC 端 {@code TeacherCourses.vue} 那个「关联课程」按钮从此**只能关联无主讲
     * 的课程**（活库 10 门课都已有主讲，所以它会给出"无权修改其他教师的课程"）——
     * 这是有意的收口，不是回归；详见手册 §6.45。
     */
    @PutMapping("/courses/{id}")
    public ApiResponse<String> updateCourse(@PathVariable Long id, @RequestBody Course course,
                                            HttpServletRequest request) {
        Course existing = courseService.getById(id);
        if (existing == null) {
            return ApiResponse.notFound("课程不存在");
        }
        if (!isAdmin(request)) {
            Long me = getUserIdFromGateway(request);
            boolean unowned = existing.getTeacherId() == null;
            boolean mine = me != null && me.equals(existing.getTeacherId());
            if (!unowned && !mine) {
                return forbidden("无权修改其他教师的课程");
            }
        }
        try {
            course.setId(id);
            boolean success = courseService.updateCourse(course);
            if (success) {
                return ApiResponse.success("课程信息更新成功");
            } else {
                return ApiResponse.notFound("课程不存在");
            }
        } catch (Exception e) {
            return ApiResponse.serverError("更新课程信息失败: " + e.getMessage());
        }
    }

    /**
     * 删除课程（仅限课程所属教师或管理员）
     */
    @DeleteMapping("/courses/{id}")
    public ApiResponse<String> deleteCourse(@PathVariable Long id,
                                             HttpServletRequest request) {
        Long requestUserId = getUserIdFromGateway(request);
        Course course = courseService.getById(id);
        if (course != null && course.getTeacherId() != null
                && !course.getTeacherId().equals(requestUserId) && !isAdmin(request)) {
            return ApiResponse.error(403, "无权删除其他教师的课程");
        }
        try {
            boolean success = courseService.deleteCourse(id);
            if (success) {
                return ApiResponse.success("课程删除成功");
            } else {
                return ApiResponse.notFound("课程不存在");
            }
        } catch (Exception e) {
            return ApiResponse.serverError("删除课程失败: " + e.getMessage());
        }
    }

    /**
     * 获取课程列表
     */
    @GetMapping("/courses")
    public ApiResponse<List<Course>> getCourseList(@RequestParam(required = false) Long teacher_id, HttpServletRequest request) {
        // W11 读侧归属闸：courses.teacher_id 收 **users.id**（与 _id 后缀无关，别被名字骗了）。
        // 不传 teacher_id 是"全部课程"列表（前端 teacherAPI.getAllCourses 就这么用），不属归属人型，照旧放行。
        if (teacher_id != null && !isSelf(teacher_id, request)) {
            return forbidden("只能查看本人的课程");
        }
        try {
            List<Course> courses;
            if (teacher_id != null) {
                courses = courseService.getCoursesByTeacherId(teacher_id);
            } else {
                courses = courseService.list();
            }
            return ApiResponse.success(courses);
        } catch (Exception e) {
            return ApiResponse.serverError("获取课程列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取教师仪表盘
     */
    @GetMapping("/dashboard/{userId}")
    public ApiResponse<Object> getDashboard(@PathVariable String userId, HttpServletRequest request) {
        // W11 读侧归属闸：路径参数是 users.id —— 换个数字就读到别的教师的看板。
        if (!isSelf(Long.parseLong(userId), request)) {
            return forbidden("只能查看本人的工作台");
        }
        try {
            Long teacherId = Long.parseLong(userId);
            List<Object> myClasses = (List<Object>) (List<?>) classInfoService.getClassesByTeacherId(teacherId);
            List<Object> myRequests = (List<Object>) (List<?>) applicationService.getApplicationsByTeacherId(teacherId);
            
            // 计算总学生数
            int totalStudents = 0;
            for (Object clazz : myClasses) {
                if (clazz instanceof Map) {
                    Object studentCount = ((Map<?, ?>) clazz).get("studentCount");
                    if (studentCount instanceof Number) {
                        totalStudents += ((Number) studentCount).intValue();
                    }
                }
            }
            
            // 获取教师名下课程数和姓名
            int totalCourses = 0;
            String teacherName = "";
            String collegeName = "";
            try {
                List<Object> courses = (List<Object>) (List<?>) courseService.getCoursesByTeacherId(teacherId);
                totalCourses = courses != null ? courses.size() : 0;
            } catch (Exception e) {
                log.warn("获取课程列表失败(降级): {}", e.getMessage());
            }
            try {
                TeacherProfile profile = teacherService.getByUserId(teacherId);
                if (profile != null && profile.getName() != null) {
                    teacherName = profile.getName();
                }
                // 2026-10-03（W8.2）：带出学院名 —— 教师端「个人设置 → 工作信息 → 学院」
                // 那一格原先是 disabled 恒空，因为**没有任何接口返回学院**。此处补上
                // （teacher_profile.college_id 有值，活库 6 行分别指向学院 1/2）。
                if (profile != null && profile.getCollegeId() != null) {
                    String name = collegeMapper.getNameById(profile.getCollegeId());
                    if (name != null) {
                        collegeName = name;
                    }
                }
            } catch (Exception e) {
                log.warn("获取教师信息失败(降级): {}", e.getMessage());
            }
            
            Map<String, Object> dashboard = new HashMap<>();
            dashboard.put("totalClasses", myClasses.size());
            dashboard.put("pendingRequests", myRequests.size());
            dashboard.put("totalStudents", totalStudents);
            // 2026-10-01 第二十四轮：这里原先是写死的 0，而**同一个文件**的 /todos（下方
            // pendingFeedbacks 那段）早就有真实统计了 —— 真实现和桩并存，被首页读的是桩，
            // 于是教师首页「待回复反馈」永远是 0。现在改用同一口径真算：
            // status==0 即待回复（与 /todos 的判断逐字一致，避免两处口径漂移）。
            long pendingFeedbacks = 0L;
            try {
                List<Feedback> feedbacks = feedbackService.getFeedbacks(teacherId, null);
                pendingFeedbacks = feedbacks.stream()
                        .filter(f -> f.getStatus() != null && f.getStatus() == 0)
                        .count();
            } catch (Exception e) {
                // 与上面两段同样的降级口径：取不到就保持 0，但留日志，不静默。
                log.warn("获取待回复反馈数失败(降级): {}", e.getMessage());
            }
            dashboard.put("pendingFeedbacks", pendingFeedbacks);
            dashboard.put("totalCourses", totalCourses);
            dashboard.put("teacherName", teacherName);
            dashboard.put("college", collegeName);
            
            return ApiResponse.success(dashboard);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取教师的学生列表
     */
    @GetMapping("/students/{teacherId}")
    public ApiResponse<Object> getStudents(@PathVariable String teacherId, HttpServletRequest request) {
        // W11 读侧归属闸：users.id 空间（本端点当前零调用方，闸照加 —— 见手册 §6.44）。
        if (!isSelf(Long.parseLong(teacherId), request)) {
            return forbidden("只能查看本人班级的学生");
        }
        try {
            Long tid = Long.parseLong(teacherId);
            // 从教师管理的班级获取学生
            List<Map<String, Object>> classes = classInfoService.getClassesByTeacherId(tid);
            List<Object> allStudents = new ArrayList<>();
            for (Map<String, Object> clazz : classes) {
                Long classId = (Long) clazz.get("id");
                List<Map<String, Object>> classStudents = classInfoService.getStudentsByClassId(classId);
                allStudents.addAll(classStudents);
            }
            return ApiResponse.success(allStudents);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取课程成绩（带教师过滤）
     */
    @GetMapping("/scores")
    public ApiResponse<Object> getScores(@RequestParam Long course_id, @RequestParam(required = false) Long teacher_id,
                                         HttpServletRequest request) {
        // W12 资源归属闸：`course_id` 是课程 id。此前传了 teacher_id 也只当**查询条件**
        // 用（`WHERE teacher_id = ?`），换个 course_id 就绕开了 —— 闸必须落在课程归属上。
        ApiResponse<Object> deny = denyUnlessMyCourse(course_id, request, "只能查看本人所授课程的成绩");
        if (deny != null) {
            return deny;
        }
        try {
            List<Object> scores;
            if (teacher_id != null) {
                scores = (List<Object>) (List<?>) scoreRecordService.getScoresByCourseIdAndTeacherId(course_id, teacher_id);
            } else {
                scores = (List<Object>) (List<?>) scoreRecordService.getScoresByCourseId(course_id);
            }
            return ApiResponse.success(scores);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 按班级获取成绩（带教师过滤）
     */
    @GetMapping("/scores/by-class")
    public ApiResponse<Object> getScoresByClass(@RequestParam String class_name, @RequestParam(required = false) Long teacher_id,
                                                HttpServletRequest request) {
        // W11 读侧归属闸：teacher_id 收 users.id，传了就必须是本人。
        // 不传的那条分支是"全班成绩"视图（含别位教师录的行）—— W30（2026-10-08）之前
        // **完全没有校验**，理由是"前端 useTeacherRoster 靠它拿姓名+分数"。
        // 该理由已被证伪（useTeacherRoster 走的是带 teacher_id 的过滤分支），
        // 见 denyUnlessMyClassByName 的 javadoc。现在按班级归属把关：
        // 只有这个班的班主任（或管理员）能看全班成绩。
        if (teacher_id != null) {
            if (!isSelf(teacher_id, request)) {
                return forbidden("只能查看本人所授班级的成绩");
            }
            try {
                return ApiResponse.success((List<Object>) (List<?>) scoreRecordService
                        .getScoresByClassNameAndTeacherId(class_name, teacher_id));
            } catch (Exception e) {
                return ApiResponse.error(e.getMessage());
            }
        }
        ApiResponse<Object> deny = denyUnlessMyClassByName(class_name, request, "只能查看本人所带班级的成绩");
        if (deny != null) {
            return deny;
        }
        try {
            return ApiResponse.success((List<Object>) (List<?>) scoreRecordService.getScoresByClassName(class_name));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 保存成绩
     */
    @PostMapping("/scores")
    public ApiResponse<Object> saveScores(@RequestBody Map<String, Object> data, HttpServletRequest request) {
        // W12 资源归属闸：`teacherId` 与每行的 `courseId` 都是客户端说了算的。
        ApiResponse<Object> deny = denyUnlessScoreWriteAllowed(data, request, "只能为本人所授课程保存成绩");
        if (deny != null) {
            return deny;
        }
        try {
            List<Map<String, Object>> scores = (List<Map<String, Object>>) data.get("scores");
            Long teacherId = Long.parseLong(data.get("teacherId").toString());
            scoreRecordService.saveScores(scores, teacherId);
            return ApiResponse.success("成绩保存成功");
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 更新成绩
     *
     * <p>2026-10-03（W10）：补**归属校验**。
     *
     * <p>此前这个端点只有 {@code scoreId} 一个入参，连"谁在改"都没有 —— 而**同一个资源**的
     * {@code DELETE /scores/{scoreId}}（见下方 {@code deleteScore}）一直有归属闸。
     * 同一个 id、两个动词、两套口径：删不掉别人的成绩，却改得了别人的分数。
     * 实测（经网关 8076）：教师 18 用 {@code PUT /teachers/scores/{29名下的id}} 直接改动了
     * 教师 29 录入的成绩，回 200「成绩更新成功」。
     *
     * <p>判据与 {@code deleteScore} 逐字同形：录入人不是自己且不是管理员 ⇒ 拒。
     * 返回形状沿用那个既有闸的 **HTTP 200 + body {@code code:403}**（本仓归属拒绝的惯用形状，
     * 前端按 {@code code} 判），不改它们的读取方式。
     */
    @PutMapping("/scores/{scoreId}")
    public ApiResponse<Object> updateScore(@PathVariable Long scoreId, @RequestBody Map<String, Object> data,
                                            HttpServletRequest request) {
        Long requestUserId = getUserIdFromGateway(request);
        ScoreRecord record = scoreRecordService.getById(scoreId);
        if (record != null && record.getTeacherId() != null
                && !record.getTeacherId().equals(requestUserId) && !isAdmin(request)) {
            return ApiResponse.error(403, "无权修改其他教师录入的成绩");
        }
        try {
            Double score = Double.parseDouble(data.get("score").toString());
            String remark = (String) data.get("remark");
            String modifiedBy = (String) data.getOrDefault("modifiedBy", "教师");
            scoreRecordService.updateScore(scoreId, score, remark, modifiedBy);
            return ApiResponse.success("成绩更新成功");
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 导出某门课的成绩单为真实 xlsx（W1.8，2026-10-02 改）。
     *
     * <p>改之前 {@code /export/scores} 与 {@code /download/scores} 是两个**返回 JSON
     * 列表**的端点，而前端 {@code TeacherScores.vue} 用 {@code responseType:'blob'}
     * 把它们当文件存下来 —— 用户拿到的"xlsx"其实是一段 JSON 文本。现在只留一条
     * 路径，真的产出工作簿；重复的 {@code /download/scores} 一并删掉（前后端都零调用方
     * 的那一条）。
     *
     * <p>⚠️ <b>2026-10-04（W12）更正</b>：上面那句"不做课程归属校验"已经不成立 ——
     * 本轮给它补上了和同族读端点一样的课程归属闸。此前它是**一次能把别人整门课
     * 成绩单（姓名 + 各项分数）下载成 xlsx** 的出口，比逐条读还省事。
     */
    @GetMapping("/export/scores")
    public void exportScores(@RequestParam Long course_id, HttpServletRequest request,
                             HttpServletResponse response) {
        ApiResponse<Object> deny = denyUnlessMyCourse(course_id, request, "只能导出本人所授课程的成绩单");
        if (deny != null) {
            // 这个端点返回的是**文件流**，不能直接 return ApiResponse —— 得自己写回状态与 JSON 体，
            // 否则前端 responseType:'blob' 拿到的是一个 200 的、内容却是错误提示的"xlsx"。
            writeJsonDenied(response, deny);
            return;
        }
        try {
            List<ScoreRecord> scores = scoreRecordService.exportScores(course_id);

            // 第一列是「学号」（W19 加）：导出件要能当模板用 —— 改完分数直接导回来。
            // 学号从 student_profile 顺带查出来（见 ScoreRecordMapper.getScoresByCourseId）。
            String[] headers = {"学号", "姓名", "班级", "平时成绩", "期末成绩", "总评成绩", "等级", "绩点", "学期", "备注"};
            byte[] content;
            try (Workbook workbook = new XSSFWorkbook();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                Sheet sheet = workbook.createSheet("成绩单");
                Row headerRow = sheet.createRow(0);
                for (int i = 0; i < headers.length; i++) {
                    headerRow.createCell(i).setCellValue(headers[i]);
                }
                int rowNum = 1;
                for (ScoreRecord s : scores) {
                    Row row = sheet.createRow(rowNum++);
                    row.createCell(0).setCellValue(s.getStudentNo() == null ? "" : s.getStudentNo());
                    row.createCell(1).setCellValue(s.getStudentName() == null ? "" : s.getStudentName());
                    row.createCell(2).setCellValue(s.getClassName() == null ? "" : s.getClassName());
                    setNumber(row.createCell(3), s.getRegularScore());
                    setNumber(row.createCell(4), s.getFinalScore());
                    setNumber(row.createCell(5), s.getScoreTotal());
                    row.createCell(6).setCellValue(s.getGrade() == null ? "" : s.getGrade());
                    setNumber(row.createCell(7), s.getGradePoint());
                    row.createCell(8).setCellValue(s.getSemester() == null ? "" : s.getSemester());
                    row.createCell(9).setCellValue(s.getRemark() == null ? "" : s.getRemark());
                }
                for (int i = 0; i < headers.length; i++) {
                    sheet.autoSizeColumn(i);
                }
                workbook.write(out);
                content = out.toByteArray();
            }

            String fileName = "scores_course" + course_id + "_"
                    + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                    + ".xlsx";
            writeXlsx(response, content, fileName);
            log.info("Export scores ok: course_id={}, rows={}", course_id, scores.size());
        } catch (Exception e) {
            log.error("Export scores failed: {}", e.getMessage(), e);
            try {
                if (!response.isCommitted()) {
                    response.reset();
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":500,\"message\":\"导出失败: "
                            + String.valueOf(e.getMessage()).replace("\"", "'") + "\"}");
                    response.getWriter().flush();
                }
            } catch (Exception ignore) {
                // 响应已提交，无处可写
            }
        }
    }

    /** 数字列：空值写空串 —— "没录入"和"0 分"是两回事。 */
    private static void setNumber(Cell cell, Double value) {
        if (value == null) {
            cell.setCellValue("");
        } else {
            cell.setCellValue(value);
        }
    }

    /** 把一段 xlsx 字节当附件写回（导出成绩单与下载模板共用）。 */
    private static void writeXlsx(HttpServletResponse response, byte[] content, String fileName) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
        response.setContentLength(content.length);
        response.getOutputStream().write(content);
        response.getOutputStream().flush();
    }

    /**
     * 导入成绩 —— **上传 Excel，服务端 POI 解析**。
     *
     * <p><b>2026-10-04（W19）换契约</b>：改之前这里收 {@code @RequestBody Map}
     * （{@code scores} 列表 + {@code teacherId}），而前端 {@code TeacherScores.vue} 发的是
     * {@code FormData}（{@code file/courseId/className/semester}）—— 两边从来对不上，
     * **这个按钮一次都没成功过**。本服务里 {@code /students/import} 本来就是 multipart + POI，
     * 模板与导出也都是真 xlsx，所以直接换成 multipart（前端那侧一并改成"先选课程再导"）。
     *
     * <p>归属闸落在**课程**上：{@code courseId} 是这次导入的目标课程，必须是本人所授
     * （管理员旁路）。闸放在读文件**之前** —— 拒绝一个不属于自己的课程，不需要先把文件解析完。
     *
     * <p>学号 → {@code student_profile.id} 的换算在这里做（{@code saveScores} 要的是**档案 id**，
     * 不是学号字符串）。
     *
     * <p><b>列口径</b>（与 {@link #downloadScoresTemplate} 产出的模板一致，也与本端点真正
     * 能落库的字段一致）：{@code 学号(必填) | 姓名 | 总评成绩(必填) | 学期 | 备注}。
     * 刻意**没有「平时成绩/期末成绩」**：{@code ScoreRecordService.saveScores} 不读这两个 key，
     * {@code insertScore} 的 INSERT 列里也没有它们，放上模板只会变成"填了却静默消失"。
     * 导出文件里那两列同样被忽略；但"导出→改分→导回"改的是**已存在的行**，
     * {@code saveScores} 走 existing 分支时不碰这两列（从库里原样保留），所以不会丢。
     *
     * <p><b>semester 必须是数字</b>：{@code scores.semester} 是 int 列，而
     * {@code score_record.semester} 是 varchar —— 塞进 {@code "2025-2026-1"} 这类字符串，
     * 写 {@code scores} 那一步会在 MySQL 严格模式下报错。所以这里先卡格式，不留给数据库去炸。
     */
    @PostMapping("/scores/import")
    public ApiResponse<Map<String, Object>> importScores(@RequestParam("file") MultipartFile file,
                                                        @RequestParam("courseId") Long courseId,
                                                        @RequestParam(value = "className", required = false) String className,
                                                        @RequestParam(value = "semester", required = false) String semester,
                                                        HttpServletRequest request) {
        ApiResponse<Object> deny = denyUnlessMyCourse(courseId, request, "只能向本人所授课程导入成绩");
        if (deny != null) {
            return ApiResponse.error(403, deny.getMessage());
        }

        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> errors = new ArrayList<>();
        int acceptedCount = 0, skipCount = 0;
        result.put("fileName", file == null ? null : file.getOriginalFilename());

        try {
            if (semester != null && !semester.trim().isEmpty()) {
                int sem;
                try {
                    sem = Integer.parseInt(semester.trim());
                } catch (NumberFormatException e) {
                    return ApiResponse.error("学期必须是数字（如 5），收到的是：" + semester);
                }
                if (sem < 1 || sem > 20) {
                    return ApiResponse.error("学期超出合理范围（1~20），收到的是：" + semester);
                }
            }
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
            // W22：`new XSSFWorkbook(...)` 读不了 .xls，而上面的扩展名闸写着"仅支持 .xlsx 或 .xls"。
            // WorkbookFactory 让那句承诺变成真的（真 .xls 走 HSSF）。
            //
            // 解析失败的翻译**放在这一层**，不放外层 catch：外层 catch 还包着 saveScores 落库，
            // 那里抛的异常绝不能被说成"你的文件有问题"。
            Workbook opened;
            try {
                opened = WorkbookFactory.create(file.getInputStream());
            } catch (Exception e) {
                log.error("Parse scores excel failed: {}", file.getOriginalFilename(), e);
                String friendly = ExcelUploadErrors.describe(e);
                // 走到这里一定是"读不了这个文件"，认不出具体类型就按"认不出是什么文件"回。
                return ApiResponse.error(friendly != null ? friendly : ExcelUploadErrors.UNREADABLE);
            }
            try (Workbook workbook = opened) {
                Sheet sheet = workbook.getSheetAt(0);
                if (sheet == null || sheet.getLastRowNum() < 1) {
                    return ApiResponse.error("表里没有数据行（第 1 行应当是表头，数据从第 2 行开始）");
                }
                Row header = sheet.getRow(0);
                Integer sidCol = findColumn(header, "学号");
                if (sidCol == null) {
                    return ApiResponse.error("表头里找不到「学号」列，请先下载模板：/teachers/scores/template");
                }
                Integer scoreCol = findScoreColumn(header);
                if (scoreCol == null) {
                    return ApiResponse.error("表头里找不到「成绩」列，请先下载模板：/teachers/scores/template");
                }
                Integer nameCol = findColumn(header, "姓名");
                Integer semCol = findColumn(header, "学期");
                Integer remarkCol = findColumn(header, "备注");

                for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;
                    int rowNum = r + 1;   // 报给老师看的行号 = 他在 Excel 里看到的行号
                    String studentId = cellText(row.getCell(sidCol));
                    String rawScore = cellText(row.getCell(scoreCol));
                    if (studentId == null && rawScore == null) continue;   // 整行空

                    if (studentId == null) {
                        errors.add(rowError(rowNum, "学号为空")); skipCount++; continue;
                    }
                    if (rawScore == null) {
                        errors.add(rowError(rowNum, "成绩为空")); skipCount++; continue;
                    }
                    double score;
                    try {
                        score = Double.parseDouble(rawScore);
                    } catch (NumberFormatException e) {
                        errors.add(rowError(rowNum, "成绩不是数字：" + rawScore)); skipCount++; continue;
                    }

                    Long profileId = classInfoMapper.findProfileIdByStudentId(studentId);
                    if (profileId == null) {
                        errors.add(rowError(rowNum, "学号不存在：" + studentId)); skipCount++; continue;
                    }

                    // 姓名：Excel 里填了就用填的；没填就从档案回填 —— 否则导入的行在成绩
                    // 列表里只有分数没有姓名，正是本仓一直在清的那种"半截数据"。
                    String studentName = nameCol == null ? null : cellText(row.getCell(nameCol));
                    if (studentName == null) {
                        studentName = classInfoMapper.findStudentNameByStudentId(studentId);
                    }

                    Map<String, Object> rowData = new HashMap<>();
                    rowData.put("studentId", profileId);
                    rowData.put("courseId", courseId);
                    rowData.put("score", score);
                    rowData.put("studentName", studentName);
                    rowData.put("className", className);
                    // 每行可以覆盖学期；整表没给就用请求参数里那个。
                    rowData.put("semester", semCol == null ? semester : cellText(row.getCell(semCol)));
                    rowData.put("remark", remarkCol == null ? null : cellText(row.getCell(remarkCol)));
                    rows.add(rowData);
                    acceptedCount++;
                }
            }

            // saveScores 是 @Transactional 的：要么这批全落库，要么一行都不落。
            // 所以只有它正常返回，上面的 acceptedCount 才等于真正写进去的行数。
            if (!rows.isEmpty()) {
                scoreRecordService.saveScores(rows, getUserIdFromGateway(request));
            }

            result.put("total", acceptedCount + skipCount);
            result.put("success", acceptedCount);
            result.put("skipped", skipCount);
            result.put("errors", errors);
            log.info("Import scores ok: courseId={}, total={}, success={}, skip={}",
                    courseId, acceptedCount + skipCount, acceptedCount, skipCount);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Import scores failed", e);
            // W22：不再把 POI 的英文原文回给客户端。是"文件本身的问题"就给可操作的中文，
            // 否则说明是落库等服务端故障 —— 那种情况回"文件损坏"是谎报原因，给中性文案。
            String friendly = ExcelUploadErrors.describe(e);
            return ApiResponse.error(friendly != null ? friendly
                    : "导入失败：服务器处理这个文件时出错，请稍后重试或联系管理员");
        }
    }

    /**
     * 教师端成绩导入模板（真 xlsx）。列口径与 {@link #importScores} **逐字一致** ——
     * 模板就是这份契约唯一的人话说明，两边一旦不一致，教师照着模板填也会全军覆没。
     */
    @GetMapping("/scores/template")
    public void downloadScoresTemplate(HttpServletResponse response) throws IOException {
        String[] headers = {"学号(必填)", "姓名", "总评成绩(必填)", "学期", "备注"};
        byte[] content;
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("成绩导入模板");
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }
            Row example = sheet.createRow(1);
            example.createCell(0).setCellValue("2023020616");
            example.createCell(1).setCellValue("司明");
            example.createCell(2).setCellValue(85.5);
            example.createCell(3).setCellValue("5");
            example.createCell(4).setCellValue("");
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(out);
            content = out.toByteArray();
        }
        writeXlsx(response, content, "成绩导入模板.xlsx");
        log.info("Download scores template ok");
    }

    /**
     * 挑「总评成绩」那一列。
     *
     * <p>不能直接用 {@code findColumn(header,"成绩")} —— 它回的是**第一个**含「成绩」的列，
     * 而导出件里「平时成绩」「期末成绩」都排在「总评成绩」前面，那样会把一门课的
     * 总评读成平时分。这里让最具体的「总评」直接胜出，并**显式排除**本端点不落的
     * 平时/期末两列；都没有时才退到泛化的「成绩/分数」。
     */
    private static Integer findScoreColumn(Row header) {
        if (header == null || header.getLastCellNum() < 0) return null;
        Integer fallback = null;
        for (int c = header.getFirstCellNum(); c < header.getLastCellNum(); c++) {
            String h = cellText(header.getCell(c));
            if (h == null) continue;
            String flat = h.replace(" ", "");
            if (flat.contains("总评")) return c;
            if (flat.contains("平时") || flat.contains("期末")) continue;
            if (fallback == null && (flat.contains("成绩") || flat.contains("分数"))) fallback = c;
        }
        return fallback;
    }

    /**
     * 获取反馈
     */
    @GetMapping("/feedbacks")
    public ApiResponse<Object> getFeedbacks(@RequestParam Long teacherId, @RequestParam(required = false) String category,
                                           HttpServletRequest request) {
        // W11 读侧归属闸。与 `/feedbacks/{teacherId}/list` 是**两条不同的映射**，只补后者会漏这条
        // —— 探针的 @RequestParam 抽取器当时认不出"光秃秃没括号"的 @RequestParam，所以这条
        // 一直到 W11 修完抽取器才浮出来（实测 teacher18 打 ?teacherId=29 拿到 29 的反馈）。
        if (!isSelf(teacherId, request)) {
            return forbidden("只能查看本人的反馈");
        }
        try {
            List<Object> feedbacks = (List<Object>) (List<?>) feedbackService.getFeedbacks(teacherId, category);
            return ApiResponse.success(feedbacks);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取学生报名信息（选课记录）
     */
    @GetMapping("/enrollments")
    public ApiResponse<Object> getEnrollments(@RequestParam(required = false) Long teacherId, @RequestParam(required = false) Long courseId,
                                              HttpServletRequest request) {
        // W11 读侧归属闸。两条分支的归属判据**不同**：
        //   · teacherId 分支 —— 查询参数收 users.id，必须是本人；
        //   · courseId  分支 —— 看的是"某门课的全体选课学生"（带姓名+成绩），归属落在**课程**上，
        //                      所以要求这门课是本人所授（管理员旁路）。
        // 实测：不加闸时教师 18 打 `?teacherId=29` 一次拿到 **246 行带学生姓名**的花名册；
        // `?courseId=N` 同理能读任意课程的选课名单。courseId 分支当前零调用方，闸照加。
        if (teacherId != null && !isSelf(teacherId, request)) {
            return forbidden("只能查看本人所带班级的选课情况");
        }
        if (courseId != null && !isAdmin(request)) {
            Course owned = courseService.getById(courseId);
            Long owner = owned == null ? null : owned.getTeacherId();
            if (owner == null || !owner.equals(getUserIdFromGateway(request))) {
                return forbidden("只能查看本人所授课程的选课情况");
            }
        }
        try {
            List<Map<String, Object>> enrollments = new ArrayList<>();
            if (courseId != null) {
                // 根据课程获取报名学生。
                // 两条分支刻意输出**同一组键**（courseId/courseName/credits/type/className/
                // score/scoreLevel），这样调用方拿到哪一支都能按 courseId 分组 ——
                // 早先这一支只给 4 个键、另一支给 9 个，前端得写两套逻辑。
                // courseName/credits/type 课程表里才有，这里查一次补齐（不是编造）。
                Course course = courseService.getById(courseId);
                for (ScoreRecord score : scoreRecordService.getScoresByCourseId(courseId)) {
                    Map<String, Object> enrollment = new HashMap<>();
                    enrollment.put("courseId", courseId);
                    enrollment.put("courseName", course == null ? null : course.getName());
                    enrollment.put("credits", course == null ? null : course.getCredits());
                    enrollment.put("type", course == null ? null : course.getType());
                    enrollment.put("className", score.getClassName());
                    enrollment.put("studentId", score.getStudentId());
                    enrollment.put("studentName", score.getStudentName());
                    enrollment.put("score", score.getScore());
                    enrollment.put("scoreLevel", score.getScoreLevel());
                    enrollment.put("status", "已选课");
                    enrollments.add(enrollment);
                }
            } else if (teacherId != null) {
                // 获取教师管辖班级的学生成绩记录（含课程名、学分、类型）
                List<Map<String, Object>> classes = classInfoService.getClassesByTeacherId(teacherId);
                for (Map<String, Object> c : classes) {
                    Long classId = (Long) c.get("id");
                    String className = (String) c.get("name");
                    // 这一支的数据源是 mapper 里手写的 JOIN，返回的**就是**
                    // Map（列名是下划线，因为 SQL 没起别名）。全场唯一一处
                    // 真·Map 结果集，与上面那些"实体被当 Map 用"的坑无关。
                    List<Map<String, Object>> rows = scoreRecordService.getScoresByClassId(classId);
                    if (rows != null) {
                        for (Map<String, Object> score : rows) {
                            Map<String, Object> enrollment = new HashMap<>();
                            enrollment.put("studentId", score.get("student_id"));
                            enrollment.put("studentName", score.get("student_name"));
                            enrollment.put("courseId", score.get("course_id"));
                            enrollment.put("courseName", score.get("course_name"));
                            enrollment.put("credits", score.get("credits"));
                            enrollment.put("type", score.get("type"));
                            enrollment.put("className", className);
                            enrollment.put("score", score.get("score_total"));
                            enrollment.put("scoreLevel", score.get("score_level"));
                            enrollments.add(enrollment);
                        }
                    }
                }
            }
            return ApiResponse.success(enrollments);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 保存沟通记录
     */
    @PostMapping("/communications")
    public ApiResponse<Object> saveCommunication(@RequestBody Map<String, Object> data) {
        try {
            Long teacherId = Long.parseLong(data.get("teacherId").toString());
            Long studentId = Long.parseLong(data.get("studentId").toString());
            String content = (String) data.get("content");
            String type = (String) data.get("type");
            String studentName = (String) data.get("studentName");
            
            communicationRecordService.saveCommunication(teacherId, studentId, content, type, studentName);
            return ApiResponse.success("沟通记录保存成功");
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 根据班级名称获取辅导员信息
     */
    @GetMapping("/counselor/by-class")
    public ApiResponse<Object> getCounselorByClass(@RequestParam String class_name) {
        try {
            Map<String, Object> counselor = classInfoService.getCounselorByClassName(class_name);
            if (counselor == null || counselor.isEmpty()) {
                return ApiResponse.success(null);
            }
            return ApiResponse.success(counselor);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取课程分析
     */
    @GetMapping("/analysis")
    public ApiResponse<Object> getAnalysis(@RequestParam Long course_id, HttpServletRequest request) {
        // W12 资源归属闸。`course_id` 是**课程** id，不是教师 id —— 换个数字就能读到
        // 别人的课程分析（平均分/及格率/挂科人数/最高分名单），实测对别人课程回 200。
        ApiResponse<Object> deny = denyUnlessMyCourse(course_id, request, "只能查看本人所授课程的分析");
        if (deny != null) {
            return deny;
        }
        try {
            return ApiResponse.success(buildCourseAnalysis(course_id));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取课程详情
     */
    @GetMapping("/courses/{courseId}")
    public ApiResponse<Object> getCourseDetail(@PathVariable Long courseId, HttpServletRequest request) {
        try {
            Course course = courseService.getById(courseId);
            // 先保住既有的 404 契约（课程真的不存在），再判归属 —— 顺序反过来会把
            // "不存在"和"不是你的"压成同一个 403，前端那句「课程不存在」就再也看不到了。
            if (course == null) {
                return ApiResponse.notFound("课程不存在");
            }
            if (!isAdmin(request)) {
                Long me = getUserIdFromGateway(request);
                if (me == null || course.getTeacherId() == null || !course.getTeacherId().equals(me)) {
                    return forbidden("只能查看本人所授课程的详情");
                }
            }
            return ApiResponse.success(course);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 推荐课程（W14，2026-10-04 真实现）。
     *
     * <p><b>修之前是纯回声桩</b>：把入参原样吐回来、**一行都不落库**，却回一句
     * 「课程推荐成功，学生将收到通知」；而前端 {@code TeacherCourses.vue} 连
     * {@code teacherId} 都不发 ⇒ `data.get("teacherId").toString()` 先 NPE。
     * 库里也没有任何推荐表。
     *
     * <p><b>现在</b>：身份取 {@code X-User-Id}（不再由请求体声明 —— 谁发的就记谁），
     * 校验"这门课是不是我教的"，落一行 {@code course_recommendation}，
     * 并把**真正会看到它的人数**回给前端 —— 提示语从此有据可依。
     *
     * <p>⚠️ 这里没有复用 {@link #denyUnlessMyCourse}：那条对"课程不存在"也回 403，
     * 而本端点需要把"不存在"(404) 与"不是你的"(403) 分开。
     */
    @PostMapping("/courses/recommend")
    public ApiResponse<Object> recommendCourse(@RequestBody(required = false) Map<String, Object> data,
                                               HttpServletRequest request) {
        Long me = getUserIdFromGateway(request);
        if (me == null) {
            return forbidden("请先登录");
        }
        Long courseId = parseLongOrNull(data == null ? null : data.get("courseId"));
        if (courseId == null) {
            return ApiResponse.badRequest("缺少 courseId");
        }
        String reason = firstNonBlank(data.get("reason"));
        if (reason == null) {
            return ApiResponse.badRequest("请填写推荐理由");
        }
        Course course = courseService.getById(courseId);
        if (course == null) {
            return ApiResponse.notFound("课程不存在");
        }
        if (!isAdmin(request)
                && (course.getTeacherId() == null || !course.getTeacherId().equals(me))) {
            return forbidden("只能推荐本人所授课程");
        }
        Long targetClassId = parseLongOrNull(data.get("targetClassId"));
        if (targetClassId != null) {
            ApiResponse<Object> deny = denyUnlessMyClass(targetClassId, request, "只能把课程推荐给本人所带的班级");
            if (deny != null) {
                return deny;
            }
        }

        CourseRecommendation rec = new CourseRecommendation();
        rec.setTeacherId(me);
        rec.setCourseId(courseId);
        rec.setTargetClassId(targetClassId);
        // reason 列是 varchar(500)；严格模式下超长会直接抛 DataTooLong（变成"推荐失败"而其实只是话太长）。
        rec.setReason(truncate(reason, 500));
        rec.setStatus(1);
        courseRecommendationMapper.insert(rec);

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", rec.getId());
        result.put("courseId", courseId);
        result.put("courseName", course.getName());
        result.put("targetClassId", targetClassId);
        result.put("reason", rec.getReason());
        result.put("recipients", courseRecommendationMapper.countRecipients(me, targetClassId));
        result.put("status", "已推荐");
        return ApiResponse.success(result);
    }

    /**
     * 我发出过的推荐选课（教师端回看）。
     *
     * <p>与 {@code GET /courses/{courseId}/recommendations}（那条是**教学建议**，硬编码两句话）
     * 是两回事：分段数不同，不冲突。
     */
    @GetMapping("/courses/recommendations")
    public ApiResponse<Object> listMyRecommendations(HttpServletRequest request) {
        Long me = getUserIdFromGateway(request);
        if (me == null) {
            return forbidden("请先登录");
        }
        return ApiResponse.success(courseRecommendationMapper.listByTeacher(me));
    }

    /**
     * 获取预警信息
     */
    @GetMapping("/warnings/{teacherId}")
    public ApiResponse<Object> getWarnings(@PathVariable String teacherId, @RequestParam(required = false) String status,
                                           HttpServletRequest request) {
        // W11 读侧归属闸：users.id 空间。
        if (!isSelf(Long.parseLong(teacherId), request)) {
            return forbidden("只能查看本人的预警");
        }
        try {
            Long tid = Long.parseLong(teacherId);
            List<Map<String, Object>> result = new ArrayList<>();
            List<Map<String, Object>> classes = classInfoService.getClassesByTeacherId(tid);
            for (Map<String, Object> clazz : classes) {
                Long classId = (Long) clazz.get("id");
                if (classId == null) continue;
                List<com.academic.common.entity.AcademicWarning> wlist = academicWarningMapper.findByClassId(classId);
                for (com.academic.common.entity.AcademicWarning w : wlist) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", w.getId());
                    m.put("studentId", w.getStudentId());
                    m.put("studentName", w.getStudentName());
                    m.put("warningLevel", w.getWarningLevel() != null ? (w.getWarningLevel() >= 3 ? "严重" : w.getWarningLevel() == 2 ? "中度" : "轻度") : "轻度");
                    m.put("title", w.getTitle());
                    m.put("description", w.getDescription());
                    m.put("type", w.getType());
                    // 2026-10-02（W1.6）：原来是两态 `status == 0 ? "待处理" : "已处理"`，
                    // 于是 status=2（已解除）被翻成"已处理"—— 与同一个类里另一处翻成"待处理"**相反**。
                    // 收敛到 WarningStatus.text()，三处共用一份。
                    m.put("status", WarningStatus.text(w.getStatus()));
                    m.put("className", clazz.get("name"));
                    m.put("createdAt", w.getCreatedAt());
                    result.add(m);
                }
            }
            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 处理预警。
     *
     * <p>⚠️ 原实现是个**桩**：读一下请求体就返回
     * {@code {status:"已处理", processedAt:now}}，一行库都不写。
     * PC 端 {@code TeacherWarnings.vue} 拿到 200 就弹「已处理」，紧接着
     * {@code loadWarnings()} 重新拉列表 —— 状态原样还是「待处理」。
     * 同一屏里前脚报成功、后脚显示没变，是"报告与事实不符"最直白的一条。
     * （同族的真实现见 {@code CounselorController.processWarning}。）
     *
     * <p>现在真的写 {@code academic_warning}，并且**回读一行再返回** ——
     * 响应里的 {@code status/handledBy/handleResult/handledAt} 都是数据库里读回来的值，
     * 不是本地拼的。写没写进去、写成了什么样，调用方看到的就是库里的样子。
     *
     * <p>取值口径：PC 端发的是 {@code {measures, remark}}（页面上的"帮扶措施"与
     * "沟通记录"），辅导员端/小程序发的是 {@code {handleResult, handledBy}}。
     * 两组 key 都收 —— 此前 {@code measures} 被静默丢弃，老师选的措施根本没落库，
     * 落库的只有那个默认字符串。{@code remark} 与措施合并进 {@code handle_result}
     * （该表没有单独放备注的列，两段都丢不得，只好并进同一列并保留标记）。
     *
     * <p>谁处理的取 {@code X-Username}（网关注入）。直连服务时该头可能为空，
     * 那就如实记 {@code null}，不编一个名字。
     *
     * <p><b>2026-09-30 更新："已处理"现在是稳的。</b>此前 {@code WarningScheduledTask}
     * 每 30 分钟增量扫一次，判重只看"该生有没有 status=0 的预警"
     * （{@code ScoreReadMapper} 里写死 {@code WHERE status = 0}），于是置成已处理
     * 就等于给该生开了绿灯 —— 只要他挂科门数仍 &gt; 0，下一轮就再插一条新的
     * （新 id、status=0），页面上同一个人挂着"已处理"和"待处理"两条。
     * 判重口径已改成「学生 + 挂科门数」且**不看 status**（见
     * {@code ScoreReadMapper.findExistingWarningKeys}），所以处理掉之后不会复活；
     * 只有门数真的变了才会再报一条 —— 那本来就该报。
     */
    @PostMapping("/warnings/{warningId}/process")
    public ApiResponse<Object> processWarning(@PathVariable Long warningId,
                                             @RequestBody(required = false) Map<String, Object> data,
                                             HttpServletRequest request) {
        Map<String, Object> body = data == null ? new HashMap<>() : data;
        // W12 资源归属闸：`warningId` 是**资源 id**，此前只查"这条预警存在吗"，
        // 不问"这条预警管的学生是不是我班上的" ⇒ 任何教师都能把别人班上学生的预警
        // 置成"已处理"并写进处理措施（学生还会收到一条"教师已处理你的预警"的通知）。
        // 归属口径：该预警的学生所挂班级的班主任必须是自己。
        if (!isAdmin(request)) {
            Long me = getUserIdFromGateway(request);
            if (me == null || academicWarningMapper.findByIdOwnedByTeacher(warningId, me) == null) {
                return forbidden("只能处理本人所带班级学生的预警");
            }
        }
        try {
            com.academic.common.entity.AcademicWarning warning = academicWarningMapper.selectById(warningId);
            if (warning == null) {
                return ApiResponse.notFound("预警不存在");
            }

            // 帮扶措施：PC 端叫 measures，辅导员端叫 handleResult
            String measures = firstNonBlank(body.get("handleResult"), body.get("measures"));
            String remark = firstNonBlank(body.get("remark"));
            String handleResult = joinHandleResult(measures, remark);
            String handledBy = truncate(firstNonBlank(body.get("handledBy"),
                    request.getHeader("X-Username")), 100);

            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            warning.setStatus(1);
            warning.setHandleResult(handleResult);
            warning.setHandledBy(handledBy);
            warning.setHandledAt(now);
            warning.setUpdatedBy(handledBy);
            warning.setUpdatedAt(now);
            academicWarningMapper.updateById(warning);

            // 回读：返回给前端的必须是库里的真实状态，而不是上面刚 set 的值
            com.academic.common.entity.AcademicWarning saved = academicWarningMapper.selectById(warningId);
            if (saved == null) {
                return ApiResponse.serverError("预警处理失败：写入后回读不到该条记录");
            }

            // 通知学生「你的预警已被处理」。刻意包 try/catch：处理本身已经落库成功，
            // 通知发不出去（学生关了开关、档案没绑账号）不该让教师看到 500 而重复点。
            try {
                String handleNote = saved.getHandleResult();
                studentNotifier.notifyStudent(saved.getStudentId(),
                        com.academic.common.notify.NotificationPreferences.Category.WARNING,
                        saved.getWarningLevel(),
                        "教师已处理你的预警",
                        "你此前的预警「" + saved.getTitle() + "」已被处理。"
                                + (handleNote == null || handleNote.isBlank() ? "" : "处理措施：" + handleNote),
                        saved.getId());
            } catch (Exception ne) {
                log.warn("预警 {} 的处理通知发送失败（处理本身已生效）: {}", warningId, ne.getMessage());
            }

            Map<String, Object> result = new HashMap<>();
            result.put("warningId", saved.getId());
            result.put("studentId", saved.getStudentId());
            result.put("studentName", saved.getStudentName());
            // 2026-10-02（W1.6）：同上一处，收敛到共用映射（status=2 现在是"已解除"）
            result.put("status", WarningStatus.text(saved.getStatus()));
            result.put("handledBy", saved.getHandledBy());
            result.put("handleResult", saved.getHandleResult());
            result.put("handledAt", saved.getHandledAt());
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("处理预警失败: warningId={}, {}", warningId, e.getMessage(), e);
            return ApiResponse.serverError("处理预警失败: " + e.getMessage());
        }
    }

    /**
     * 导入学生到本班。
     *
     * <p>⚠️ 原实现是个**桩**：只校验后缀和 10MB，然后返回
     * {@code {imported: 0, message: "文件已接收，请稍后查看导入结果"}} —— 一行库都不写。
     * 而教师端 {@code TeacherClassManagement.vue} 拿到成功响应就
     * {@code ElMessage.success('学生导入成功')}，所以这个按钮是当着老师的面报告了一件
     * 没发生的事。2026-09-30 改成真做。
     *
     * <p>它**只做一件事**：把已经存在于系统里的学生归入本班。班级成员的权威口径是
     * {@code student_profile.class_id}（本服务所有班级人数/名单/按班统计的预警都读这一列），
     * 所以这里改的就是它，并同步 {@code class_name} 与既有数据保持一致。
     * **不新建账号、不新建学生档案** —— 建账号是管理员那边的活
     * （{@code /admin/import/students}，要 BCrypt 口令、写 users + student_profile 两张表）；
     * 一个"往我的班里加人"的动作不该顺手在别处造出登录凭据。
     *
     * <p>逐行尽力而为：一行 = 一个单表 UPDATE，本身即原子，不需要跨表事务；
     * 失败的行走 {@code errors}，不连坐其余行。返回体与 {@code /admin/import/students}
     * 同口径（{@code total/success/skipped/errors}），多一个 {@code moved}
     * 表示"原本在别的班级、这次改过来了"—— 移出原班是学生的实际影响，得说出来。
     *
     * <p><b>2026-10-04（W12）补归属闸</b>：{@code classId} 是客户端传的查询参数，
     * 此前**一道校验都没有** —— 传别人班上的 {@code classId} 就能把学生**从别的班
     * 拽进/拽出**（这个端点改的就是 {@code student_profile.class_id}）。
     * 现在要求该班班主任是本人（或管理员），且 {@code teacherId} 也得是本人 ——
     * 后者是调用方自己声称的"我是谁"，不校验的话闸只挡得住一半。
     */
    @PostMapping("/students/import")
    public ApiResponse<Map<String, Object>> importStudents(@RequestParam("file") MultipartFile file,
                                                          @RequestParam("classId") Long classId,
                                                          @RequestParam("teacherId") Long teacherId,
                                                          HttpServletRequest request) {
        if (!isAdmin(request)) {
            Long me = getUserIdFromGateway(request);
            if (me == null || !me.equals(teacherId)) {
                return forbidden("teacherId 与登录身份不一致");
            }
            ApiResponse<Object> deny = denyUnlessMyClass(classId, request, "只能向本人所带的班级导入学生");
            if (deny != null) {
                return ApiResponse.error(403, deny.getMessage());
            }
        }
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> errors = new ArrayList<>();
        int successCount = 0, skipCount = 0, movedCount = 0;
        result.put("fileName", file == null ? null : file.getOriginalFilename());

        try {
            if (file == null || file.isEmpty()) {
                return ApiResponse.error("文件为空");
            }
            String fileName = file.getOriginalFilename();
            if (fileName == null || !fileName.matches("(?i).*\\.(xlsx|xls)$")) {
                return ApiResponse.error("仅支持 .xlsx 或 .xls 格式的 Excel 文件");
            }
            // 上限与 application.yml 的 spring.servlet.multipart 一致；容器先拦的话
            // 抛的是 MaxUploadSizeExceededException，这句"不能超过 10MB"就成了谎话。
            if (file.getSize() > MAX_IMPORT_BYTES) {
                return ApiResponse.error("文件大小不能超过 10MB");
            }

            ClassInfo clazz = classInfoMapper.selectById(classId);
            if (clazz == null) {
                return ApiResponse.error("班级不存在：" + classId);
            }
            // 只有本班的任课教师能往里导人。按 teacherId 查而不是往前端要一个"我是不是"的
            // 布尔值 —— 后者谁都能传 true。
            if (clazz.getTeacherId() == null || !clazz.getTeacherId().equals(teacherId)) {
                return ApiResponse.error("您不是该班级的教师，不能往这个班导学生");
            }
            result.put("className", clazz.getName());

            // W22：`new XSSFWorkbook(...)` 读不了 .xls，而上面的扩展名闸写着"仅支持 .xlsx 或 .xls"。
            // WorkbookFactory 让那句承诺变成真的（真 .xls 走 HSSF）。
            // 解析失败的翻译放在这一层，理由同 /scores/import。
            Workbook opened;
            try {
                opened = WorkbookFactory.create(file.getInputStream());
            } catch (Exception e) {
                log.error("Parse class students excel failed: {}", file.getOriginalFilename(), e);
                String friendly = ExcelUploadErrors.describe(e);
                return ApiResponse.error(friendly != null ? friendly : ExcelUploadErrors.UNREADABLE);
            }
            try (Workbook workbook = opened) {
                Sheet sheet = workbook.getSheetAt(0);
                if (sheet == null || sheet.getLastRowNum() < 1) {
                    return ApiResponse.error("表里没有数据行（第 1 行应当是表头，数据从第 2 行开始）");
                }
                Integer sidCol = findColumn(sheet.getRow(0), "学号");
                if (sidCol == null) {
                    return ApiResponse.error("表头里找不到「学号」列");
                }

                for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;
                    int rowNum = r + 1;   // 报给老师看的行号 = 他在 Excel 里看到的行号
                    try {
                        String studentId = cellText(row.getCell(sidCol));
                        if (studentId == null) continue;   // 整行空
                        if (studentId.length() > 20) {
                            errors.add(rowError(rowNum, "学号超过 20 位"));
                            skipCount++; continue;
                        }

                        Long profileId = classInfoMapper.findProfileIdByStudentId(studentId);
                        if (profileId == null) {
                            errors.add(rowError(rowNum, "学号不存在：" + studentId
                                    + "（这里只把已有学生归入本班，不新建账号）"));
                            skipCount++; continue;
                        }

                        Long currentClassId = classInfoMapper.findClassIdOfProfile(profileId);
                        if (classId.equals(currentClassId)) {
                            errors.add(rowError(rowNum, studentId + " 已在本班"));
                            skipCount++; continue;
                        }

                        classInfoMapper.assignProfileToClass(profileId, classId, clazz.getName());
                        if (currentClassId != null) movedCount++;   // 从别的班挪过来的
                        successCount++;
                    } catch (Exception e) {
                        log.warn("导入学生第 {} 行失败", rowNum, e);
                        errors.add(rowError(rowNum, briefMessage(e)));
                        skipCount++;
                    }
                }
            }

            result.put("total", successCount + skipCount);
            result.put("success", successCount);
            result.put("skipped", skipCount);
            result.put("moved", movedCount);
            result.put("errors", errors);
            log.info("Import students to class {} finished: total={}, success={}, skip={}, moved={}",
                    classId, successCount + skipCount, successCount, skipCount, movedCount);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Import students failed", e);
            // W22：文案与 /scores/import 逐字一致。
            String friendly = ExcelUploadErrors.describe(e);
            return ApiResponse.error(friendly != null ? friendly
                    : "导入失败：服务器处理这个文件时出错，请稍后重试或联系管理员");
        }
    }

    /** 与 application.yml 的 spring.servlet.multipart.max-file-size 保持一致。 */
    private static final long MAX_IMPORT_BYTES = 10L * 1024 * 1024;

    /**
     * 读单元格为文本。Excel 里的学号常常是"数字格式"，POI 读出来是 double，
     * 直接 toString 会得到 {@code 2023020616.0} —— 整数值一律按 long 输出。
     * （管理员端那份 {@code AdminController.cellText} 同一套规则；两个端点做的事不同，
     * 共同部分只有这十几行读单元格，就没为一个工具方法去动 academic-common。）
     */
    private static String cellText(Cell cell) {
        if (cell == null) return null;
        try {
            CellType type = cell.getCellType();
            if (type == CellType.FORMULA) type = cell.getCachedFormulaResultType();
            if (type == CellType.NUMERIC) {
                double d = cell.getNumericCellValue();
                if (d == Math.rint(d) && Math.abs(d) < 1e15) return String.valueOf((long) d);
                return String.valueOf(d);
            }
            if (type == CellType.BOOLEAN) return String.valueOf(cell.getBooleanCellValue());
            if (type == CellType.BLANK) return null;
            String s = cell.getStringCellValue();
            return s == null || s.trim().isEmpty() ? null : s.trim();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 按**表头文字**找列。管理员模板里是「学号(必填)」、导出的文件里是「学号」，
     * 按"包含"匹配两边都能认，不必按列序（那会在两个来源之间整体错位）。
     */
    private static Integer findColumn(Row header, String keyword) {
        if (header == null || header.getLastCellNum() < 0) return null;
        for (int c = header.getFirstCellNum(); c < header.getLastCellNum(); c++) {
            String h = cellText(header.getCell(c));
            if (h != null && h.replace(" ", "").contains(keyword)) return c;
        }
        return null;
    }

    /** 用 HashMap 而不是 Map.of —— 后者的值不允许为 null。 */
    private static Map<String, Object> rowError(int row, String reason) {
        Map<String, Object> m = new HashMap<>();
        m.put("row", row);
        m.put("reason", reason);
        return m;
    }

    /**
     * 只取最内层 cause。直接把 e.getMessage() 放出去会是一整段 MyBatis 包装的
     * {@code "### Error updating database. Cause: ... ### SQL: UPDATE ..."}，
     * 既没有解释力又把实现细节漏给前端；完整堆栈仍在服务端日志里。
     */
    private static String briefMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String msg = root.getMessage();
        return (msg == null || msg.trim().isEmpty()) ? root.getClass().getSimpleName() : msg.trim();
    }

    /**
     * 成绩预检：本课程有多少名学生低于及格线。
     *
     * <p><b>W24（2026-10-05）：这个端点从前在说谎。</b>它回的是
     * {@code "已为" + N + "名学生生成预警"}，而方法体里<b>一行都没落库</b> ——
     * 只把成绩流了一遍、数了数低于 {@link ScoreCaliber#PASS_LINE} 的人数就返回。
     * 同一个响应里那个 {@code warningsTriggered} 键名也在重复这个说法。
     * 换句话说：**谁调它，谁就会得到一句"预警已生成"的假确认**（HTTP 200 + success）。
     *
     * <p>路径与 {@code teacherAPI.triggerWarnings} 这个名字都还叫"触发预警"，
     * 但**真正的预警生成不在这个服务里**：{@code warning-service} 的
     * {@code POST /warnings/scan}（{@code WarningScheduledTask.triggerNow()}）与每日 02:00
     * 的定时任务才是出口，且带"先解除后生成 + 按学生/挂科门数判重"的完整规则。
     * 这里**刻意不去**转调它：本端点是**按课程**的，而那个扫描是**全库**的，
     * 从一门课的按钮触发全库重扫，语义上就是错的；况且扫描会连带发通知。
     *
     * <p>所以本轮的修法是**把说法改成事实**（而不是"让承诺成真"）：
     * 响应里不再有 {@code warningsTriggered}，改成一个不冒充动作的
     * {@code studentsBelowPassLine}，{@code message} 也明说"只做统计、不生成预警记录"。
     * 该键可安全移除 —— PC 与小程序**两边的封装都不是调用方**（全仓零调用点）。
     */
    @PostMapping("/scores/warnings")
    public ApiResponse<Object> inspectScoresForWarnings(@RequestParam Long course_id, HttpServletRequest request) {
        ApiResponse<Object> deny = denyUnlessMyCourse(course_id, request, "只能对本人所授课程触发预警");
        if (deny != null) {
            return deny;
        }
        try {
            List<ScoreRecord> scores = latestPerStudent(scoreRecordService.getScoresByCourseId(course_id));
            long belowPassLine = scores.stream()
                .map(ScoreRecord::getScore)
                .filter(Objects::nonNull)              // 未录入的不算 0 分
                .filter(score -> score < ScoreCaliber.PASS_LINE)
                .count();
            Map<String, Object> result = new HashMap<>();
            result.put("courseId", course_id);
            result.put("totalStudents", scores.size());
            result.put("passLine", ScoreCaliber.PASS_LINE);
            // 不叫 warningsTriggered：这个名字断言"预警被触发了"，而事实是只数了个数。
            result.put("studentsBelowPassLine", belowPassLine);
            result.put("message", "本课程有 " + belowPassLine + " 名学生成绩低于 "
                    + ScoreCaliber.PASS_LINE + " 分；本接口只做统计，不生成预警记录");
            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取学分预测
     */
    @GetMapping("/students/{studentId}/credit-prediction")
    public ApiResponse<Object> getCreditPrediction(@PathVariable String studentId, HttpServletRequest request) {
        // W18（2026-10-04）：路径参数是 student_profile.id（与 scores.student_id 同一套），
        // **由客户端给**。此前无闸，且本类 isSelf 的注释还把它辩护成"按设计就是教师看本班学生，
        // 属合法的跨用户读，不能拦" —— 但那里没有一行代码验证过这个 studentId 真的在本人班上。
        // 于是任意教师换个数字就能读到任意学生的姓名 / 已修学分 / 挂科门数。
        Long sid;
        try {
            sid = Long.parseLong(studentId);
        } catch (NumberFormatException e) {
            return ApiResponse.badRequest("学生 id 非法: " + studentId);
        }
        ApiResponse<Object> deny = denyUnlessMyStudent(sid, request, "只能查看本人所带班级的学生");
        if (deny != null) {
            return deny;
        }
        try {
            // 学分口径唯一来源：ScoreCaliber.ONE_STUDENT_STATS。
            // 学生端的"已修学分"用的是同一段 SQL，所以教师这里看到的数字
            // 和学生自己首页上的必然一致 —— 早先这里是 `成绩条数 × 3.0`，
            // 假设"每门课 3 学分"，算出的 48 学分与学生端是两回事，
            // 一被追问"你怎么知道每门课 3 学分"就塌了。
            Map<String, Object> stats = scoreRecordMapper.selectStudentStats(sid);
            int completedCredits = intValue(stats == null ? null : stats.get("completedCredits"));
            int totalCourses = intValue(stats == null ? null : stats.get("totalCourses"));
            int failedCourses = intValue(stats == null ? null : stats.get("failedCourses"));
            int required = ScoreCaliber.GRADUATION_CREDITS;

            Map<String, Object> result = new HashMap<>();
            result.put("studentId", sid);
            result.put("studentName", scoreRecordMapper.selectStudentName(sid));
            result.put("completedCredits", completedCredits);
            result.put("requiredCredits", required);
            // 剩余学分是"毕业要求 − 已修"，纯减法，不是预测；
            // 也不再给 predictedCredit —— 那需要修读计划/培养方案数据，库里没有，
            // 编一个数字出来只会让答辩时多一个回答不了的问题。
            result.put("remainingCredits", Math.max(required - completedCredits, 0));
            result.put("totalCourses", totalCourses);
            result.put("failedCourses", failedCourses);
            result.put("progressRate", required == 0 ? 0
                    : round2((double) completedCredits / required));
            // trend 只由**已落库的事实**推出：有挂科就"需关注"，没有就"良好"。
            // 不掺入任何外推。
            result.put("trend", failedCourses > 0 ? "需关注" : "良好");
            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 分析学生成绩（单门课视角）。
     *
     * <p>返回的每一项都必须是**库里查得到、或由查到的数直接算出来的**：
     * 名次是这门课里按分数排出来的真实名次，`averageScore` 是这门课的真实平均分，
     * `failedCount` 是该生自己的挂科门数（走 ScoreCaliber 口径）。
     *
     * <p>刻意<b>不返回</b> `predictedScore`（预测成绩）：没有修读计划或历史趋势模型，
     * 预测只能是编的。前端原来那一格改成了"该生在本课程的名次"。
     */
    @GetMapping("/scores/student/analyze")
    public ApiResponse<Object> analyzeStudentScore(@RequestParam Long student_id, @RequestParam Long course_id,
                                                   HttpServletRequest request) {
        // W18（2026-10-04）：course_id 与 student_id 都由客户端给，此前一条闸都没有 ——
        // 任意教师换个 course_id 就能读到别的教师课程里任意学生的成绩、名次、班级平均。
        // 闸口放在**课程**上：这门课必须是本人所授（判据与 W12 的课程族完全一致）。
        // 不额外要求"该生在我班上"：只授课、不是班主任的教师（活库 18/19/22/28）本就该
        // 看得到自己所授课程里的学生 —— 那是本人课程的数据，多一道班级校验反而是误伤。
        ApiResponse<Object> deny = denyUnlessMyCourse(course_id, request, "只能查看本人所授课程的成绩");
        if (deny != null) {
            return deny;
        }
        try {
            // 一人一行：司明在高等数学下有 3 个批次，取最新的那次（第 5 学期 56.5），
            // 否则"当前成绩"会随行序随机命中第 3 学期的 87 分 —— 同一个接口两次调用
            // 给出两个分数，且与预警里的 56.5 对不上。
            List<ScoreRecord> rows = latestPerStudent(scoreRecordService.getScoresByCourseId(course_id));

            Double studentScore = null;
            String studentName = null;
            for (ScoreRecord row : rows) {
                if (student_id.equals(row.getStudentId())) {
                    studentScore = row.getScore();
                    studentName = row.getStudentName();
                    break;
                }
            }
            if (studentScore == null) {
                // 该生在这门课没有成绩（或期末未录入），不当作 0 分 —— 0 分会让
                // "预警状态"直接跳到"严重"，而事实是"还没录"。
                return ApiResponse.error(404, "该生在此课程暂无成绩记录");
            }

            // 名次：同课程内按分数降序，并列同分同名次（标准竞赛排名法）。
            int better = 0;
            int total = 0;
            double sum = 0;
            for (ScoreRecord row : rows) {
                Double s = row.getScore();
                if (s == null) continue;
                total++;
                sum += s;
                if (s > studentScore) better++;
            }
            double courseAvg = total == 0 ? 0 : sum / total;

            Course course = courseService.getById(course_id);
            int failedCount = intValue(scoreRecordMapper.selectFailedCourseCount(student_id));

            Map<String, Object> analysis = new HashMap<>();
            analysis.put("studentId", student_id);
            analysis.put("studentName", studentName);
            analysis.put("courseId", course_id);
            analysis.put("courseName", course == null ? null : course.getName());
            analysis.put("currentScore", studentScore);
            analysis.put("averageScore", round2(courseAvg));     // 本课程平均分
            analysis.put("difference", round2(studentScore - courseAvg));
            analysis.put("rank", better + 1);
            analysis.put("totalStudents", total);
            analysis.put("failedCount", failedCount);
            analysis.put("level", ScoreSyncSupport.scoreToLevel(studentScore));
            analysis.put("warningLevel", warningLevelText(studentScore));
            analysis.put("comment", buildStudentComment(studentScore, courseAvg, better + 1, total));
            return ApiResponse.success(analysis);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /** 分数 → 预警等级文案，与 `/warnings/{teacherId}` 的"轻度/中度/严重"同一套词汇。 */
    private static String warningLevelText(double score) {
        if (score >= 70) return "正常";
        if (score >= ScoreCaliber.PASS_LINE) return "轻度";
        if (score >= 50) return "中度";
        return "严重";
    }

    /** 评语只复述上面几个真实数字，不引入任何新判断。 */
    private static String buildStudentComment(double score, double courseAvg, int rank, int total) {
        String position = "本课程排名 " + rank + "/" + total;
        if (score >= 90) {
            return "成绩为 " + round2(score) + " 分，课程平均 " + round2(courseAvg)
                    + " 分，" + position + "。处于优秀区间，建议保持并参与课堂展示。";
        }
        if (score >= ScoreCaliber.PASS_LINE) {
            String vs = score >= courseAvg ? "高于" : "低于";
            return "成绩为 " + round2(score) + " 分，课程平均 " + round2(courseAvg)
                    + " 分，" + position + "，" + vs + "课程平均 "
                    + round2(Math.abs(score - courseAvg)) + " 分。已及格，建议巩固易错知识点。";
        }
        return "成绩为 " + round2(score) + " 分，未达 " + ScoreCaliber.PASS_LINE
                + " 分及格线，课程平均 " + round2(courseAvg) + " 分，" + position
                + "。建议安排单独辅导并纳入下一次预警跟踪。";
    }

    /**
     * 获取班级管理申请
     */
    @GetMapping("/class-management/requests")
    public ApiResponse<Object> getMyClassManagementRequests(@RequestParam Long teacherId, HttpServletRequest request) {
        // W11 读侧归属闸：users.id。
        if (!isSelf(teacherId, request)) {
            return forbidden("只能查看本人的班级管理申请");
        }
        try {
            List<Object> requests = (List<Object>) (List<?>) applicationService.getApplicationsByTeacherId(teacherId);
            return ApiResponse.success(requests);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取待办事项
     */
    @GetMapping("/todos/{teacherId}")
    public ApiResponse<Object> getTodos(@PathVariable String teacherId, HttpServletRequest request) {
        // W11 读侧归属闸：users.id 空间。
        if (!isSelf(Long.parseLong(teacherId), request)) {
            return forbidden("只能查看本人的待办");
        }
        try {
            Long tid = Long.parseLong(teacherId);
            List<Map<String, Object>> todos = new ArrayList<>();
            // 1. 检查待回复反馈。
            //    原来这里是 `f instanceof Map` —— getFeedbacks 返回的是 Feedback 实体，
            //    这个条件恒为假，待办数恒 0；后面两条也同样恒假，
            //    于是 /todos 无论谁来问都只吐下面那条写死的预警项。
            List<Feedback> feedbacks = feedbackService.getFeedbacks(tid, null);
            long pendingFeedbacks = feedbacks.stream()
                .filter(f -> f.getStatus() != null && f.getStatus() == 0)
                .count();
            if (pendingFeedbacks > 0) {
                Map<String, Object> todo = new HashMap<>();
                todo.put("id", 1L);
                todo.put("title", "未回复的反馈");
                todo.put("count", pendingFeedbacks);
                todo.put("type", "feedback");
                todos.add(todo);
            }
            // 2. 检查待审核的班级管理申请（status = 0 为待审核）
            List<TeacherClassApplication> requests = applicationService.getApplicationsByTeacherId(tid);
            long pendingRequests = requests.stream()
                .filter(r -> r.getStatus() != null && r.getStatus() == 0)
                .count();
            if (pendingRequests > 0) {
                Map<String, Object> todo = new HashMap<>();
                todo.put("id", 2L);
                todo.put("title", "待审核的班级申请");
                todo.put("count", pendingRequests);
                todo.put("type", "class_request");
                todos.add(todo);
            }
            // 3. 该教师名下班级里"待处理"的预警（academic_warning.status = 0）。
            //    改掉原来写死的 count=0：数字必须是查出来的，否则待办卡片
            //    永远显示 0，而预警页上明明挂着 4 条待处理。
            long pendingWarnings = 0;
            for (Map<String, Object> clazz : classInfoService.getClassesByTeacherId(tid)) {
                Object classId = clazz.get("id");
                if (!(classId instanceof Number)) continue;
                for (com.academic.common.entity.AcademicWarning w
                        : academicWarningMapper.findByClassId(((Number) classId).longValue())) {
                    if (w.getStatus() == null || w.getStatus() == 0) pendingWarnings++;
                }
            }
            if (pendingWarnings > 0) {
                Map<String, Object> todo3 = new HashMap<>();
                todo3.put("id", 3L);
                todo3.put("title", "待处理的学生预警");
                todo3.put("count", pendingWarnings);
                todo3.put("type", "warning");
                todos.add(todo3);
            }
            return ApiResponse.success(todos);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取课程成绩分布
     */
    @GetMapping("/courses/{courseId}/distribution")
    public ApiResponse<Object> getCourseScoreDistribution(@PathVariable Long courseId, HttpServletRequest request) {
        ApiResponse<Object> deny = denyUnlessMyCourse(courseId, request, "只能查看本人所授课程的分布");
        if (deny != null) {
            return deny;
        }
        try {
            int[] distribution = new int[5]; // 90+, 80-89, 70-79, 60-69, <60
            for (ScoreRecord row : latestPerStudent(scoreRecordService.getScoresByCourseId(courseId))) {
                Double s = row.getScore();
                if (s == null) continue;       // 未录入不属于任何一档
                if (s >= 90) distribution[0]++;
                else if (s >= 80) distribution[1]++;
                else if (s >= 70) distribution[2]++;
                else if (s >= 60) distribution[3]++;
                else distribution[4]++;
            }

            // 顺手补一个按标签索引的 Map：前端柱状图普遍要 {label: count} 的形态，
            // 让每个页面各自把 int[] 配 labels 数组拼一遍是把同一个易错点复制 N 份。
            Map<String, Object> byLabel = new java.util.LinkedHashMap<>();
            String[] labels = {"90+", "80-89", "70-79", "60-69", "<60"};
            for (int i = 0; i < labels.length; i++) {
                byLabel.put(labels[i], distribution[i]);
            }

            Map<String, Object> result = Map.of(
                "courseId", courseId,
                "distribution", distribution,
                "byLabel", byLabel,
                "labels", labels
            );

            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取课程成绩异常
     */
    @GetMapping("/courses/{courseId}/anomaly")
    public ApiResponse<Object> getCourseAnomalies(@PathVariable Long courseId, @RequestParam(required = false, defaultValue = "60") Integer threshold,
                                                 HttpServletRequest request) {
        // 实测泄漏最直的一条：换个课程 id 就拿到别人课上 24 行「姓名 + 分数 + 不及格评语」。
        ApiResponse<Object> deny = denyUnlessMyCourse(courseId, request, "只能查看本人所授课程的低分名单");
        if (deny != null) {
            return deny;
        }
        try {
            List<ScoreRecord> anomalies = new ArrayList<>();
            for (ScoreRecord row : latestPerStudent(scoreRecordService.getScoresByCourseId(courseId))) {
                Double s = row.getScore();
                if (s != null && s < threshold) {
                    anomalies.add(row);
                }
            }
            return ApiResponse.success(anomalies);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取课程学生列表（一人一行，取该生在这门课最新一次录入）。
     *
     * <p>返回 {@code {total, page, size, list}} 而不是裸数组：方法签名上一直
     * 声明着 {@code page}/{@code size}，但早先两个参数被忽略、整表一次性返回 ——
     * 前端按"有分页"来写，翻页却永远看到同一批数据。这里把分页真正实现掉。
     */
    @GetMapping("/courses/{courseId}/students")
    public ApiResponse<Object> getCourseStudents(@PathVariable Long courseId,
                                                 @RequestParam(required = false, defaultValue = "1") Integer page,
                                                 @RequestParam(required = false, defaultValue = "20") Integer size,
                                                 HttpServletRequest request) {
        ApiResponse<Object> deny = denyUnlessMyCourse(courseId, request, "只能查看本人所授课程的学生名单");
        if (deny != null) {
            return deny;
        }
        try {
            List<ScoreRecord> all = latestPerStudent(scoreRecordService.getScoresByCourseId(courseId));
            int p = page == null || page < 1 ? 1 : page;
            int s = size == null || size < 1 ? 20 : size;
            int from = Math.min((p - 1) * s, all.size());
            int to = Math.min(from + s, all.size());

            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("total", all.size());
            result.put("page", p);
            result.put("size", s);
            result.put("list", new ArrayList<>(all.subList(from, to)));
            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取课程推荐（教学建议）。
     *
     * <p><b>W15</b>：{@code limit} 原来声明了却从不生效 —— 无论传什么，返回的条数只由数据决定。
     * 现在它真的是"最多回几条"的上限。
     *
     * <p><b>W21</b>：两条建议现在**都**由数据触发 —— 「加强基础」看不及格人数、「增加实践」
     * 看优秀（≥{@link ScoreCaliber#EXCELLENT_LINE}）人数。此前第二条是**无条件**拼上去的，
     * 与课程数据无关，全班 90+ 或课程与编程毫无关系时也照样建议"增加编程实践"。
     * 因此本端点在"既无不及格、也无优秀"时**返回空数组**，属预期行为。
     */
    @GetMapping("/courses/{courseId}/recommendations")
    public ApiResponse<Object> getCourseRecommendations(@PathVariable Long courseId, @RequestParam(required = false, defaultValue = "5") Integer limit,
                                                        HttpServletRequest request) {
        ApiResponse<Object> deny = denyUnlessMyCourse(courseId, request, "只能查看本人所授课程的推荐");
        if (deny != null) {
            return deny;
        }
        try {
            List<Map<String, Object>> recommendations = new ArrayList<>();
            // 根据课程成绩分布推荐学习建议（一人一行，避免同一名学生被数多次）
            List<ScoreRecord> scores = latestPerStudent(scoreRecordService.getScoresByCourseId(courseId));
            if (scores.isEmpty()) {
                return ApiResponse.success(recommendations);
            }
            // 统计低分/优秀学生，**各**给一条由数据触发的推荐（W21）
            int lowScoreCount = 0;
            int excellentCount = 0;
            for (ScoreRecord s : scores) {
                // 未录入不等于不及格：跳过，不当作 0 分
                if (s.getScore() == null) continue;
                if (s.getScore() < ScoreCaliber.PASS_LINE) lowScoreCount++;
                else if (s.getScore() >= ScoreCaliber.EXCELLENT_LINE) excellentCount++;
            }
            if (lowScoreCount > 0) {
                Map<String, Object> rec = new HashMap<>();
                rec.put("id", 1L);
                rec.put("title", "加强基础知识点教学");
                rec.put("description", "该课程有" + lowScoreCount + "名学生成绩不及格，建议针对薄弱知识点加强教学");
                rec.put("priority", "high");
                recommendations.add(rec);
            }
            // W21：这条原来是**无条件**拼上去的 —— 与课程数据无关，哪怕全班都在 90 分以上
            // （甚至这门课是《中国传统文化》）也照样建议"增加编程实践"，与"恒 0 的 total"同族。
            // 现改为**由数据触发**，并把真实人数写进文案：没有优秀学生的课不该收到"加实践"的建议。
            if (excellentCount > 0) {
                Map<String, Object> rec2 = new HashMap<>();
                rec2.put("id", 2L);
                rec2.put("title", "增加实践环节");
                rec2.put("description", "该课程有" + excellentCount + "名学生成绩优秀，建议增加实践环节与拓展内容，进一步提升动手能力");
                rec2.put("priority", "medium");
                recommendations.add(rec2);
            }
            // W15：让 `limit` 真的生效（此前它只是个摆设）。
            if (limit != null && limit > 0 && recommendations.size() > limit) {
                recommendations = new ArrayList<>(recommendations.subList(0, limit));
            }
            return ApiResponse.success(recommendations);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取成绩修改审计日志（score_audit_log 表）
     */
    @GetMapping("/scores/audit/{teacherId}")
    public ApiResponse<Object> getAuditLogs(@PathVariable String teacherId, @RequestParam(required = false, defaultValue = "1") Integer page, @RequestParam(required = false, defaultValue = "20") Integer size,
                                            HttpServletRequest request) {
        // W11 读侧归属闸：users.id 空间。改分审计是敏感面，换个数字就读到别人的改动史。
        if (!isSelf(Long.parseLong(teacherId), request)) {
            return forbidden("只能查看本人的改分记录");
        }
        try {
            Long tid = Long.parseLong(teacherId);
            List<ScoreAuditLog> list = scoreAuditLogService.getByTeacherId(tid);
            Map<String, Object> result = new HashMap<>();
            result.put("total", list.size());
            result.put("page", page);
            result.put("size", size);
            result.put("list", list);
            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取成绩日志（已废弃，请用 getAuditLogs）
     */
    @GetMapping("/scores/logs/{teacherId}")
    @Deprecated
    public ApiResponse<Object> getScoreLogs(@PathVariable String teacherId, @RequestParam(required = false, defaultValue = "1") Integer page, @RequestParam(required = false, defaultValue = "20") Integer size,
                                            HttpServletRequest request) {
        return getAuditLogs(teacherId, page, size, request);
    }

    /**
     * 获取反馈列表。
     *
     * <p><b>W15</b>：原来签名上有 {@code page} / {@code size} 两个参数，但方法体从头到尾
     * 没引用过它们 —— 调用方以为按页取，实际拿到的永远是**全量**。两个参数都已删掉：
     * 小程序（唯一的真实调用方 {@code teacherAPI.getFeedbackList(userId)}）本来就不传，
     * PC 的 {@code getFeedbackList}/{@code getFeedback} 两个绑定零调用方。签名现在与行为一致。
     */
    @GetMapping("/feedbacks/{teacherId}/list")
    public ApiResponse<Object> getFeedbackList(@PathVariable String teacherId, @RequestParam(required = false, defaultValue = "") String category,
                                               HttpServletRequest request) {
        // W11 读侧归属闸：users.id 空间。
        if (!isSelf(Long.parseLong(teacherId), request)) {
            return forbidden("只能查看本人的反馈");
        }
        try {
            Long tid = Long.parseLong(teacherId);
            List<Object> feedbacks = (List<Object>) (List<?>) feedbackService.getFeedbacks(tid, category.isEmpty() ? null : category);
            return ApiResponse.success(feedbacks);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 回复反馈
     */
    @PostMapping("/feedbacks/{feedbackId}/reply")
    public ApiResponse<Object> replyFeedback(@PathVariable Long feedbackId, @RequestBody Map<String, Object> data,
                                             HttpServletRequest request) {
        // W12 资源归属闸：feedback.teacher_id 是 users.id（活库那两行都是 29）。
        // 此前换个 feedbackId 就能**以别人的名义回复学生反馈** —— 而读侧
        // `/feedbacks/{teacherId}/list` 在 W11 已经收口了，写侧这条漏在外面。
        if (!isAdmin(request)) {
            Long me = getUserIdFromGateway(request);
            Feedback feedback = feedbackService.getFeedbackById(feedbackId);
            if (me == null || feedback == null || feedback.getTeacherId() == null
                    || !feedback.getTeacherId().equals(me)) {
                return forbidden("只能回复本人的反馈");
            }
        }
        try {
            String reply = (String) data.get("reply");
            feedbackService.replyFeedback(feedbackId, reply);
            return ApiResponse.success("回复成功");
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 分析成绩
     */
    @GetMapping("/scores/analyze")
    public ApiResponse<Object> analyzeScores(@RequestParam Long course_id, HttpServletRequest request) {
        ApiResponse<Object> deny = denyUnlessMyCourse(course_id, request, "只能分析本人所授课程");
        if (deny != null) {
            return deny;
        }
        try {
            return ApiResponse.success(buildCourseAnalysis(course_id));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 单门课程的成绩聚合（`/analysis` 与 `/scores/analyze` 共用的唯一实现）。
     *
     * <p>这两个接口问的是同一件事，历史上各写了一份：一份只产 6 个字段、另一份产 5 个，
     * 而且两份都把 {@code ScoreRecord} 当成 {@code Map} 强转，一调就
     * {@code ClassCastException}。留一份实现，两个入口共用 —— 否则同一位老师
     * 从两个按钮点进去会看到两个不同的平均分。
     *
     * <p>口径：数据源是 {@code score_record}（教师自己的录入暂存表），
     * 并先用 {@link #latestPerStudent} 收敛成"一人一行／取学期最大的那次录入"——
     * 与学生端和预警引擎"每门课取期末已录入的最新一次"是同一套收敛方式，
     * 所以这里的班额、及格人数与预警里的人数不会各说各话。
     * 唯一不复用 {@link ScoreCaliber} 的地方是它要求 {@code scores} 表的
     * {@code final_score IS NOT NULL}（"期末未录入"整行排除），而教师看的是
     * 自己录进来的分数本身，期末/平时还没填也要能统计。
     *
     * <p>{@code score} 为 NULL 的行（未录入）不计入任何一档，也**不当作 0 分**：
     * 当成 0 分会凭空多出一批"不及格"，与前端"待录入"的展示口径冲突。
     *
     * <p>{@code passRate} / {@code excellentRate} 是 <b>0~1 的小数</b>（与
     * {@code /classes/{classId}/analysis} 一致），不是百分数；标准差是总体标准差
     * （分母为 n，与 {@code /scores/anomalies} 的 2σ 判定同一套）。
     */
    private Map<String, Object> buildCourseAnalysis(Long courseId) {
        // 先去重再统计：一人一行，班额/平均分/标准差才与预警口径一致
        List<ScoreRecord> rows = latestPerStudent(scoreRecordService.getScoresByCourseId(courseId));
        Course course = courseService.getById(courseId);

        List<Double> scores = new ArrayList<>();
        int[] dist = new int[5]; // 90+ / 80-89 / 70-79 / 60-69 / <60
        int highCount = 0;       // >= 80
        int mediumCount = 0;     // 60 ~ 79
        int lowCount = 0;        // < 60
        for (ScoreRecord row : rows) {
            Double s = row.getScore();
            if (s == null) {
                continue;
            }
            scores.add(s);
            if (s >= 90) dist[0]++;
            else if (s >= 80) dist[1]++;
            else if (s >= 70) dist[2]++;
            else if (s >= 60) dist[3]++;
            else dist[4]++;
            if (s >= 80) highCount++;
            else if (s >= 60) mediumCount++;
            else lowCount++;
        }

        int graded = scores.size();
        double sum = 0;
        double max = 0;
        double min = 0;
        for (int i = 0; i < graded; i++) {
            double s = scores.get(i);
            sum += s;
            if (i == 0 || s > max) max = s;
            if (i == 0 || s < min) min = s;
        }
        double avg = graded == 0 ? 0 : sum / graded;
        double variance = 0;
        for (double s : scores) {
            variance += (s - avg) * (s - avg);
        }
        double stdDev = graded == 0 ? 0 : Math.sqrt(variance / graded);

        int passCount = 0;
        int excellentCount = 0;
        for (double s : scores) {
            if (s >= ScoreCaliber.PASS_LINE) passCount++;
            if (s >= 90) excellentCount++;
        }

        Map<String, Object> distribution = new java.util.LinkedHashMap<>();
        distribution.put("90+", dist[0]);
        distribution.put("80-89", dist[1]);
        distribution.put("70-79", dist[2]);
        distribution.put("60-69", dist[3]);
        distribution.put("<60", dist[4]);

        Map<String, Object> analysis = new java.util.LinkedHashMap<>();
        analysis.put("courseId", courseId);
        analysis.put("courseName", course == null ? null : course.getName());
        // totalStudents 是**去重后的学生人数**（一人一行），未录入的行也占一个名额；
        // gradedCount 才是真正有分数、参与统计的人数。柱状图的分母用前者。
        analysis.put("totalStudents", rows.size());
        analysis.put("gradedCount", graded);
        analysis.put("averageScore", round2(avg));
        analysis.put("maxScore", round2(max));
        analysis.put("minScore", round2(min));
        analysis.put("standardDeviation", round2(stdDev));
        analysis.put("passRate", graded == 0 ? 0 : round2((double) passCount / graded));
        analysis.put("excellentRate", graded == 0 ? 0 : round2((double) excellentCount / graded));
        analysis.put("failedCount", graded - passCount);
        analysis.put("highScoreCount", highCount);
        analysis.put("mediumScoreCount", mediumCount);
        analysis.put("lowScoreCount", lowCount);
        analysis.put("scoreDistribution", distribution);
        return analysis;
    }

    /** 保留两位小数。给前端直接 {@code toFixed(2)} 之外留一份服务端口径，两边不会漂。 */
    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * 同一名学生的一门课只保留**最新一次**录入（学期号最大；学期为空视为最早，
     * 并列时取 id 大的那个）。
     *
     * <p>统计类接口必须先去重，否则同一个人的多个批次会被重复计入：
     * 司明在高等数学下有三行（第 3/4/5 学期，87 → 71.5 → 56.5），
     * 不去重的话——课程平均分把他算三遍、标准差被这三行撑大、
     * "低于 60 分的名单"里他出现三次、班额显示 26 人（实际 24 人）。
     * 同样的数字在预警引擎那边（{@link ScoreCaliber}：每门课取期末已录入的
     * 最新一次）只算一次，两处必然对不上。
     *
     * <p>列表明细接口（{@code /scores}、{@code /scores/by-class}、{@code /enrollments}）
     * 刻意**不**去重：那是"录入了哪些批次"的台账，每一行都要能单独改分/删分。
     * 只有统计才收敛到一人一行。
     *
     * <p>键带上 courseId 是防御性的：万一将来有人把多门课的行一起传进来，
     * 不会把"同一学生在另一门课的最新成绩"当成这门课的。
     */
    private static List<ScoreRecord> latestPerStudent(List<ScoreRecord> rows) {
        Map<String, ScoreRecord> best = new java.util.LinkedHashMap<>();
        for (ScoreRecord row : rows) {
            if (row.getStudentId() == null) continue;
            String key = row.getCourseId() + "#" + row.getStudentId();
            ScoreRecord kept = best.get(key);
            if (kept == null || isLater(row, kept)) {
                best.put(key, row);
            }
        }
        return new ArrayList<>(best.values());
    }

    private static boolean isLater(ScoreRecord a, ScoreRecord b) {
        int sa = semesterNo(a.getSemester());
        int sb = semesterNo(b.getSemester());
        if (sa != sb) return sa > sb;
        return a.getId() != null && b.getId() != null && a.getId() > b.getId();
    }

    /** 学期列是 varchar，必须按数值比，否则 "10" &lt; "2"。空/非数字按 0（最早）。 */
    private static int semesterNo(String semester) {
        if (semester == null) return 0;
        try {
            return Integer.parseInt(semester.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 检测成绩异常
     */
    @GetMapping("/scores/anomalies")
    public ApiResponse<Object> detectAnomalies(@RequestParam Long course_id, HttpServletRequest request) {
        ApiResponse<Object> deny = denyUnlessMyCourse(course_id, request, "只能检测本人所授课程的异常");
        if (deny != null) {
            return deny;
        }
        try {
            // 先把"未录入"的行剔出去再算平均分与标准差：把它们当 0 分会把均值
            // 整体拉低、标准差抬高，判定阈值随之虚高 —— 结果是"正常偏低的学生"
            // 反而被判成异常，真正异常的却因为阈值太宽被漏掉。
            List<ScoreRecord> rows = latestPerStudent(scoreRecordService.getScoresByCourseId(course_id));
            List<Double> values = new ArrayList<>();
            for (ScoreRecord row : rows) {
                if (row.getScore() != null) values.add(row.getScore());
            }

            List<ScoreRecord> anomalies = new ArrayList<>();
            if (values.size() >= 2) {                 // 只有 1 个样本时标准差恒为 0，判定无意义
                double avgScore = 0;
                for (double v : values) avgScore += v;
                avgScore /= values.size();

                double variance = 0;
                for (double v : values) variance += (v - avgScore) * (v - avgScore);
                double stdDev = Math.sqrt(variance / values.size());
                double threshold = stdDev * 2;

                if (threshold > 0) {
                    for (ScoreRecord row : rows) {
                        Double s = row.getScore();
                        if (s != null && Math.abs(s - avgScore) > threshold) {
                            anomalies.add(row);
                        }
                    }
                }
            }
            return ApiResponse.success(anomalies);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 删除成绩（仅限录入该成绩的教师）
     */
    @DeleteMapping("/scores/{scoreId}")
    public ApiResponse<Object> deleteScore(@PathVariable Long scoreId,
                                            HttpServletRequest request) {
        Long requestUserId = getUserIdFromGateway(request);
        ScoreRecord record = scoreRecordService.getById(scoreId);
        if (record != null && record.getTeacherId() != null
                && !record.getTeacherId().equals(requestUserId) && !isAdmin(request)) {
            return ApiResponse.error(403, "无权删除其他教师录入的成绩");
        }
        try {
            // 2026-10-03（W7）：0 行 = 该 id 不存在（此前回「成绩删除成功」）
            return scoreRecordService.removeById(scoreId) > 0
                    ? ApiResponse.success("成绩删除成功")
                    : ApiResponse.notFound("成绩记录不存在");
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 批量删除成绩。
     *
     * <p><b>2026-10-04（W12）补归属闸</b>：单条 {@code DELETE /scores/{scoreId}} 一直有
     * "只能删自己录的"这道闸，而这条**按 id 列表**的批量版一道都没有 —— 同一个资源、
     * 两个入口、两套口径。传别人的 id 进来就删掉了别人的成绩。现在逐条比
     * {@code score_record.teacher_id}，有一条不是本人录的就整批拒绝。
     */
    @PostMapping("/scores/batch-delete")
    public ApiResponse<Object> batchDeleteScores(@RequestBody List<Long> scoreIds, HttpServletRequest request) {
        if (!isAdmin(request) && !allScoresAreMine(scoreIds, request)) {
            return forbidden("只能删除本人录入的成绩");
        }
        try {
            // 2026-10-03（W7）：0 行 = 这些 id 一个都不存在，如实回 404
            return scoreRecordService.removeByIds(scoreIds) > 0
                    ? ApiResponse.success("成绩批量删除成功")
                    : ApiResponse.notFound("没有可删除的成绩记录");
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取班级成绩分析
     */
    @GetMapping("/classes/{classId}/analysis")
    public ApiResponse<Object> getClassScoreAnalysis(@PathVariable Long classId, HttpServletRequest request) {
        // W12 资源归属闸：classes.teacher_id 是 users.id（活库 18/19/22/29）。
        ApiResponse<Object> deny = denyUnlessMyClass(classId, request, "只能查看本人所带班级的分析");
        if (deny != null) {
            return deny;
        }
        try {
            // 获取班级信息
            ClassInfo clazz = classInfoService.getClassById(classId);
            if (clazz == null) {
                return ApiResponse.error("班级不存在");
            }
            
            // 获取班级成绩列表，按学生去重
            List<ScoreRecord> scores = scoreRecordService.getScoresByClassName(clazz.getName());
            
            double averageScore = 0;
            double passRate = 0;
            double excellentRate = 0;
            
            // 按studentId分组，计算每个学生的平均分
            Map<Long, List<Double>> studentScoreMap = new java.util.LinkedHashMap<>();
            for (ScoreRecord score : scores) {
                if (score.getStudentId() != null && score.getScore() != null) {
                    studentScoreMap.computeIfAbsent(score.getStudentId(), k -> new ArrayList<>()).add(score.getScore());
                }
            }
            
            int totalStudents = studentScoreMap.size();
            
            if (totalStudents > 0) {
                double totalAvg = 0;
                int passCount = 0;
                int excellentCount = 0;
                
                for (List<Double> stuScores : studentScoreMap.values()) {
                    double sum = 0;
                    for (Double s : stuScores) sum += s;
                    double avg = sum / stuScores.size();
                    totalAvg += avg;
                    if (avg >= 60) passCount++;
                    if (avg >= 90) excellentCount++;
                }
                
                averageScore = Math.round(totalAvg / totalStudents * 100.0) / 100.0;
                passRate = Math.round((double) passCount / totalStudents * 100.0) / 100.0;
                excellentRate = Math.round((double) excellentCount / totalStudents * 100.0) / 100.0;
            }
            
            Map<String, Object> analysis = Map.of(
                "classId", classId,
                "className", clazz.getName(),
                "averageScore", averageScore,
                "passRate", passRate,
                "excellentRate", excellentRate,
                "totalStudents", totalStudents
            );
            return ApiResponse.success(analysis);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取我的班级
     */
    @GetMapping("/class-management/my-classes")
    public ApiResponse<Object> getMyClasses(@RequestParam Long teacherId, HttpServletRequest request) {
        // W11 读侧归属闸：users.id（classInfoService.getClassesByTeacherId）。
        if (!isSelf(teacherId, request)) {
            return forbidden("只能查看本人所带班级");
        }
        try {
            List<Map<String, Object>> classes = classInfoService.getClassesByTeacherId(teacherId);
            return ApiResponse.success(classes);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 搜索班级
     */
    @GetMapping("/class-management/search")
    public ApiResponse<Object> searchClasses(@RequestParam(required = false) String keyword) {
        try {
            List<Map<String, Object>> classes = classInfoService.searchClasses(keyword);
            return ApiResponse.success(classes);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取教师的班级管理申请记录
     */
    @GetMapping("/class-management/applications")
    public ApiResponse<Object> getMyApplications(@RequestParam Long teacherId, HttpServletRequest request) {
        // W11 读侧归属闸：users.id。
        if (!isSelf(teacherId, request)) {
            return forbidden("只能查看本人的班级管理申请记录");
        }
        try {
            List<Object> applications = (List<Object>) (List<?>) applicationService.getApplicationsByTeacherId(teacherId);
            return ApiResponse.success(applications);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 提交班级管理申请（申请管理或解除管理）
     */
    @PostMapping("/class-management/apply")
    public ApiResponse<Object> submitClassManagementRequest(@RequestBody Map<String, Object> data) {
        try {
            Long teacherId = Long.parseLong(data.get("teacherId").toString());
            Long classId = Long.parseLong(data.get("classId").toString());
            String reason = (String) data.get("reason");
            String className = (String) data.get("className");
            
            // 获取申请类型，默认为0（申请管理）
            Integer applicationType = 0;
            if (data.get("applicationType") != null) {
                applicationType = Integer.parseInt(data.get("applicationType").toString());
            }
            
            String errorMessage = applicationService.submitApplication(teacherId, classId, reason, className, applicationType);
            if (errorMessage != null) {
                return ApiResponse.error(errorMessage);
            }
            
            String message = applicationType == 1 ? "解除申请提交成功" : "申请提交成功";
            return ApiResponse.success(message);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 解除班级管理权限
     */
    @PostMapping("/class-management/cancel")
    public ApiResponse<Object> cancelClassManagement(@RequestBody Map<String, Object> data) {
        try {
            Long teacherId = Long.parseLong(data.get("teacherId").toString());
            Long classId = Long.parseLong(data.get("classId").toString());
            
            String errorMessage = applicationService.cancelClassManagement(teacherId, classId);
            if (errorMessage != null) {
                return ApiResponse.error(errorMessage);
            }
            return ApiResponse.success("班级管理权限已解除");
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取班级学生
     */
    @GetMapping("/classes/{classId}/students")
    public ApiResponse<Object> getClassStudents(@PathVariable Long classId, HttpServletRequest request) {
        // 实测：教师 18 传 classId=1（于凤海的班）拿到整班 24 行「学号 + 姓名」。
        ApiResponse<Object> deny = denyUnlessMyClass(classId, request, "只能查看本人所带班级的学生");
        if (deny != null) {
            return deny;
        }
        try {
            List<Map<String, Object>> students = classInfoService.getStudentsByClassId(classId);
            return ApiResponse.success(students);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取教师列表
     */
    @GetMapping("/list")
    public ApiResponse<Object> getTeachers() {
        try {
            List<TeacherProfile> teachers = teacherService.list();
            List<Map<String, Object>> result = new ArrayList<>();
            for (TeacherProfile t : teachers) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", t.getId());
                item.put("userId", t.getUserId());
                item.put("name", t.getName());
                item.put("title", t.getTitle());
                item.put("collegeId", t.getCollegeId());
                result.add(item);
            }
            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取教师消息
     */
    /**
     * 获取教师消息。
     *
     * <p>数据源是 {@code feedback} 表：教师这里的"消息"就是学生提交的反馈。
     * 每条消息是一个 {@code status} 为 0（未回复）/ 1（已回复）的行，
     * <b>不</b>再额外给一个 {@code isRead} —— 同一条记录上挂两个含义相同的布尔，
     * 前端总会有一个读到错的那个（PC 端读 {@code status}，早先接口只给
     * {@code isRead}，于是"未读"恒为 0）。
     *
     * <p>早先这段把 {@code Feedback} 实体当 {@code Map} 强转，一调即
     * {@code ClassCastException}。
     */
    @GetMapping("/messages/{userId}")
    public ApiResponse<Object> getMessages(@PathVariable String userId, HttpServletRequest request) {
        // W11 读侧归属闸：users.id 空间。
        if (!isSelf(Long.parseLong(userId), request)) {
            return forbidden("只能查看本人的消息");
        }
        try {
            Long uid = Long.parseLong(userId);
            List<Map<String, Object>> messages = new ArrayList<>();
            for (Feedback fb : feedbackService.getFeedbacks(uid, null)) {
                Map<String, Object> msg = new HashMap<>();
                msg.put("id", fb.getId());
                msg.put("title", "学生反馈");
                msg.put("content", fb.getContent());
                msg.put("category", fb.getCategory());
                msg.put("type", "feedback");
                msg.put("status", fb.getStatus() == null ? 0 : fb.getStatus());
                msg.put("reply", fb.getReply());
                msg.put("createdAt", fb.getCreateTime());
                msg.put("repliedAt", fb.getUpdateTime());
                messages.add(msg);
            }
            return ApiResponse.success(messages);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取未读消息数量。
     *
     * <p>返回的是**一个数字**，不是 {@code {userId, unreadCount}} 对象 ——
     * 与 {@code /teachers/count-by-college} 一样，统计类接口直接给标量。
     * PC 端 {@code TeacherMessages.vue} 收到响应后判的是
     * {@code typeof r === 'number'}，早先接口返回对象，于是角标恒为 0。
     */
    @GetMapping("/messages/{userId}/unread-count")
    public ApiResponse<Long> getUnreadCount(@PathVariable String userId, HttpServletRequest request) {
        // W11 读侧归属闸：users.id 空间。
        if (!isSelf(Long.parseLong(userId), request)) {
            return forbidden("只能查看本人的消息");
        }
        try {
            Long uid = Long.parseLong(userId);
            // 未读 = 未回复（feedback.status = 0）。
            List<Feedback> feedbacks = feedbackService.getFeedbacks(uid, null);
            long unread = feedbacks.stream()
                    .filter(f -> f.getStatus() != null && f.getStatus() == 0)
                    .count();
            return ApiResponse.success(unread);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    // 「标记消息已读」`POST /messages/{id}/mark-read` 已删（2026-09-30）。
    //
    // 它原先的整个方法体只有一句 `return ApiResponse.success("消息已标记为已读")` ——
    // **不碰任何表**，却回 success，是典型的"200 但不写库"空壳。
    // 更要紧的是：这里的"消息"就是 `feedback` 表（见 getMessages 的说明），
    // 该表**没有"已读"这一列**，只有 status 0=未回复 / 1=已回复。
    // 于是前端那个「已读」按钮本地把 status 改成 1 —— 等于**点一下就把未回复的反馈标成已回复**，
    // 而刷新后 unread-count 又从库里算回来，界面与库各说各话。
    //
    // 删掉而不是"补实现"：这个状态没有地方可落，补出来也只能是假的。
    // 「未回复」口径由 `GET /messages/{userId}/unread-count` 给（status=0 的条数），
    // 回复走 `POST /feedbacks/{feedbackId}/reply`。

    /**
     * 按学院统计教师数量。
     *
     * <p><b>W15 修的一条静默错</b>：原来 {@code catch} 里回的是 {@code ApiResponse.success(0L)} ——
     * 查库炸了与"这个学院一个教师都没有"在响应上长得一模一样（而且连日志都没打），
     * 调用方会把一次故障显示成一个可信的 0。与 W14 修的 {@code StudentController.countByCollege}
     * 同族，现在如实回错误并记日志。
     */
    @GetMapping("/count-by-college")
    public ApiResponse<Long> countByCollege(@RequestParam Long collegeId) {
        try {
            long count = teacherService.countByCollege(collegeId);
            return ApiResponse.success(count);
        } catch (Exception e) {
            log.error("统计教师数量失败: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    // ========== Gateway 头解析工具方法 ==========

    /**
     * 把聚合查询里可能是 Integer / Long / BigDecimal / String 的计数转成 int。
     * 取不到时返回 0 —— 这里全是"门数/学分"这类计数，缺失即 0 是安全的；
     * 若换成分数则不能这么处理（{@code null} 是"未录入"，不是 0 分）。
     */
    private static int intValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return (int) Double.parseDouble(value.toString().trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private Long getUserIdFromGateway(HttpServletRequest request) {
        String header = request.getHeader("X-User-Id");
        if (header != null) {
            try { return Long.parseLong(header); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    /** 取第一个非空白的值并 trim；都没有则返回 {@code null}。 */
    private static String firstNonBlank(Object... values) {
        for (Object value : values) {
            if (value != null && !value.toString().trim().isEmpty()) {
                return value.toString().trim();
            }
        }
        return null;
    }

    /**
     * 帮扶措施 + 沟通记录 → {@code handle_result}。
     *
     * <p>{@code academic_warning} 没有单独放备注的列，但这两段都是"处理了什么"的一部分，
     * 丢掉哪一段都又是一处"提交体被静默丢弃"，所以合并存进同一列并保留可读的标记。
     * 两段都空时给中性默认值「已处理」（与 warning-service 的 {@code handleWarning} 一致）。
     */
    private static String joinHandleResult(String measures, String remark) {
        String m = measures == null ? "" : measures;
        String r = remark == null ? "" : remark;
        if (m.isEmpty() && r.isEmpty()) {
            return "已处理";
        }
        if (r.isEmpty()) {
            return m;
        }
        if (m.isEmpty()) {
            return "沟通记录：" + r;
        }
        return "帮扶措施：" + m + "；沟通记录：" + r;
    }

    /**
     * {@code handled_by} 是 {@code varchar(100)}；严格模式下超长会直接抛
     * {@code DataTooLong}，那会变成"处理失败"而其实只是名字太长。截断比报错合理。
     */
    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private boolean isAdmin(HttpServletRequest request) {
        String role = request.getHeader("X-Role");
        return "4".equals(role) || "admin".equals(role);
    }

    /**
     * 读侧归属闸（W11）。
     *
     * <p>教师自助面的路径/查询参数收的都是 <b>{@code users.id}</b>（与 {@link #getUserIdFromGateway}
     * 同一套）。此前这些读端点"客户端给什么 id 就读什么 id" —— 换个数字就能读到别的教师的
     * 看板 / 班级学生 / 预警 / 待办 / 审计日志 / 消息 / 报名花名册。
     * 实测（经网关，只读）11 条，其中 {@code /enrollments?teacherId=29} 一次返回 246 行且**带学生姓名**。
     *
     * <p>管理员（网关注入的 {@code X-Role: 4}）旁路 —— 与写侧 {@code deleteScore}/{@code updateScore} 同形。
     * 身份头缺失/非法一律<b>拒绝</b>（fail-closed）。
     *
     * <p>⚠️ {@code /students/{studentId}/credit-prediction} **不属于**本方法管 —— 它的路径参数是
     * {@code student_profile.id}，与上面这套 users.id 不同源。它自己的闸是
     * {@link #denyUnlessMyStudent}（W18，2026-10-04）。此前这里写着"按设计就是教师看本班学生，
     * 属合法的跨用户读，不能拦" —— 那句话把**假设**当成了保证：没有任何代码验证过客户端给的
     * {@code studentId} 真在本人班上，于是它当时是个实打实的越权读（任意教师读任意学生学分）。
     */
    private boolean isSelf(Long teacherId, HttpServletRequest request) {
        if (isAdmin(request)) {
            return true;
        }
        Long me = getUserIdFromGateway(request);
        return me != null && me.equals(teacherId);
    }

    /**
     * 归属闸的**第二套 id 空间** —— {@code teacher_profile.id}。
     *
     * <p>全仓只有 {@code teacher_course} 一张表用 profile.id（其余教师列全是 users.id），
     * 所以只有 {@code /teachers/{teacherId}/courses} 走这条。用 {@link #isSelf} 去比会
     * **永远不等**：于凤海 users.id=29、profile.id=1 ⇒ 本人也被拦（"更安全"是假象，是坏了）。
     * 反过来把 {@link #isSelf} 改成比 profile.id 更糟：那 10 个端点会拿 29 去和 profile.id 比。
     */
    private boolean isSelfProfile(Long profileId, HttpServletRequest request) {
        if (isAdmin(request)) {
            return true;
        }
        Long me = getUserIdFromGateway(request);
        if (me == null) {
            return false;
        }
        TeacherProfile mine = teacherService.getByUserId(me);
        return mine != null && mine.getId() != null && mine.getId().equals(profileId);
    }

    /**
     * 归属拒绝的统一形状：HTTP 200 + body {@code code:403}（本仓惯用，前端按 code 判）。
     *
     * <p>写成泛型是因为本控制器有 {@code ApiResponse<Long>}（unread-count）与
     * {@code ApiResponse<Object>} 两种返回类型 —— 钉死成 {@code <Object>} 会编译不过。
     */
    private static <T> ApiResponse<T> forbidden(String message) {
        return ApiResponse.error(403, message);
    }

    // ========== 资源归属闸（W12，2026-10-04） ==========
    //
    /** 只用于把拒绝体写成 JSON（见 {@link #writeJsonDenied}）—— 无状态，可静态复用。 */
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();
    //
    //
    // W11 收口的是「**属主 id** 型」参数（studentId / teacherId —— 换个数字就读到别人的
    // 看板、花名册、消息）。这一族收的是「**资源 id 型**」参数（courseId / classId /
    // warningId / feedbackId / scoreId 列表）：id 本身不指向某个人，而指向一件东西，
    // 于是"这个 id 是不是我的"要**多查一次库**才能回答 —— 这正是它们此前一条都没被
    // 加闸的原因（加闸不再是"比一个数字"，得先看资源归属）。
    //
    // 实测（直连备用 8187，只读）：教师 18（刘教授，授课课程 6）打
    // `GET /teachers/courses/1/anomaly` 拿到**课程 1（高等数学，授课教师 29）的 24 行
    // 学生姓名 + 分数 + 不及格评语**；`GET /teachers/classes/1/students` 拿到整班
    // 学号 + 姓名；`/courses/1/distribution`、`/courses/1`、`/analysis?course_id=1`、
    // `/scores/analyze?course_id=1` 同样一律 HTTP 200。
    //
    // 归属口径（活库实测，两张表都是 users.id，与 W11 同一套）：
    //   courses.teacher_id = 18/19/22/28/29   classes.teacher_id = 18/19/22/29
    // 管理员（X-Role: 4）旁路；身份头缺失一律拒绝（fail-closed）。

    /**
     * 资源归属闸：**课程**。{@code courses.teacher_id} 是 {@code users.id}。
     *
     * <p>返回 {@code null} 表示放行；非 null 就是要直接 return 掉的拒绝响应。
     * 课程不存在时也**拒绝** —— 归属无从谈起，且不该借 404/403 的差别泄漏"这个 id 存在吗"。
     * 唯一的例外是 {@code getCourseDetail}（那条本就有 404 契约，见方法内的显式分支）。
     */
    private ApiResponse<Object> denyUnlessMyCourse(Long courseId, HttpServletRequest request, String message) {
        if (isAdmin(request)) {
            return null;
        }
        Long me = getUserIdFromGateway(request);
        Course course = courseService.getById(courseId);
        if (me != null && course != null && course.getTeacherId() != null
                && course.getTeacherId().equals(me)) {
            return null;
        }
        return forbidden(message);
    }

    /**
     * 资源归属闸：**班级**。{@code classes.teacher_id} 是 {@code users.id}。
     *
     * <p>与 {@link #denyUnlessMyCourse} 分开写是因为两边的表不同；口径一模一样。
     */
    private ApiResponse<Object> denyUnlessMyClass(Long classId, HttpServletRequest request, String message) {
        if (isAdmin(request)) {
            return null;
        }
        Long me = getUserIdFromGateway(request);
        ClassInfo clazz = classInfoMapper.selectById(classId);
        if (me != null && clazz != null && clazz.getTeacherId() != null
                && clazz.getTeacherId().equals(me)) {
            return null;
        }
        return forbidden(message);
    }

    /**
     * 资源归属闸：**班级名**。{@code classes.teacher_id} 是 {@code users.id}。W30，2026-10-08。
     *
     * <p>用于 {@code GET /teachers/scores/by-class} 里 {@code teacher_id} 缺省的那条分支 ——
     * 那条分支按班级名查**全班成绩**（含别位教师录的行），此前**一道校验都没有**：
     * {@code teacher_id} 只在传了的时候才过 {@link #isSelf}。W11 当时**有意保留**，
     * 理由写在注释里是「前端 {@code useTeacherRoster} 就是靠它拿姓名+分数的，保持放行」。
     * 这个理由**站不住**：
     * <ul>
     *   <li>{@code useTeacherRoster}（小程序 {@code composables/useTeacherRoster.js} 的消费方
     *       {@code services/api.js}）**是带 {@code teacher_id} 的** —— 它走的是过滤分支，
     *       压根不依赖这条无闸路径；</li>
     *   <li>PC 端唯一不传 {@code teacher_id} 的调用点是 {@code TeacherAnalysis.vue}，
     *       而它的班级下拉来自 {@code /class-management/my-classes}（按 {@code classes.teacher_id}
     *       限死）⇒ 它能选到的班**必然是自己带的**；</li>
     *   <li>本服务 SecurityConfig 是 {@code anyRequest().authenticated()} —— **控制器只认
     *       {@code X-User-Id} 头、不认角色**；网关上 {@code /api/teachers/**} 有
     *       {@code role != ROLE_TEACHER ⇒ false}（{@code AuthenticationFilter:365}），
     *       管理员在更上面已 {@code return true} ⇒ **经网关可达的严重性是「任意教师
     *       （role 2）可读任意班的整份成绩单」**。直连服务端口时（本仓服务均直连可达，
     *       网关不是网络边界）任何角色、乃至伪造的头都能读 —— 实测见手册 W30。</li>
     * </ul>
     * ⇒ 两条"放行的理由"都不成立，而代价是任意教师可读任意班成绩。
     *
     * <p>判据：{@code classes.name = class_name AND teacher_id = 本人} 至少一行。
     * 0 行（没有这个班 / 这个班不是我的 / 班级没有班主任）一律**拒绝** ——
     * 在归属闸里这三者必须同解，否则畸形请求就是一条绕过路径。
     */
    private ApiResponse<Object> denyUnlessMyClassByName(String className, HttpServletRequest request, String message) {
        if (isAdmin(request)) {
            return null;
        }
        Long me = getUserIdFromGateway(request);
        if (me == null || className == null || className.isBlank()) {
            return forbidden(message);
        }
        return classInfoMapper.countClassesNamedAndOwned(className, me) > 0 ? null : forbidden(message);
    }

    /**
     * 资源归属闸：**学生**（{@code student_profile.id}）。W18，2026-10-04。
     *
     * <p>用于 {@code GET /students/{studentId}/credit-prediction} —— 那条路径参数是
     * {@code student_profile.id}，**由客户端给**，此前一道校验都没有。控制器的注释还把它
     * 辩护成「按设计就是教师看本班学生，属合法的跨用户读，不能拦」；但"本班"是**假设**，
     * 那里没有一行代码验证过调用方给的 {@code studentId} 真的在本人班上。任意教师换个数字
     * 就能读到任意学生的姓名、已修学分、挂科门数（实测见手册 W18）。
     *
     * <p>判据：{@code student_profile.id → class_id → classes.teacher_id}，与
     * {@link #denyUnlessMyClass} 同源（{@code classes.teacher_id} 是 users.id）。
     * 学生没有班级（{@code class_id} 为 NULL）时**拒绝** —— 归属无从谈起，
     * 不能因为"查不到班"就当成"不是别人的"而放行。
     *
     * <p>⚠️ 已知边界：口径是「**班主任**所带班级的学生」。只授课、不是班主任的教师
     * （活库里 18/19/22/28 都给 class 1 的学生录过成绩）**取不到**这些学生的学分预测。
     * 这是有意收窄到"看本班学生"这一条 —— 前端能走到这条端点的只有小程序名册，
     * 而名册的班级列表来自 {@code /class-management/my-classes}（同样按 {@code classes.teacher_id}
     * 限死），所以**没有**任何现存流程因此被误伤。
     */
    private ApiResponse<Object> denyUnlessMyStudent(Long profileId, HttpServletRequest request, String message) {
        if (isAdmin(request)) {
            return null;
        }
        Long me = getUserIdFromGateway(request);
        if (me == null) {
            return forbidden(message);
        }
        Long classId = classInfoMapper.findClassIdOfProfile(profileId);
        if (classId == null) {
            return forbidden(message);
        }
        ClassInfo clazz = classInfoMapper.selectById(classId);
        if (clazz != null && clazz.getTeacherId() != null && clazz.getTeacherId().equals(me)) {
            return null;
        }
        return forbidden(message);
    }

    /**
     * 这批成绩是不是**本人录的**。
     *
     * <p>用于 {@code /scores/batch-delete} 这类**按 id 列表下手**的路径 —— 逐条比
     * {@code score_record.teacher_id}，有一条不是本人录的就整批拒绝（不做"部分成功"：
     * 一半写进去一半没写，调用方无法知道到底发生了什么）。
     * id 在库里查不到时**不算通过**（不存在 ≠ 属于本人）。
     */
    private boolean allScoresAreMine(List<Long> scoreIds, HttpServletRequest request) {
        if (scoreIds == null || scoreIds.isEmpty()) {
            return false;
        }
        Long me = getUserIdFromGateway(request);
        if (me == null) {
            return false;
        }
        for (Long scoreId : scoreIds) {
            if (scoreId == null) {
                return false;
            }
            ScoreRecord record = scoreRecordService.getById(scoreId);
            if (record == null || record.getTeacherId() == null || !record.getTeacherId().equals(me)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 资源归属闸：**按 JSON 请求体写入的成绩面**（{@code POST /scores}）。
     *
     * <p><b>2026-10-04（W19）</b>：{@code /scores/import} 换成了 multipart（见该方法的
     * javadoc），它不再走这条闸，改由 {@link #denyUnlessMyCourse} 对请求参数里的
     * {@code courseId} 单独把关。这条现在只服务于 {@code POST /scores}。
     *
     * <p>这个端点的请求体里有两样由客户端说了算的东西：{@code teacherId}（记成"谁录的"）
     * 与每一行的 {@code courseId}（写进哪门课）。此前两样都照单全收 —— 换个
     * {@code teacherId} 就能以别人的名义录分，换个 {@code courseId} 就能往别人的课里录分。
     *
     * <p>判据：{@code teacherId} 必须等于登录人，且**每一行**的课程都必须是本人所授。
     * 形状不对（{@code scores} 不是列表、某行缺 {@code courseId}）一律**拒绝**而不是跳过 ——
     * "判不出来"和"不是你的"在归属闸里必须同解，否则畸形请求就是一条绕过路径。
     */
    private ApiResponse<Object> denyUnlessScoreWriteAllowed(Map<String, Object> data, HttpServletRequest request,
                                                            String message) {
        if (isAdmin(request)) {
            return null;
        }
        Long me = getUserIdFromGateway(request);
        if (me == null) {
            return forbidden(message);
        }
        Object rawTeacherId = data == null ? null : data.get("teacherId");
        if (rawTeacherId == null || !me.equals(parseLongOrNull(rawTeacherId))) {
            return forbidden(message);
        }
        Object rawScores = data.get("scores");
        if (!(rawScores instanceof List<?> rows)) {
            return forbidden(message);
        }
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> map)) {
                return forbidden(message);
            }
            Long courseId = parseLongOrNull(map.get("courseId"));
            if (courseId == null) {
                return forbidden(message);
            }
            ApiResponse<Object> deny = denyUnlessMyCourse(courseId, request, message);
            if (deny != null) {
                return deny;
            }
        }
        return null;
    }

    /** 宽进严出地把一个来自 JSON 的数字解析成 {@code Long}；非数字返回 {@code null}。 */
    private static Long parseLongOrNull(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 归属拒绝落到**文件下载**端点上的写法。
     *
     * <p>下载端点不能用"HTTP 200 + body code 403"那套 —— 前端带
     * {@code responseType:'blob'}，200 会被直接当文件存下来（一个内容是错误 JSON 的
     * "xlsx"）。所以这里如实回 4xx，并把同一段 {@code {code, message}} 写成响应体；
     * 前端 {@code utils/download.js} 的 {@code readErrorMessage} 会把 Blob 读回文本、
     * 取里面的 {@code message} 显示出来（它不是 JSON 时退化成截断原文）。
     */
    private static void writeJsonDenied(HttpServletResponse response, ApiResponse<?> body) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("code", body.getCode());
        payload.put("message", body.getMessage());
        payload.put("success", false);
        try {
            response.getWriter().write(JSON_MAPPER.writeValueAsString(payload));
        } catch (Exception e) {
            // 连错误体都写不出去就只能算了 —— 状态码已经是 403，前端不会把它当成功文件。
        }
    }
}

