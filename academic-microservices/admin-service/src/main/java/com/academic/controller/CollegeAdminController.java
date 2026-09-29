package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.common.entity.*;
import com.academic.entity.Classes;
import com.academic.entity.StudentProfile;
import com.academic.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/college-admin")
public class CollegeAdminController {

    @Autowired private CollegeAdminProfileMapper collegeAdminProfileMapper;
    @Autowired private ClassesMapper classesMapper;
    @Autowired private StudentProfileMapper studentProfileMapper;
    @Autowired private TeacherProfileMapper teacherProfileMapper;
    @Autowired private CounselorProfileMapper counselorProfileMapper;
    @Autowired private AcademicWarningMapper academicWarningMapper;
    @Autowired private com.academic.mapper.MajorMapper majorMapper;
    @Autowired private ScoreStatMapper scoreStatMapper;

    /** 聚合查询返回的计数类字段。JDBC 对 COUNT() 可能给 Long，也可能给 BigDecimal。 */
    private static int intOf(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    /** 聚合查询返回的比率类字段；无数据（NULL）返回 0。 */
    private static double doubleOf(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0;
    }

    /** 平均分／GPA 统一保留两位小数。 */
    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /** 平均分统一保留一位小数（成绩均分的展示习惯）。 */
    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    /**
     * 及格率。分母为 0 时返回 0，且分子分母都来自同一份聚合结果 ——
     * 此前多处用"整表记录数"当分母，把没成绩的学生也算进去。
     */
    private static int passRateOf(Object passCount, Object recordCount) {
        int total = intOf(recordCount);
        return total == 0 ? 0 : (int) Math.round(intOf(passCount) * 100.0 / total);
    }

    @GetMapping("/profile/{userId}")
    public ApiResponse<Map<String, Object>> getProfile(@PathVariable Long userId) {
        Map<String, Object> profile = collegeAdminProfileMapper.selectByUserId(userId);
        return profile != null ? ApiResponse.success(profile) : ApiResponse.error("未找到学院管理员档案");
    }

    private Long getCollegeId(Long userId) {
        Map<String, Object> p = collegeAdminProfileMapper.selectByUserId(userId);
        return p != null ? (Long) p.get("college_id") : null;
    }

    @GetMapping("/dashboard/{userId}")
    public ApiResponse<Map<String, Object>> dashboard(@PathVariable Long userId) {
        Long collegeId = getCollegeId(userId);
        if (collegeId == null) return ApiResponse.error("无权限");

        Map<String, Object> d = new HashMap<>();
        Map<String, Object> profile = collegeAdminProfileMapper.selectByUserId(userId);
        d.put("profile", profile);

        List<Map<String, Object>> classes = classesMapper.getClassesByCollegeId(collegeId);
        d.put("classCount", classes.size());

        List<Long> classIds = classes.stream().map(c -> (Long) c.get("id")).collect(Collectors.toList());
        long studentCount = classIds.isEmpty() ? 0 :
            studentProfileMapper.selectCount(new QueryWrapper<StudentProfile>().in("class_id", classIds));
        d.put("studentCount", (int) studentCount);

        long teacherCount = teacherProfileMapper.selectCount(
            new QueryWrapper<TeacherProfile>().eq("college_id", collegeId));
        d.put("teacherCount", (int) teacherCount);

        List<CounselorProfile> allCounselors = counselorProfileMapper.selectList(new QueryWrapper<>());
        Set<Long> counselorClassIds = new HashSet<>();
        allCounselors.forEach(c -> {
            List<Map<String, Object>> cc = classesMapper.getClassesByCounselorIdMap(c.getUserId());
            cc.forEach(m -> counselorClassIds.add((Long) m.get("id")));
        });
        long counselorCount = classes.stream().filter(c -> counselorClassIds.contains(c.get("id"))).count();
        d.put("counselorCount", (int) Math.min(counselorCount, allCounselors.size()));

        // 原实现把整张预警表查回来，再逐条 selectById 查学生以判断班级 ——
        // 预警数 × 学生表查询。改为一次聚合；academic_warning.student_id 存的是
        // student_profile.id，故按 sp.class_id 关联。
        long warningCount = classIds.isEmpty() ? 0 :
            intOf(academicWarningMapper.selectCollegeWarningStats(collegeId).get("warning_count"));
        d.put("warningCount", (int) warningCount);

        List<Major> majors = majorMapper.selectList(new QueryWrapper<Major>().eq("college_id", collegeId));
        d.put("majorCount", majors.size());
        return ApiResponse.success(d);
    }

    @GetMapping("/classes/{userId}")
    public ApiResponse<List<Map<String, Object>>> getClasses(@PathVariable Long userId) {
        Long collegeId = getCollegeId(userId);
        if (collegeId == null) return ApiResponse.error("无权限");
        List<Map<String, Object>> classes = classesMapper.getClassesByCollegeId(collegeId);
        // 班级统计一次查出后按 classId 取用，取代"逐班逐生查库"
        Map<Long, Map<String, Object>> statsByClass = new HashMap<>();
        for (Map<String, Object> row : scoreStatMapper.selectClassStats(collegeId)) {
            statsByClass.put(((Number) row.get("class_id")).longValue(), row);
        }
        for (Map<String, Object> c : classes) {
            Map<String, Object> st = statsByClass.get(((Number) c.get("id")).longValue());
            c.put("studentCount", st == null ? 0 : intOf(st.get("student_count")));
            c.put("gradedStudentCount", st == null ? 0 : intOf(st.get("graded_student_count")));
            // 无成绩的班级返回 null 而非 0：0 会被读成"全班 GPA 为零"
            c.put("avgGpa", st == null || st.get("avg_gpa") == null
                ? null : round2(doubleOf(st.get("avg_gpa"))));
            c.put("warningCount", st == null ? 0 : intOf(st.get("warning_count")));
        }
        return ApiResponse.success(classes);
    }

    @GetMapping("/students/{userId}")
    public ApiResponse<List<Map<String, Object>>> getStudents(
            @PathVariable Long userId,
            @RequestParam(required = false) String keyword) {
        Long collegeId = getCollegeId(userId);
        if (collegeId == null) return ApiResponse.error("无权限");
        List<Map<String, Object>> classes = classesMapper.getClassesByCollegeId(collegeId);
        List<Long> classIds = classes.stream().map(c -> (Long) c.get("id")).collect(Collectors.toList());
        if (classIds.isEmpty()) return ApiResponse.success(Collections.emptyList());

        // 全院成绩汇总一次查出，取代"每个学生查一次成绩表 + 查一次预警表"
        Map<Long, Map<String, Object>> statsByStudent = new HashMap<>();
        for (Map<String, Object> row : scoreStatMapper.selectStudentScoreStats(collegeId)) {
            statsByStudent.put(((Number) row.get("profile_id")).longValue(), row);
        }

        List<StudentProfile> all = studentProfileMapper.selectList(new QueryWrapper<>());
        List<Map<String, Object>> result = new ArrayList<>();
        for (StudentProfile sp : all) {
            if (sp.getClassId() != null && classIds.contains(sp.getClassId())) {
                // 搜索过滤
                if (keyword != null && !keyword.isEmpty()) {
                    String kw = keyword.toLowerCase();
                    boolean match = (sp.getName() != null && sp.getName().toLowerCase().contains(kw))
                        || (String.valueOf(sp.getStudentId()).contains(kw))
                        || (sp.getClassName() != null && sp.getClassName().toLowerCase().contains(kw));
                    if (!match) continue;
                }
                Map<String, Object> m = new HashMap<>();
                m.put("id", sp.getId());
                m.put("studentId", sp.getStudentId());
                m.put("name", sp.getName());
                m.put("className", sp.getClassName());
                m.put("classId", sp.getClassId());
                m.put("collegeId", sp.getCollegeId());
                // 附加成绩统计
                Map<String, Object> st = statsByStudent.get(sp.getId());
                m.put("gpa", st == null || st.get("gpa") == null ? null : round2(doubleOf(st.get("gpa"))));
                m.put("totalCourses", st == null ? 0 : intOf(st.get("total_courses")));
                m.put("passedCourses", st == null ? 0 : intOf(st.get("passed_courses")));
                m.put("failedCourses", st == null ? 0 : intOf(st.get("failed_courses")));
                m.put("warningCount", st == null ? 0 : intOf(st.get("warning_count")));
                result.add(m);
            }
        }
        return ApiResponse.success(result);
    }

    /** 学生详情 */
    @GetMapping("/student-detail/{userId}/{profileId}")
    public ApiResponse<Map<String, Object>> getStudentDetail(@PathVariable Long userId, @PathVariable Long profileId) {
        Long collegeId = getCollegeId(userId);
        if (collegeId == null) return ApiResponse.error("无权限");
        StudentProfile sp = studentProfileMapper.selectById(profileId);
        if (sp == null) return ApiResponse.error("学生不存在");
        if (sp.getClassId() == null) return ApiResponse.error("该学生无班级信息");
        List<Map<String, Object>> classes = classesMapper.getClassesByCollegeId(collegeId);
        boolean inCollege = classes.stream().anyMatch(c -> c.get("id").equals(sp.getClassId()));
        if (!inCollege) return ApiResponse.error("该学生不属于您管理的学院");

        Map<String, Object> detail = new HashMap<>();
        detail.put("id", sp.getId());
        detail.put("studentId", sp.getStudentId());
        detail.put("name", sp.getName());
        detail.put("className", sp.getClassName());

        // 成绩明细直接带出课程名，不再逐行回查 courses 表
        List<Map<String, Object>> scoreList = new ArrayList<>();
        for (Map<String, Object> row : scoreStatMapper.selectStudentScoreRows(profileId)) {
            Map<String, Object> sm = new HashMap<>();
            sm.put("id", row.get("id"));
            sm.put("courseId", row.get("course_id"));
            sm.put("courseName", row.get("course_name"));
            sm.put("regularScore", row.get("regular_score"));   // 平时成绩
            sm.put("finalScore", row.get("final_score"));       // 期末成绩；null 表示未录入
            sm.put("totalScore", row.get("score_total"));       // 总评成绩
            sm.put("gradePoint", row.get("grade_point"));       // 绩点
            sm.put("grade", row.get("grade"));                  // 等级
            sm.put("semester", row.get("semester"));
            sm.put("credits", row.get("credits"));
            scoreList.add(sm);
        }
        Map<String, Object> st = scoreStatMapper.selectOneStudentScoreStats(profileId);
        detail.put("gpa", st == null || st.get("gpa") == null ? null : round2(doubleOf(st.get("gpa"))));
        detail.put("totalCourses", st == null ? 0 : intOf(st.get("total_courses")));
        detail.put("passedCourses", st == null ? 0 : intOf(st.get("passed_courses")));
        detail.put("failedCourses", st == null ? 0 : intOf(st.get("failed_courses")));
        detail.put("scores", scoreList);

        List<AcademicWarning> warnings = academicWarningMapper.selectList(
            new QueryWrapper<AcademicWarning>().eq("student_id", profileId));
        List<Map<String, Object>> warnList = new ArrayList<>();
        for (AcademicWarning w : warnings) {
            Map<String, Object> wm = new HashMap<>();
            wm.put("id", w.getId()); wm.put("description", w.getDescription());
            wm.put("warningLevel", w.getWarningLevel()); wm.put("status", w.getStatus());
            wm.put("createdAt", w.getCreatedAt());
            warnList.add(wm);
        }
        detail.put("warnings", warnList);
        detail.put("warningCount", warnings.size());

        return ApiResponse.success(detail);
    }

    @GetMapping("/teachers/{userId}")
    public ApiResponse<List<Map<String, Object>>> getTeachers(@PathVariable Long userId) {
        Long collegeId = getCollegeId(userId);
        if (collegeId == null) return ApiResponse.error("无权限");
        List<TeacherProfile> all = teacherProfileMapper.selectList(new QueryWrapper<>()).stream()
            .filter(t -> t.getCollegeId() != null && t.getCollegeId().equals(collegeId))
            .collect(Collectors.toList());
        // 授课统计一次查出，按 teacher_id（users.id）取用
        Map<Long, Map<String, Object>> statsByTeacher = new HashMap<>();
        for (Map<String, Object> row : scoreStatMapper.selectTeacherStats(collegeId)) {
            statsByTeacher.put(((Number) row.get("teacher_id")).longValue(), row);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (TeacherProfile tp : all) {
            Map<String, Object> st = statsByTeacher.get(tp.getUserId());
            Map<String, Object> m = new HashMap<>();
            m.put("id", tp.getId());
            m.put("name", tp.getName());
            m.put("title", tp.getTitle());
            m.put("researchAreas", tp.getResearchAreas());
            m.put("courseCount", st == null ? 0 : intOf(st.get("course_count")));
            // 授课学生数取去重后的人数，不是成绩记录条数
            m.put("studentCount", st == null ? 0 : intOf(st.get("student_count")));
            m.put("courses", st == null || st.get("course_names") == null ? "" : st.get("course_names").toString());
            m.put("avgScore", st == null || st.get("avg_score") == null
                ? null : round1(doubleOf(st.get("avg_score"))));
            result.add(m);
        }
        return ApiResponse.success(result);
    }

    @GetMapping("/warnings/{userId}")
    public ApiResponse<List<Map<String, Object>>> getWarnings(@PathVariable Long userId) {
        Long collegeId = getCollegeId(userId);
        if (collegeId == null) return ApiResponse.error("无权限");

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : academicWarningMapper.selectWarningsByCollege(collegeId)) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", row.get("id"));
            m.put("studentId", row.get("student_id"));
            m.put("studentName", row.get("student_name"));
            m.put("className", row.get("class_name"));
            m.put("description", row.get("description"));
            m.put("warningLevel", row.get("warning_level"));
            m.put("status", row.get("status"));
            m.put("createdAt", row.get("created_at"));
            result.add(m);
        }
        return ApiResponse.success(result);
    }

