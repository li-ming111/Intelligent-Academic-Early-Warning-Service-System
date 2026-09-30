package com.academic.controller;

import com.academic.common.constant.ScoreCaliber;
import com.academic.common.dto.ApiResponse;
import com.academic.common.util.ScoreSyncSupport;
import com.academic.common.entity.TeacherProfile;
import com.academic.common.entity.Course;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @Autowired
    private com.academic.mapper.ScoreRecordMapper scoreRecordMapper;

    /**
     * 教师注册
     */
    @PostMapping("/register")
    public ApiResponse<TeacherProfile> register(@RequestBody TeacherProfile teacherProfile) {
        try {
            boolean success = teacherService.register(teacherProfile);
            if (success) {
                return ApiResponse.success(teacherProfile);
            } else {
                return ApiResponse.badRequest("注册失败");
            }
        } catch (Exception e) {
            return ApiResponse.serverError("注册失败: " + e.getMessage());
        }
    }

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
    public ApiResponse<List<TeacherCourse>> getTeacherCourses(@PathVariable Long teacherId) {
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
     * 创建课程
     */
    @PostMapping("/courses")
    public ApiResponse<Course> createCourse(@RequestBody Course course) {
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
     * 更新课程信息
     */
    @PutMapping("/courses/{id}")
    public ApiResponse<String> updateCourse(@PathVariable Long id, @RequestBody Course course) {
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
    public ApiResponse<List<Course>> getCourseList(@RequestParam(required = false) Long teacher_id) {
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
    public ApiResponse<Object> getDashboard(@PathVariable String userId) {
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
            } catch (Exception e) {
                log.warn("获取教师信息失败(降级): {}", e.getMessage());
            }
            
            Map<String, Object> dashboard = new HashMap<>();
            dashboard.put("totalClasses", myClasses.size());
            dashboard.put("pendingRequests", myRequests.size());
            dashboard.put("totalStudents", totalStudents);
            dashboard.put("pendingFeedbacks", 0);
            dashboard.put("totalCourses", totalCourses);
            dashboard.put("teacherName", teacherName);
            
            return ApiResponse.success(dashboard);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取教师的学生列表
     */
    @GetMapping("/students/{teacherId}")
    public ApiResponse<Object> getStudents(@PathVariable String teacherId) {
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
    public ApiResponse<Object> getScores(@RequestParam Long course_id, @RequestParam(required = false) Long teacher_id) {
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
    public ApiResponse<Object> getScoresByClass(@RequestParam String class_name, @RequestParam(required = false) Long teacher_id) {
        try {
            List<Object> scores;
            if (teacher_id != null) {
                scores = (List<Object>) (List<?>) scoreRecordService.getScoresByClassNameAndTeacherId(class_name, teacher_id);
            } else {
                scores = (List<Object>) (List<?>) scoreRecordService.getScoresByClassName(class_name);
            }
            return ApiResponse.success(scores);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 保存成绩
     */
    @PostMapping("/scores")
    public ApiResponse<Object> saveScores(@RequestBody Map<String, Object> data) {
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
     */
    @PutMapping("/scores/{scoreId}")
    public ApiResponse<Object> updateScore(@PathVariable Long scoreId, @RequestBody Map<String, Object> data) {
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
     * 导出成绩
     */
    @GetMapping("/export/scores")
    public ApiResponse<Object> exportScores(@RequestParam Long course_id) {
        try {
            List<Object> scores = (List<Object>) (List<?>) scoreRecordService.exportScores(course_id);
            return ApiResponse.success(scores);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 下载成绩
     */
    @GetMapping("/download/scores")
    public ApiResponse<Object> downloadScores(@RequestParam Long course_id) {
        try {
            List<Object> scores = (List<Object>) (List<?>) scoreRecordService.exportScores(course_id);
            return ApiResponse.success(scores);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 导入成绩
     */
    @PostMapping("/scores/import")
    public ApiResponse<Object> importScores(@RequestBody Map<String, Object> data) {
        try {
            List<Map<String, Object>> scores = (List<Map<String, Object>>) data.get("scores");
            Long teacherId = Long.parseLong(data.get("teacherId").toString());
            scoreRecordService.saveScores(scores, teacherId);
            return ApiResponse.success("成绩导入成功");
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取反馈
     */
    @GetMapping("/feedbacks")
    public ApiResponse<Object> getFeedbacks(@RequestParam Long teacherId, @RequestParam(required = false) String category) {
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
    public ApiResponse<Object> getEnrollments(@RequestParam(required = false) Long teacherId, @RequestParam(required = false) Long courseId) {
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
    public ApiResponse<Object> getAnalysis(@RequestParam Long course_id) {
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
    public ApiResponse<Object> getCourseDetail(@PathVariable Long courseId) {
        try {
            Course course = courseService.getById(courseId);
            return ApiResponse.success(course);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 推荐课程
     */
    @PostMapping("/courses/recommend")
    public ApiResponse<Object> recommendCourse(@RequestBody Map<String, Object> data) {
        try {
            Long teacherId = Long.parseLong(data.get("teacherId").toString());
            Long courseId = Long.parseLong(data.get("courseId").toString());
            String reason = (String) data.getOrDefault("reason", "");
            Map<String, Object> result = new HashMap<>();
            result.put("teacherId", teacherId);
            result.put("courseId", courseId);
            result.put("reason", reason);
            result.put("status", "已推荐");
            result.put("message", "课程推荐成功，学生将收到通知");
            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取预警信息
     */
    @GetMapping("/warnings/{teacherId}")
    public ApiResponse<Object> getWarnings(@PathVariable String teacherId, @RequestParam(required = false) String status) {
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
                    m.put("status", w.getStatus() != null ? (w.getStatus() == 0 ? "待处理" : "已处理") : "待处理");
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
     * 处理预警
     */
    @PostMapping("/warnings/{warningId}/process")
    public ApiResponse<Object> processWarning(@PathVariable Long warningId, @RequestBody Map<String, Object> data) {
        try {
            String action = (String) data.getOrDefault("action", "处理");
            String remark = (String) data.getOrDefault("remark", "");
            Map<String, Object> result = new HashMap<>();
            result.put("warningId", warningId);
            result.put("action", action);
            result.put("remark", remark);
            result.put("status", "已处理");
            result.put("processedAt", java.time.LocalDateTime.now().toString());
            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 导入学生
     */
    @PostMapping("/students/import")
    public ApiResponse<Object> importStudents(@RequestParam("file") MultipartFile formData) {
        try {
            String fileName = formData.getOriginalFilename();
            long fileSize = formData.getSize();
            // 文件大小限制 10MB，仅允许 Excel 文件
            if (fileSize > 10 * 1024 * 1024) {
                return ApiResponse.error("文件大小不能超过10MB");
            }
            if (fileName == null || !fileName.matches(".*\\.(xlsx|xls)$")) {
                return ApiResponse.error("仅支持 .xlsx 或 .xls 格式的Excel文件");
            }
            Map<String, Object> result = new HashMap<>();
            result.put("fileName", fileName);
            result.put("fileSize", fileSize);
            result.put("imported", 0);
            result.put("message", "文件已接收，请稍后查看导入结果");
            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 触发预警
     */
    @PostMapping("/scores/warnings")
    public ApiResponse<Object> triggerWarnings(@RequestParam Long course_id) {
        try {
            List<ScoreRecord> scores = latestPerStudent(scoreRecordService.getScoresByCourseId(course_id));
            long warningCount = scores.stream()
                .map(ScoreRecord::getScore)
                .filter(Objects::nonNull)              // 未录入的不算 0 分
                .filter(score -> score < ScoreCaliber.PASS_LINE)
                .count();
            Map<String, Object> result = new HashMap<>();
            result.put("courseId", course_id);
            result.put("totalStudents", scores.size());
            result.put("warningsTriggered", warningCount);
            result.put("message", "已为" + warningCount + "名学生生成预警");
            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取学分预测
     */
    @GetMapping("/students/{studentId}/credit-prediction")
    public ApiResponse<Object> getCreditPrediction(@PathVariable String studentId) {
        try {
            // 路径参数是 student_profile.id（与 scores.student_id 同一套），不是 users.id、也不是学号。
            Long sid = Long.parseLong(studentId);

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
    public ApiResponse<Object> analyzeStudentScore(@RequestParam Long student_id, @RequestParam Long course_id) {
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
    public ApiResponse<Object> getMyClassManagementRequests(@RequestParam Long teacherId) {
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
    public ApiResponse<Object> getTodos(@PathVariable String teacherId) {
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
    public ApiResponse<Object> getCourseScoreDistribution(@PathVariable Long courseId) {
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
    public ApiResponse<Object> getCourseAnomalies(@PathVariable Long courseId, @RequestParam(required = false, defaultValue = "60") Integer threshold) {
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
                                                 @RequestParam(required = false, defaultValue = "20") Integer size) {
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
     * 获取课程推荐
     */
    @GetMapping("/courses/{courseId}/recommendations")
    public ApiResponse<Object> getCourseRecommendations(@PathVariable Long courseId, @RequestParam(required = false, defaultValue = "5") Integer limit) {
        try {
            List<Map<String, Object>> recommendations = new ArrayList<>();
            // 根据课程成绩分布推荐学习建议（一人一行，避免同一名学生被数多次）
            List<ScoreRecord> scores = latestPerStudent(scoreRecordService.getScoresByCourseId(courseId));
            if (scores.isEmpty()) {
                return ApiResponse.success(recommendations);
            }
            // 统计低分学生，给出推荐
            int lowScoreCount = 0;
            for (ScoreRecord s : scores) {
                // 未录入不等于不及格：跳过，不当作 0 分
                if (s.getScore() != null && s.getScore() < ScoreCaliber.PASS_LINE) lowScoreCount++;
            }
            if (lowScoreCount > 0) {
                Map<String, Object> rec = new HashMap<>();
                rec.put("id", 1L);
                rec.put("title", "加强基础知识点教学");
                rec.put("description", "该课程有" + lowScoreCount + "名学生成绩不及格，建议针对薄弱知识点加强教学");
                rec.put("priority", "high");
                recommendations.add(rec);
            }
            Map<String, Object> rec2 = new HashMap<>();
            rec2.put("id", 2L);
            rec2.put("title", "增加实践环节");
            rec2.put("description", "建议增加编程实践和小组项目，提升学生动手能力");
            rec2.put("priority", "medium");
            recommendations.add(rec2);
            return ApiResponse.success(recommendations);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取成绩修改审计日志（score_audit_log 表）
     */
    @GetMapping("/scores/audit/{teacherId}")
    public ApiResponse<Object> getAuditLogs(@PathVariable String teacherId, @RequestParam(required = false, defaultValue = "1") Integer page, @RequestParam(required = false, defaultValue = "20") Integer size) {
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
    public ApiResponse<Object> getScoreLogs(@PathVariable String teacherId, @RequestParam(required = false, defaultValue = "1") Integer page, @RequestParam(required = false, defaultValue = "20") Integer size) {
        return getAuditLogs(teacherId, page, size);
    }

    /**
     * 获取反馈列表
     */
    @GetMapping("/feedbacks/{teacherId}/list")
    public ApiResponse<Object> getFeedbackList(@PathVariable String teacherId, @RequestParam(required = false, defaultValue = "") String category, @RequestParam(required = false, defaultValue = "1") Integer page, @RequestParam(required = false, defaultValue = "20") Integer size) {
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
    public ApiResponse<Object> replyFeedback(@PathVariable Long feedbackId, @RequestBody Map<String, Object> data) {
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
    public ApiResponse<Object> analyzeScores(@RequestParam Long course_id) {
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
    public ApiResponse<Object> detectAnomalies(@RequestParam Long course_id) {
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
            scoreRecordService.removeById(scoreId);
            return ApiResponse.success("成绩删除成功");
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 批量删除成绩
     */
    @PostMapping("/scores/batch-delete")
    public ApiResponse<Object> batchDeleteScores(@RequestBody List<Long> scoreIds) {
        try {
            scoreRecordService.removeByIds(scoreIds);
            return ApiResponse.success("成绩批量删除成功");
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取班级成绩分析
     */
    @GetMapping("/classes/{classId}/analysis")
    public ApiResponse<Object> getClassScoreAnalysis(@PathVariable Long classId) {
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
    public ApiResponse<Object> getMyClasses(@RequestParam Long teacherId) {
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
    public ApiResponse<Object> getMyApplications(@RequestParam Long teacherId) {
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
    public ApiResponse<Object> getClassStudents(@PathVariable Long classId) {
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
    public ApiResponse<Object> getMessages(@PathVariable String userId) {
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
    public ApiResponse<Long> getUnreadCount(@PathVariable String userId) {
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

    /**
     * 标记消息已读
     */
    @PostMapping("/messages/{messageId}/mark-read")
    public ApiResponse<Object> markMessageAsRead(@PathVariable Long messageId) {
        try {
            return ApiResponse.success("消息已标记为已读");
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 按学院统计教师数量
     */
    @GetMapping("/count-by-college")
    public ApiResponse<Long> countByCollege(@RequestParam Long collegeId) {
        try {
            long count = teacherService.countByCollege(collegeId);
            return ApiResponse.success(count);
        } catch (Exception e) {
            return ApiResponse.success(0L);
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

    private boolean isAdmin(HttpServletRequest request) {
        String role = request.getHeader("X-Role");
        return "4".equals(role) || "admin".equals(role);
    }
}