    @GetMapping("/scores-stats/{userId}")
    public ApiResponse<Map<String, Object>> getScoreStats(@PathVariable Long userId) {
        Long collegeId = getCollegeId(userId);
        if (collegeId == null) return ApiResponse.error("无权限");

        Map<String, Object> overview = scoreStatMapper.selectCollegeScoreOverview(collegeId);
        if (overview == null) overview = Map.of();
        Map<String, Object> stats = new HashMap<>();
        stats.put("avgScore", overview.get("avg_score") == null
            ? 0 : round1(doubleOf(overview.get("avg_score"))));
        stats.put("passRate", passRateOf(overview.get("pass_count"), overview.get("record_count")));
        stats.put("recordCount", intOf(overview.get("record_count")));
        stats.put("studentCount", intOf(overview.get("student_count")));
        return ApiResponse.success(stats);
    }

    /** 本学院成绩分布分析 + 各科目分析 */
    @GetMapping("/score-analysis/{userId}")
    public ApiResponse<Map<String, Object>> scoreAnalysis(@PathVariable Long userId) {
        Long collegeId = getCollegeId(userId);
        if (collegeId == null) return ApiResponse.error("无权限");
        Map<String, Object> band = scoreStatMapper.selectScoreDistribution(collegeId);
        if (band == null) band = Map.of();
        Map<String, Integer> dist = new LinkedHashMap<>();
        dist.put("90-100", intOf(band.get("band1")));
        dist.put("80-89", intOf(band.get("band2")));
        dist.put("70-79", intOf(band.get("band3")));
        dist.put("60-69", intOf(band.get("band4")));
        dist.put("0-59", intOf(band.get("band5")));

        // 课程维度统计已在 SQL 里聚合，且按平均分降序返回
        List<Map<String, Object>> courseStats = new ArrayList<>();
        double total = 0; int cnt = 0, pass = 0;
        for (Map<String, Object> row : scoreStatMapper.selectCourseStats(collegeId)) {
            Map<String, Object> cs = new HashMap<>();
            cs.put("courseId", row.get("course_id"));
            cs.put("courseName", row.get("course_name") != null ? row.get("course_name") : "未知课程");
            cs.put("recordCount", intOf(row.get("record_count")));
            cs.put("avgScore", row.get("avg_score") == null ? 0 : round1(doubleOf(row.get("avg_score"))));
            cs.put("passRate", passRateOf(row.get("pass_count"), row.get("record_count")));
            courseStats.add(cs);
            total += doubleOf(row.get("avg_score")) * intOf(row.get("record_count"));
            cnt += intOf(row.get("record_count"));
            pass += intOf(row.get("pass_count"));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("distribution", dist);
        result.put("avgScore", cnt > 0 ? round1(total / cnt) : 0);
        result.put("passRate", passRateOf(pass, cnt));
        result.put("totalRecords", cnt);
        result.put("courseStats", courseStats);
        return ApiResponse.success(result);
    }

    /** 班级对比分析 */
    @GetMapping("/class-comparison/{userId}")
    public ApiResponse<List<Map<String, Object>>> classComparison(@PathVariable Long userId) {
        Long collegeId = getCollegeId(userId);
        if (collegeId == null) return ApiResponse.error("无权限");
        List<Map<String, Object>> rows = scoreStatMapper.selectClassComparison(collegeId);
        List<Map<String, Object>> result = new ArrayList<>();
        // 在原实现里，下面整个统计过程写在 classes 的 for 循环内部，
        // 每个班级都重新整表查询一次成绩 —— 班级数 × 成绩表行数。
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new HashMap<>();
            item.put("classId", row.get("class_id"));
            item.put("className", row.get("class_name"));
            item.put("avgScore", row.get("avg_score") == null
                ? null : round1(doubleOf(row.get("avg_score"))));
            item.put("passRate", passRateOf(row.get("pass_count"), row.get("record_count")));
            item.put("studentCount", intOf(row.get("student_count")));
            item.put("recordCount", intOf(row.get("record_count")));
            result.add(item);
        }
        return ApiResponse.success(result);
    }

    /** 本学院辅导员及管辖班级 */
    @GetMapping("/counselors/{userId}")
    public ApiResponse<List<Map<String, Object>>> getCounselors(@PathVariable Long userId) {
        Long collegeId = getCollegeId(userId);
        if (collegeId == null) return ApiResponse.error("无权限");
        List<CounselorProfile> counselors = counselorProfileMapper.selectList(
            new QueryWrapper<CounselorProfile>().eq("college_id", collegeId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (CounselorProfile cp : counselors) {
            List<Map<String, Object>> cls = classesMapper.getClassesByCounselorIdMap(cp.getUserId());
            List<String> classNames = new ArrayList<>();
            for (Map<String, Object> c : cls) classNames.add((String) c.get("name"));
            Map<String, Object> m = new HashMap<>();
            m.put("id", cp.getId()); m.put("name", cp.getName()); m.put("phone", cp.getPhone());
            m.put("classCount", cls.size()); m.put("classes", String.join("、", classNames));
            result.add(m);
        }
        return ApiResponse.success(result);
    }

    /** 本学院预警学生的帮扶计划 */
    @GetMapping("/assistance/{userId}")
    public ApiResponse<List<Map<String, Object>>> getAssistance(@PathVariable Long userId) {
        Long collegeId = getCollegeId(userId);
        if (collegeId == null) return ApiResponse.error("无权限");
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : academicWarningMapper.selectWarningsByCollege(collegeId)) {
            Map<String, Object> m = new HashMap<>();
            m.put("studentName", row.get("student_name"));
            m.put("className", row.get("class_name"));
            m.put("level", row.get("warning_level"));
            m.put("description", row.get("description"));
            m.put("status", row.get("status"));
            m.put("createdAt", row.get("created_at"));
            result.add(m);
        }
        return ApiResponse.success(result);
    }

    /** 导出本学院数据 */
    @GetMapping("/export/{userId}")
    public ApiResponse<List<Map<String, Object>>> exportData(@PathVariable Long userId,
            @RequestParam(defaultValue = "scores") String type) {
        Long collegeId = getCollegeId(userId);
        if (collegeId == null) return ApiResponse.error("无权限");
        List<Map<String, Object>> classes = classesMapper.getClassesByCollegeId(collegeId);
        Set<Long> classIds = classes.stream().map(c -> (Long) c.get("id")).collect(Collectors.toSet());
        List<Map<String, Object>> result = new ArrayList<>();
        
        if ("scores".equals(type)) {
            for (Map<String, Object> row : scoreStatMapper.selectScoreRows(collegeId, null, null)) {
                Map<String, Object> m = new HashMap<>();
                m.put("studentName", row.get("student_name"));
                m.put("studentId", row.get("student_no"));
                m.put("className", row.get("class_name"));
                m.put("courseName", row.get("course_name"));
                m.put("regularScore", row.get("regular_score"));   // 平时成绩
                m.put("finalScore", row.get("final_score"));       // 期末成绩
                m.put("totalScore", row.get("score_total"));       // 总评成绩
                m.put("gradePoint", row.get("grade_point"));       // 绩点
                m.put("grade", row.get("grade"));                  // 等级
                m.put("semester", row.get("semester"));
                result.add(m);
            }
        } else if ("warnings".equals(type)) {
            for (Map<String, Object> row : academicWarningMapper.selectWarningsByCollege(collegeId)) {
                Map<String, Object> m = new HashMap<>();
                m.put("studentName", row.get("student_name"));
                m.put("className", row.get("class_name"));
                m.put("description", row.get("description"));
                m.put("warningLevel", row.get("warning_level"));
                m.put("status", intOf(row.get("status")) == 1 ? "已处理" : "待处理");
                result.add(m);
            }
        } else if ("students".equals(type)) {
            List<StudentProfile> all = studentProfileMapper.selectList(new QueryWrapper<>());
            for (StudentProfile sp : all) {
                if (sp.getClassId() == null || !classIds.contains(sp.getClassId())) continue;
                Map<String, Object> m = new HashMap<>();
                m.put("studentId", sp.getStudentId()); m.put("name", sp.getName());
                m.put("className", sp.getClassName());
                result.add(m);
            }
        } else if ("teachers".equals(type)) {
            List<TeacherProfile> all = teacherProfileMapper.selectList(new QueryWrapper<>());
            for (TeacherProfile tp : all) {
                if (tp.getCollegeId() == null || !tp.getCollegeId().equals(collegeId)) continue;
                Map<String, Object> m = new HashMap<>();
                m.put("name", tp.getName()); m.put("title", tp.getTitle());
                m.put("researchAreas", tp.getResearchAreas());
                result.add(m);
            }
        }
        return ApiResponse.success(result);
    }

    /** 教师详情 */
    @GetMapping("/teacher-detail/{userId}/{teacherId}")
    public ApiResponse<Map<String, Object>> getTeacherDetail(@PathVariable Long userId, @PathVariable Long teacherId) {
        Long collegeId = getCollegeId(userId);
        if (collegeId == null) return ApiResponse.error("无权限");
        TeacherProfile tp = teacherProfileMapper.selectById(teacherId);
        if (tp == null || !tp.getCollegeId().equals(collegeId)) return ApiResponse.error("无权限");
        Map<String, Object> d = new HashMap<>();
        d.put("id", tp.getId()); d.put("name", tp.getName()); d.put("title", tp.getTitle());
        d.put("researchAreas", tp.getResearchAreas());
        d.put("collegeId", tp.getCollegeId());

        List<Map<String, Object>> scoreList = new ArrayList<>();
        Set<Long> uniqueCourses = new HashSet<>();
        double total = 0; int sc = 0;
        for (Map<String, Object> row : scoreStatMapper.selectTeacherScoreRows(collegeId, tp.getUserId())) {
            Map<String, Object> sm = new HashMap<>();
            sm.put("studentName", row.get("student_name"));
            sm.put("className", row.get("class_name"));
            sm.put("courseId", row.get("course_id"));
            sm.put("courseName", row.get("course_name"));
            sm.put("regularScore", row.get("regular_score"));
            sm.put("finalScore", row.get("final_score"));
            sm.put("score", row.get("score_total"));
            sm.put("semester", row.get("semester"));
            scoreList.add(sm);
            if (row.get("course_id") != null) {
                uniqueCourses.add(((Number) row.get("course_id")).longValue());
            }
            Double v = row.get("score_total") instanceof Number
                ? ((Number) row.get("score_total")).doubleValue() : null;
            if (v != null) { total += v; sc++; }
        }
        d.put("scores", scoreList);
        d.put("courseCount", uniqueCourses.size());
        d.put("studentCount", scoreList.size());
        d.put("avgScore", sc > 0 ? round1(total / sc) : 0);
        return ApiResponse.success(d);
    }

    /** 标记预警/帮扶状态 */
    @PostMapping("/process-warning")
    public ApiResponse<String> processWarning(@RequestBody Map<String, Object> body) {
        Long warningId = body.get("id") != null ? Long.valueOf(body.get("id").toString()) : null;
        if (warningId == null) return ApiResponse.error("缺少ID");
        AcademicWarning w = academicWarningMapper.selectById(warningId);
        if (w == null) return ApiResponse.error("记录不存在");
        w.setStatus(body.get("status") != null ? Integer.valueOf(body.get("status").toString()) : 1);
        w.setUpdatedAt(java.time.LocalDateTime.now());
        academicWarningMapper.updateById(w);
        return ApiResponse.success("处理成功");
    }

    /** 删除预警记录 */
    @DeleteMapping("/delete-warning/{warningId}")
    public ApiResponse<String> deleteWarning(@PathVariable Long warningId) {
        AcademicWarning w = academicWarningMapper.selectById(warningId);
        if (w == null) return ApiResponse.error("记录不存在");
        academicWarningMapper.deleteById(warningId);
        return ApiResponse.success("已删除");
    }

    /** 班级学生列表 */
    @GetMapping("/class-students/{userId}/{classId}")
    public ApiResponse<List<Map<String, Object>>> getClassStudents(@PathVariable Long userId, @PathVariable Long classId) {
        Long collegeId = getCollegeId(userId);
        if (collegeId == null) return ApiResponse.error("无权限");
        List<StudentProfile> all = studentProfileMapper.selectList(
            new QueryWrapper<StudentProfile>().eq("class_id", classId));
        // 成绩与预警统计一次查出，取代"每个学生查两次库"
        Map<Long, Map<String, Object>> statsByStudent = new HashMap<>();
        for (Map<String, Object> row : scoreStatMapper.selectStudentScoreStats(collegeId)) {
            statsByStudent.put(((Number) row.get("profile_id")).longValue(), row);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (StudentProfile sp : all) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", sp.getId()); m.put("studentId", sp.getStudentId());
            m.put("name", sp.getName()); m.put("className", sp.getClassName());
            Map<String, Object> st = statsByStudent.get(sp.getId());
            m.put("gpa", st == null || st.get("gpa") == null ? null : round2(doubleOf(st.get("gpa"))));
            m.put("totalCourses", st == null ? 0 : intOf(st.get("total_courses")));
            m.put("passedCourses", st == null ? 0 : intOf(st.get("passed_courses")));
            m.put("failedCourses", st == null ? 0 : intOf(st.get("failed_courses")));
            m.put("warningCount", st == null ? 0 : intOf(st.get("warning_count")));
            result.add(m);
        }
        return ApiResponse.success(result);
    }

    /** 成绩查询：按班级/学生筛选全院成绩 */
    @GetMapping("/score-query/{userId}")
    public ApiResponse<List<Map<String, Object>>> scoreQuery(
            @PathVariable Long userId,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) String keyword) {
        Long collegeId = getCollegeId(userId);
        if (collegeId == null) return ApiResponse.error("无权限");
        // 班级与关键字过滤已下推到 SQL；排序也由 SQL 的 ORDER BY 完成
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : scoreStatMapper.selectScoreRows(collegeId, classId, keyword)) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", row.get("id"));
            m.put("studentName", row.get("student_name"));
            m.put("studentId", row.get("student_no"));
            m.put("className", row.get("class_name"));
            m.put("courseName", row.get("course_name"));
            m.put("regularScore", row.get("regular_score"));
            m.put("finalScore", row.get("final_score"));
            m.put("totalScore", row.get("score_total"));
            m.put("gradePoint", row.get("grade_point"));
            m.put("grade", row.get("grade"));
            m.put("semester", row.get("semester"));
            result.add(m);
        }
        return ApiResponse.success(result);
    }
}
