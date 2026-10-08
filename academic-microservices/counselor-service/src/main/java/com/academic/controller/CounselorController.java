package com.academic.controller;

import com.academic.common.constant.ScoreCaliber;
import com.academic.common.dto.ApiResponse;
import com.academic.common.entity.CounselorProfile;
import com.academic.common.entity.AssistancePlan;
import com.academic.common.entity.CommunicationLog;
import com.academic.common.entity.Student;
import com.academic.common.util.UpdateBodyGuard;
import com.academic.common.entity.AcademicWarning;
import com.academic.entity.CounselorClassApplication;
import com.academic.entity.ClassInfo;
import com.academic.service.CounselorService;
import com.academic.service.AssistancePlanService;
import com.academic.service.CommunicationLogService;
import com.academic.service.CounselorNotificationService;
import com.academic.service.StudentService;
import com.academic.service.ClassInfoService;
import com.academic.service.ClassManagementService;
import com.academic.mapper.CounselorClassApplicationMapper;
import com.academic.mapper.ClassInfoMapper;
import com.academic.mapper.ScoreMapper;
import com.academic.mapper.WarningMapper;
import com.academic.mapper.StudentMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * 辅导员控制器
 */
@Slf4j
@RestController
@RequestMapping("/counselors")
public class CounselorController {

    @Autowired
    private CounselorService counselorService;

    @Autowired
    private AssistancePlanService assistancePlanService;

    @Autowired
    private CommunicationLogService communicationLogService;

    // 发通知要在一个事务里写两张表（communication_logs + notifications），所以下沉到 service。
    // 同 ClassManagementService 那条注释：controller 的 catch 在事务边界之外，别拿它当回滚手段。
    @Autowired
    private CounselorNotificationService counselorNotificationService;

    // 处理预警后通知学生。共用实现（含 id 空间转换 + 偏好闸 + 落库），见 academic-common。
    @Autowired
    private com.academic.common.notify.StudentNotifier studentNotifier;

    // 帮扶评价（学生提交、辅导员读）。只读 mapper，不建实体。
    @Autowired
    private com.academic.mapper.AssistanceEvaluationMapper assistanceEvaluationMapper;

    @Autowired
    private StudentService studentService;

    @Autowired
    private CounselorClassApplicationMapper counselorClassApplicationMapper;

    // 申请/审核/解除三条写路径下沉到这个 service：它们各写两张表，必须在一个事务里。
    // 别再把这些写操作挪回 controller —— controller 的 catch 在事务边界之外，
    // 拿它当"错误处理"没问题，拿它当"回滚手段"则不行。
    @Autowired
    private ClassManagementService classManagementService;

    @Autowired
    private ClassInfoService classInfoService;

    @Autowired
    private ClassInfoMapper classInfoMapper;

    @Autowired
    private ScoreMapper scoreMapper;

    @Autowired
    private WarningMapper warningMapper;

    @Autowired
    private StudentMapper studentMapper;

    // ==================== 注册（已删，2026-10-08 W27） ====================
    // 这里原来有一条 `POST /counselors/register`：它只往 `counselor_profiles` 插一行档案，
    // **不建账号、不设口令、不经 `userService.register`**。`counselor_profiles.user_id` 是
    // NOT NULL 但**没有外键**，所以它能插出一行**指向不存在用户**的孤儿档案。
    // 全仓零调用方，真实注册路径是 `POST /api/auth/register/counselor`
    // （`CounselorRegister.vue` 走的就是它），直接删。
    /**
     * 获取辅导员仪表板数据
     */
    @GetMapping("/dashboard/{userId}")
    public ApiResponse<Map<String, Object>> getDashboard(@PathVariable Long userId,
                                                         HttpServletRequest request) {
        ApiResponse<Map<String, Object>> denied =
                denyUnlessSelfCounselor(userId, request, "无权查看其他辅导员的首页");
        if (denied != null) return denied;
        try {
            CounselorProfile counselor = counselorService.getByUserId(userId);
            
            Map<String, Object> dashboard = new HashMap<>();
            
            if (counselor == null) {
                counselor = new CounselorProfile();
                counselor.setUserId(userId);
                counselor.setName("辅导员");
                counselor.setCollegeName("计算机学院");
                counselor.setDepartment("学生工作办公室");
            }
            
            dashboard.put("counselor", counselor);
            
            // 查询真实数据（classes.counselor_id 存的是 users.id，所以用 userId）
            Long counselorId = counselor.getUserId() != null ? counselor.getUserId() : counselor.getId();
            int studentCount = 0;
            int warningCount = 0;
            int redWarnings = 0;
            int yellowWarnings = 0;
            int blueWarnings = 0;
            
            if (counselorId != null) {
                // 查询管理的班级及学生总数
                List<Map<String, Object>> classes = classInfoMapper.getClassesByCounselorIdMap(counselorId);
                if (classes != null) {
                    for (Map<String, Object> c : classes) {
                        Object sc = c.get("studentCount");
                        studentCount += sc != null ? ((Number) sc).intValue() : 0;
                    }
                }
                
                // 查询预警分布
                List<Map<String, Object>> warnDist = warningMapper.getWarningDistributionByCounselor(counselorId);
                if (warnDist != null) {
                    for (Map<String, Object> w : warnDist) {
                        Object level = w.get("warning_level");
                        Object cnt = w.get("count");
                        int c = cnt != null ? ((Number) cnt).intValue() : 0;
                        warningCount += c;
                        if (level != null) {
                            int lv = ((Number) level).intValue();
                            if (lv >= 3) redWarnings += c;
                            else if (lv == 2) yellowWarnings += c;
                            else blueWarnings += c;
                        }
                    }
                }
            }
            
            dashboard.put("studentCount", studentCount);
            dashboard.put("warningCount", warningCount);
            dashboard.put("redWarnings", redWarnings);
            dashboard.put("yellowWarnings", yellowWarnings);
            dashboard.put("blueWarnings", blueWarnings);
            
            // 班级列表
            if (counselorId != null) {
                dashboard.put("classes", classInfoMapper.getClassesByCounselorIdMap(counselorId));
                // 2026-10-01 第二十一轮: 首页每张班级卡片上的「严重/中度/轻度」徽章读的是
                // warnByClass[classId].red/yellow/blue —— 后端原来完全不返回这个 key ⇒ 恒「正常」。
                // 这里按班级把预警分等级装成 map（前端按 classId 取），只含"有预警的班"。
                Map<String, Map<String, Object>> warnByClass = new HashMap<>();
                for (Map<String, Object> row : warningMapper.getWarningLevelCountByClass(counselorId)) {
                    Map<String, Object> lv = new HashMap<>();
                    lv.put("red", intOf(row.get("redCount")));
                    lv.put("yellow", intOf(row.get("yellowCount")));
                    lv.put("blue", intOf(row.get("blueCount")));
                    warnByClass.put(String.valueOf(row.get("classId")), lv);
                }
                dashboard.put("warnByClass", warnByClass);
                // 最近预警
                List<Long> counselorClassIds = classInfoMapper.getClassesByCounselorIdMap(counselorId)
                    .stream().map(c -> (Long) c.get("id")).collect(Collectors.toList());
                List<Long> counselorStudentIds = counselorClassIds.isEmpty() ? new ArrayList<>() :
                    studentMapper.selectList(new QueryWrapper<Student>().in("class_id", counselorClassIds))
                        .stream().map(Student::getId).collect(Collectors.toList());
                List<AcademicWarning> recentWarns = counselorStudentIds.isEmpty() ? new ArrayList<>() :
                    warningMapper.selectList(
                        new QueryWrapper<AcademicWarning>()
                            .in("student_id", counselorStudentIds)
                            .orderByDesc("created_at")
                            .last("LIMIT 5")
                    );
                dashboard.put("recentWarnings", recentWarns);
            } else {
                dashboard.put("classes", java.util.Collections.emptyList());
                dashboard.put("recentWarnings", java.util.Collections.emptyList());
            }
            
            return ApiResponse.success(dashboard);
        } catch (Exception e) {
            log.error("获取仪表板数据失败: {}", e.getMessage());
            return ApiResponse.serverError("获取仪表板数据失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有辅导员列表（支持按学院筛选）
     */
    @GetMapping
    public ApiResponse<List<CounselorProfile>> getAllCounselors(@RequestParam(required = false) Long collegeId) {
        try {
            List<CounselorProfile> counselors = counselorService.getAllCounselors(collegeId);
            return ApiResponse.success(counselors);
        } catch (Exception e) {
            log.error("获取辅导员列表失败: {}", e.getMessage());
            return ApiResponse.serverError("获取辅导员列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取辅导员信息
     */
    @GetMapping("/{counselorId}")
    public ApiResponse<CounselorProfile> getCounselorInfo(@PathVariable String counselorId,
                                                          HttpServletRequest request) {
        try {
            CounselorProfile counselor = counselorService.getByCounselorId(counselorId);
            if (counselor != null) {
                // W13：这里收的是**业务编号字符串**（'C53'），不是 users.id ——
                // 拿 `counselor_profiles.user_id` 去比登录人。
                if (!isAdmin(request)) {
                    Long me = callerId(request);
                    if (me == null || !me.equals(counselor.getUserId())) {
                        return ApiResponse.error(403, "无权查看其他辅导员的资料");
                    }
                }
                return ApiResponse.success(counselor);
            } else {
                return ApiResponse.notFound("辅导员不存在");
            }
        } catch (Exception e) {
            log.error("获取辅导员信息失败: {}", e.getMessage());
            return ApiResponse.serverError("获取辅导员信息失败: " + e.getMessage());
        }
    }

    /**
     * 获取辅导员管理的学生列表（查询参数方式）
     */
    @GetMapping("/students")
    public ApiResponse<List<Student>> getStudentsByParam(@RequestParam("counselor_id") Long counselorId,
                                                         HttpServletRequest request) {
        ApiResponse<List<Student>> denied =
                denyUnlessSelfCounselor(counselorId, request, "无权查看其他辅导员的学生");
        if (denied != null) return denied;
        try {
            List<Student> students = studentService.getByCounselorId(counselorId);
            return ApiResponse.success(students);
        } catch (Exception e) {
            log.error("获取学生列表失败: {}", e.getMessage());
            return ApiResponse.serverError("获取学生列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取预警列表
     */
    @GetMapping("/warnings")
    public ApiResponse<List<Map<String, Object>>> getWarnings(@RequestParam("counselor_id") Long counselorId,
                                                 @RequestParam(value = "status", required = false) String status,
                                                 HttpServletRequest request) {
        ApiResponse<List<Map<String, Object>>> denied =
                denyUnlessSelfCounselor(counselorId, request, "无权查看其他辅导员的预警");
        if (denied != null) return denied;
        try {
            List<Map<String, Object>> warnings = new ArrayList<>();
            // 获取辅导员管理的班级
            List<Map<String, Object>> classes = classInfoService.getClassesByCounselorId(counselorId);
            if (classes.isEmpty()) {
                return ApiResponse.success(warnings);
            }
            
            // 构建 classId -> className 映射
            Map<Long, String> classMap = new HashMap<>();
            StringBuilder classIds = new StringBuilder();
            for (int i = 0; i < classes.size(); i++) {
                Map<String, Object> clazz = classes.get(i);
                Long classId = (Long) clazz.get("id");
                classMap.put(classId, (String) clazz.get("name"));
                classIds.append(classId);
                if (i < classes.size() - 1) classIds.append(",");
            }

            // 查询学生档案，构建 studentId -> className 映射
            List<Long> classIdList = classes.stream()
                .map(c -> (Long) c.get("id")).collect(Collectors.toList());
            Map<Long, String> studentClassMap = new HashMap<>();
            List<Student> profiles =
                studentMapper.selectList(
                    new QueryWrapper<Student>()
                        .in("class_id", classIdList));
            if (profiles != null) {
                for (Student sp : profiles) {
                    String cname = classMap.get(sp.getClassId());
                    if (cname != null) {
                        studentClassMap.put(sp.getId(), cname);
                    }
                }
            }

            // 查询这些班级学生的预警
            QueryWrapper<AcademicWarning> qw = new QueryWrapper<>();
            List<Long> classStudents = profiles != null ? profiles.stream()
                .map(Student::getId).collect(Collectors.toList()) : Collections.emptyList();
            qw.in(classStudents.isEmpty() ? "student_id" : "student_id", 
                  classStudents.isEmpty() ? Collections.singletonList(-1L) : classStudents);
            if (status != null && !status.isEmpty()) {
                qw.eq("status", Integer.parseInt(status));
            }
            qw.orderByDesc("created_at");

            List<AcademicWarning> result = warningMapper.selectList(qw);
            for (AcademicWarning w : result) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", w.getId());
                item.put("studentId", w.getStudentId());
                item.put("studentName", w.getStudentName());
                item.put("warningLevel", w.getWarningLevel());
                item.put("description", w.getDescription());
                item.put("status", w.getStatus());
                item.put("createdAt", w.getCreatedAt());
                item.put("className", studentClassMap.getOrDefault(w.getStudentId(), "未知班级"));
                // 2026-10-02（W4.4）：PC 预警页的「详情」原来只弹一句 toast
                // （`ElMessage.info('X的预警详情')`），因为这个列表**只回了上面那 8 个 key**，
                // 页面就算想显示也别无数据。这几个列本来就在 `academic_warning` 表里、
                // 实体上也有 getter，只是没被塞进 map。扩宽投影，让详情弹窗有真东西可显示。
                item.put("title", w.getTitle());
                item.put("failCount", w.getFailCount());
                item.put("warningMessage", w.getWarningMessage());
                item.put("handledBy", w.getHandledBy());
                item.put("handleResult", w.getHandleResult());
                item.put("handledAt", w.getHandledAt());
                item.put("updatedAt", w.getUpdatedAt());
                warnings.add(item);
            }
            return ApiResponse.success(warnings);
        } catch (Exception e) {
            log.error("获取预警列表失败: {}", e.getMessage());
            return ApiResponse.serverError("获取预警列表失败: " + e.getMessage());
        }
    }

    /**
     * 更新辅导员信息
     */
    @PutMapping("/{id}")
    public ApiResponse<String> updateCounselorInfo(@PathVariable Long id, @RequestBody CounselorProfile counselorProfile,
                                                   HttpServletRequest request) {
        // W13：路径参数是 `counselor_profiles.id`（活库 1，与 users.id=53 不是一套），
        // 原来这里**只过了请求体守卫、没有任何身份检查** —— 任何辅导员都能改别人的档案。
        // 改成拿档案行的 `user_id` 比登录人。
        if (!isAdmin(request)) {
            Long me = callerId(request);
            CounselorProfile target = counselorService.getById(id);
            if (me == null || target == null || !me.equals(target.getUserId())) {
                return ApiResponse.error(403, "无权修改其他辅导员的资料");
            }
        }
        try {
            counselorProfile.setId(id);
            boolean success = counselorService.updateProfile(counselorProfile);
            if (success) {
                return ApiResponse.success("辅导员信息更新成功");
            } else {
                return ApiResponse.notFound("辅导员不存在");
            }
        } catch (Exception e) {
            log.error("更新辅导员信息失败: {}", e.getMessage());
            return ApiResponse.serverError("更新辅导员信息失败: " + e.getMessage());
        }
    }

    /**
     * 删除辅导员信息
     */
    @DeleteMapping("/{id}")
    public ApiResponse<String> deleteCounselorInfo(@PathVariable Long id,
                                                    HttpServletRequest request) {
        Long requestUserId = getUserIdFromGateway(request);
        CounselorProfile profile = counselorService.getByUserId(id);
        if (profile == null || profile.getUserId() == null
                || !profile.getUserId().equals(requestUserId)) {
            // 只有本人才能删除自己的信息
            if (!isAdmin(request)) {
                return ApiResponse.error(403, "无权删除");
            }
        }
        try {
            boolean success = counselorService.deleteProfile(id);
            if (success) {
                return ApiResponse.success("辅导员信息删除成功");
            } else {
                return ApiResponse.notFound("辅导员不存在");
            }
        } catch (Exception e) {
            log.error("删除辅导员信息失败: {}", e.getMessage());
            return ApiResponse.serverError("删除辅导员信息失败: " + e.getMessage());
        }
    }

    /**
     * 创建帮扶计划
     */
    @PostMapping("/assistance-plans")
    public ApiResponse<AssistancePlan> createAssistancePlan(@RequestBody AssistancePlan plan,
                                                            HttpServletRequest request) {
        // W13：`plan.counselorId` 是请求体里给的 —— 不核对就能**以别人的名义建计划**。
        ApiResponse<AssistancePlan> denied =
                denyUnlessSelfCounselor(plan == null ? null : plan.getCounselorId(), request,
                        "无权以其他辅导员的名义创建帮扶计划");
        if (denied != null) return denied;
        try {
            boolean success = assistancePlanService.createPlan(plan);
            if (success) {
                return ApiResponse.success(plan);
            } else {
                return ApiResponse.badRequest("帮扶计划创建失败");
            }
        } catch (Exception e) {
            log.error("创建帮扶计划失败: {}", e.getMessage());
            return ApiResponse.serverError("创建帮扶计划失败: " + e.getMessage());
        }
    }

    /**
     * 获取学生的帮扶计划列表
     */
    @GetMapping("/students/{studentId}/assistance-plans")
    public ApiResponse<List<AssistancePlan>> getStudentAssistancePlans(@PathVariable Long studentId,
                                                                      HttpServletRequest request) {
        ApiResponse<List<AssistancePlan>> denied =
                denyUnlessMyStudent(studentId, request, "该学生不在你带的班级里");
        if (denied != null) return denied;
        try {
            List<AssistancePlan> plans = assistancePlanService.getPlansByStudentId(studentId);
            return ApiResponse.success(plans);
        } catch (Exception e) {
            log.error("获取学生帮扶计划列表失败: {}", e.getMessage());
            return ApiResponse.serverError("获取学生帮扶计划列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取辅导员的帮扶计划列表
     */
    @GetMapping("/{counselorId}/assistance-plans")
    public ApiResponse<List<AssistancePlan>> getCounselorAssistancePlans(@PathVariable Long counselorId,
                                                                        HttpServletRequest request) {
        ApiResponse<List<AssistancePlan>> denied =
                denyUnlessSelfCounselor(counselorId, request, "无权查看其他辅导员的帮扶计划");
        if (denied != null) return denied;
        try {
            // W13 顺带修：这里原来调的是 `assistancePlanService.list()` —— **收了 counselorId
            // 却从没用过**，回的是全库**所有辅导员**的帮扶计划。接口名字说"我的"，内容是"大家的"。
            // 参数既然已经校验过是本人，就按它过滤。
            List<AssistancePlan> plans = assistancePlanService.list(
                    new QueryWrapper<AssistancePlan>().eq("counselor_id", counselorId));
            return ApiResponse.success(plans);
        } catch (Exception e) {
            log.error("获取辅导员帮扶计划列表失败: {}", e.getMessage());
            return ApiResponse.serverError("获取辅导员帮扶计划列表失败: " + e.getMessage());
        }
    }

    /**
     * 某条帮扶计划收到的学生评价。
     *
     * <p>{@code FEATURES.md} 只写了 {@code POST .../evaluation}（辅导员提交评估），
     * 而这张表存的是**学生对计划的评价**，写入方在 student-service（唯一），
     * 辅导员需要的是**读**。理由见
     * {@link com.academic.mapper.AssistanceEvaluationMapper} 的类注释。
     *
     * @param planId {@code assistance_plans.id}
     */
    @GetMapping("/assistance-plans/{planId}/evaluations")
    public ApiResponse<List<Map<String, Object>>> getPlanEvaluations(@PathVariable Long planId,
                                                                    HttpServletRequest request) {
        ApiResponse<List<Map<String, Object>>> denied =
                denyUnlessMyPlan(planId, request, "无权查看其他辅导员帮扶计划的评价");
        if (denied != null) return denied;
        try {
            return ApiResponse.success(assistanceEvaluationMapper.selectByPlanId(planId));
        } catch (Exception e) {
            log.error("获取帮扶计划评价失败: planId={}, {}", planId, e.getMessage());
            return ApiResponse.serverError("获取帮扶计划评价失败: " + e.getMessage());
        }
    }

    /**
     * 某辅导员名下所有帮扶计划收到的学生评价（汇总，供辅导员页展示）。
     *
     * @param counselorId {@code users.id}（{@code assistance_plans.counselor_id}）
     */
    @GetMapping("/{counselorId}/evaluations")
    public ApiResponse<List<Map<String, Object>>> getCounselorEvaluations(@PathVariable Long counselorId,
                                                                         HttpServletRequest request) {
        ApiResponse<List<Map<String, Object>>> denied =
                denyUnlessSelfCounselor(counselorId, request, "无权查看其他辅导员的帮扶评价");
        if (denied != null) return denied;
        try {
            return ApiResponse.success(assistanceEvaluationMapper.selectByCounselorId(counselorId));
        } catch (Exception e) {
            log.error("获取辅导员帮扶评价失败: counselorId={}, {}", counselorId, e.getMessage());
            return ApiResponse.serverError("获取辅导员帮扶评价失败: " + e.getMessage());
        }
    }

    /**
     * 更新帮扶计划
     */
    @PutMapping("/assistance-plans/{id}")
    public ApiResponse<String> updateAssistancePlan(@PathVariable Long id, @RequestBody AssistancePlan plan,
                                                    HttpServletRequest request) {
        ApiResponse<String> denied = denyUnlessMyPlan(id, request, "无权修改其他辅导员的帮扶计划");
        if (denied != null) return denied;
        try {
            plan.setId(id);
            boolean success = assistancePlanService.updatePlan(plan);
            if (success) {
                return ApiResponse.success("帮扶计划更新成功");
            } else {
                return ApiResponse.notFound("帮扶计划不存在");
            }
        } catch (Exception e) {
            log.error("更新帮扶计划失败: {}", e.getMessage());
            return ApiResponse.serverError("更新帮扶计划失败: " + e.getMessage());
        }
    }

    /**
     * 删除帮扶计划
     */
    @DeleteMapping("/assistance-plans/{id}")
    public ApiResponse<String> deleteAssistancePlan(@PathVariable Long id,
                                                     HttpServletRequest request) {
        Long requestUserId = getUserIdFromGateway(request);
        AssistancePlan plan = assistancePlanService.getById(id);
        // 2026-10-04（W12）：这里原来比的是 `profile.getId()`，而
        // `assistance_plans.counselor_id` 存的是 **users.id**（活库 4 行全是 53），
        // `counselor_profiles.id` 是 **1** —— 两套 id 空间不相交 ⇒ `53.equals(1)` 恒假
        // ⇒ **连本人删自己的计划也被判 403**。这是"闸装反了"，不是"更安全"。
        // 现在直接与网关注入的 users.id 比（同一列的口径与 classes.counselor_id 一致）。
        if (plan != null && plan.getCounselorId() != null
                && !plan.getCounselorId().equals(requestUserId)) {
            if (!isAdmin(request)) {
                return ApiResponse.error(403, "无权删除其他辅导员的帮扶计划");
            }
        }
        try {
            boolean success = assistancePlanService.deletePlan(id);
            if (success) {
                return ApiResponse.success("帮扶计划删除成功");
            } else {
                return ApiResponse.notFound("帮扶计划不存在");
            }
        } catch (Exception e) {
            log.error("删除帮扶计划失败: {}", e.getMessage());
            return ApiResponse.serverError("删除帮扶计划失败: " + e.getMessage());
        }
    }

    /**
     * 更新帮扶计划状态
     */
    @PatchMapping("/assistance-plans/{id}/status")
    public ApiResponse<String> updateAssistancePlanStatus(@PathVariable Long id, @RequestBody Map<String, Object> statusMap,
                                                          HttpServletRequest request) {
        ApiResponse<String> denied = denyUnlessMyPlan(id, request, "无权修改其他辅导员的帮扶计划");
        if (denied != null) return denied;
        try {
            Object raw = statusMap.get("status");
            if (raw == null) {
                return ApiResponse.badRequest("缺少 status 字段");
            }
            boolean success = assistancePlanService.updatePlanStatus(id, raw.toString());
            if (success) {
                return ApiResponse.success("帮扶计划状态更新成功");
            } else {
                return ApiResponse.notFound("帮扶计划不存在");
            }
        } catch (IllegalArgumentException e) {
            // 状态值非法属于客户端问题，返回 400 而不是 500
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("更新帮扶计划状态失败: {}", e.getMessage());
            return ApiResponse.serverError("更新帮扶计划状态失败: " + e.getMessage());
        }
    }

    /**
     * 更新帮扶计划进度（0~100）。
     *
     * <p>与 {@code /status} 分开：状态是枚举，进度是百分比。此前只有 status 一个入口，
     * 学生端把百分比塞进 status 写库，辅导员端把中文/英文状态名塞进去触发
     * {@code NumberFormatException}。
     */
    @PatchMapping("/assistance-plans/{id}/progress")
    public ApiResponse<String> updateAssistancePlanProgress(@PathVariable Long id,
                                                            @RequestBody Map<String, Object> body,
                                                            HttpServletRequest request) {
        ApiResponse<String> denied = denyUnlessMyPlan(id, request, "无权修改其他辅导员的帮扶计划");
        if (denied != null) return denied;
        try {
            Object raw = body.get("progress");
            if (raw == null) {
                return ApiResponse.badRequest("缺少 progress 字段");
            }
            boolean success = assistancePlanService.updatePlanProgress(
                    id, new java.math.BigDecimal(raw.toString().trim()));
            return success ? ApiResponse.success("帮扶计划进度更新成功")
                           : ApiResponse.notFound("帮扶计划不存在");
        } catch (NumberFormatException e) {
            return ApiResponse.badRequest("进度必须是数字");
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("更新帮扶计划进度失败: {}", e.getMessage());
            return ApiResponse.serverError("更新帮扶计划进度失败: " + e.getMessage());
        }
    }

    /**
     * 创建沟通记录
     */
    @PostMapping("/communication-logs")
    public ApiResponse<CommunicationLog> createCommunicationLog(@RequestBody CommunicationLog communicationLog,
                                                                HttpServletRequest request) {
        // W13：与建帮扶计划同型 —— `communicationLog.counselorId` 来自请求体，不核对就能
        // **以别人的名义写沟通记录**（而沟通记录里带着学生姓名与谈话内容）。
        ApiResponse<CommunicationLog> denied = denyUnlessSelfCounselor(
                communicationLog == null ? null : communicationLog.getCounselorId(), request,
                "无权以其他辅导员的名义创建沟通记录");
        if (denied != null) return denied;
        try {
            boolean success = communicationLogService.createLog(communicationLog);
            if (success) {
                return ApiResponse.success(communicationLog);
            } else {
                return ApiResponse.badRequest("沟通记录创建失败");
            }
        } catch (Exception e) {
            log.error("创建沟通记录失败: {}", e.getMessage());
            return ApiResponse.serverError("创建沟通记录失败: " + e.getMessage());
        }
    }

    /**
     * 获取学生的沟通记录列表
     */
    @GetMapping("/students/{studentId}/communication-logs")
    public ApiResponse<List<CommunicationLog>> getStudentCommunicationLogs(@PathVariable Long studentId,
                                                                          HttpServletRequest request) {
        ApiResponse<List<CommunicationLog>> denied =
                denyUnlessMyStudent(studentId, request, "该学生不在你带的班级里");
        if (denied != null) return denied;
        try {
            List<CommunicationLog> logs = communicationLogService.getLogsByStudentId(studentId);
            return ApiResponse.success(logs);
        } catch (Exception e) {
            log.error("获取学生沟通记录列表失败: {}", e.getMessage());
            return ApiResponse.serverError("获取学生沟通记录列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取辅导员的沟通记录列表
     */
    @GetMapping("/{counselorId}/communication-logs")
    public ApiResponse<List<CommunicationLog>> getCounselorCommunicationLogs(@PathVariable Long counselorId,
                                                                            HttpServletRequest request) {
        ApiResponse<List<CommunicationLog>> denied =
                denyUnlessSelfCounselor(counselorId, request, "无权查看其他辅导员的沟通记录");
        if (denied != null) return denied;
        try {
            List<CommunicationLog> logs = communicationLogService.getLogsByCounselorId(counselorId);
            return ApiResponse.success(logs);
        } catch (Exception e) {
            log.error("获取辅导员沟通记录列表失败: {}", e.getMessage());
            return ApiResponse.serverError("获取辅导员沟通记录列表失败: " + e.getMessage());
        }
    }

    /**
     * 更新沟通记录
     */
    @PutMapping("/communication-logs/{id}")
    public ApiResponse<String> updateCommunicationLog(@PathVariable Long id, @RequestBody CommunicationLog communicationLog,
                                                      HttpServletRequest request) {
        ApiResponse<String> denied = denyUnlessMyLog(id, request, "无权修改其他辅导员的沟通记录");
        if (denied != null) return denied;
        try {
            communicationLog.setId(id);
            boolean success = communicationLogService.updateLog(communicationLog);
            if (success) {
                return ApiResponse.success("沟通记录更新成功");
            } else {
                return ApiResponse.notFound("沟通记录不存在");
            }
        } catch (Exception e) {
            log.error("更新沟通记录失败: {}", e.getMessage());
            return ApiResponse.serverError("更新沟通记录失败: " + e.getMessage());
        }
    }

    /**
     * 删除沟通记录
     */
    @DeleteMapping("/communication-logs/{id}")
    public ApiResponse<String> deleteCommunicationLog(@PathVariable Long id,
                                                       HttpServletRequest request) {
        Long requestUserId = getUserIdFromGateway(request);
        CommunicationLog record = communicationLogService.getById(id);
        // 同上（W12）：`communication_logs.counselor_id` 也是 users.id（活库 2001-2003 全是 53）。
        if (record != null && record.getCounselorId() != null
                && !record.getCounselorId().equals(requestUserId)) {
            if (!isAdmin(request)) {
                return ApiResponse.error(403, "无权删除其他辅导员的沟通记录");
            }
        }
        try {
            boolean success = communicationLogService.deleteLog(id);
            if (success) {
                return ApiResponse.success("沟通记录删除成功");
            } else {
                return ApiResponse.notFound("沟通记录不存在");
            }
        } catch (Exception e) {
            log.error("删除沟通记录失败: {}", e.getMessage());
            return ApiResponse.serverError("删除沟通记录失败: " + e.getMessage());
        }
    }

    /**
     * 获取辅导员管理的学生列表
     */
    @GetMapping("/{counselorId}/students")
    public ApiResponse<List<Student>> getCounselorStudents(@PathVariable Long counselorId,
                                                           HttpServletRequest request) {
        ApiResponse<List<Student>> denied =
                denyUnlessSelfCounselor(counselorId, request, "无权查看其他辅导员的学生");
        if (denied != null) return denied;
        try {
            List<Student> students = studentService.getByCounselorId(counselorId);
            return ApiResponse.success(students);
        } catch (Exception e) {
            log.error("获取辅导员管理的学生列表失败: {}", e.getMessage());
            return ApiResponse.serverError("获取辅导员管理的学生列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取学生详情
     */
    @GetMapping("/students/{studentId}")
    public ApiResponse<Student> getStudentInfo(@PathVariable Long studentId,
                                               HttpServletRequest request) {
        ApiResponse<Student> denied = denyUnlessMyStudent(studentId, request, "该学生不在你带的班级里");
        if (denied != null) return denied;
        try {
            Student student = studentService.getById(studentId);
            if (student != null) {
                return ApiResponse.success(student);
            } else {
                return ApiResponse.notFound("学生不存在");
            }
        } catch (Exception e) {
            log.error("获取学生详情失败: {}", e.getMessage());
            return ApiResponse.serverError("获取学生详情失败: " + e.getMessage());
        }
    }

    /**
     * 更新学生信息
     */
    @PutMapping("/students/{studentId}")
    public ApiResponse<String> updateStudentInfo(@PathVariable Long studentId, @RequestBody Student student,
                                                 HttpServletRequest request) {
        ApiResponse<String> denied = denyUnlessMyStudent(studentId, request, "该学生不在你带的班级里");
        if (denied != null) return denied;
        try {
            student.setId(studentId);
            // W8：空请求体 ⇒ 无 SET 子句 ⇒ `UPDATE student_profile    WHERE id=?` ⇒ SQL 语法错。
            // 改前这里会回 **HTTP 200 + code 500**，并把上面那行 SQL 与 MyBatis 内部信息
            // 一起塞进 message 返回给客户端；现在如实回 400。
            if (!UpdateBodyGuard.hasUpdatableField(student)) {
                return ApiResponse.badRequest("请求体里没有任何可更新的字段");
            }
            boolean success = studentService.updateById(student);
            if (success) {
                return ApiResponse.success("学生信息更新成功");
            } else {
                return ApiResponse.notFound("学生不存在");
            }
        } catch (Exception e) {
            log.error("更新学生信息失败: {}", e.getMessage());
            return ApiResponse.serverError("更新学生信息失败: " + e.getMessage());
        }
    }

    /**
     * 获取辅导员管理的班级列表
     */
    @GetMapping("/class-management/my-classes")
    public ApiResponse<List<Map<String, Object>>> getMyClasses(@RequestParam("counselorId") Long counselorId,
                                                               HttpServletRequest request) {
        ApiResponse<List<Map<String, Object>>> denied =
                denyUnlessSelfCounselor(counselorId, request, "无权查看其他辅导员的班级");
        if (denied != null) return denied;
        try {
            List<Map<String, Object>> classes = classInfoService.getClassesByCounselorId(counselorId);
            return ApiResponse.success(classes);
        } catch (Exception e) {
            log.error("获取班级列表失败: {}", e.getMessage());
            return ApiResponse.serverError("获取班级列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取班级申请列表
     */
    @GetMapping("/class-management/requests")
    public ApiResponse<List<Map<String, Object>>> getClassRequests(@RequestParam("counselorId") Long counselorId,
                                                                   HttpServletRequest request) {
        ApiResponse<List<Map<String, Object>>> denied =
                denyUnlessSelfCounselor(counselorId, request, "无权查看其他辅导员的班级申请");
        if (denied != null) return denied;
        try {
            List<CounselorClassApplication> apps = counselorClassApplicationMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CounselorClassApplication>()
                    .eq("counselor_id", counselorId).orderByDesc("create_time")
            );
            List<Map<String, Object>> result = new ArrayList<>();
            for (CounselorClassApplication app : apps) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", app.getId());
                m.put("classId", app.getClassId());
                m.put("className", app.getClassName());
                m.put("reason", app.getReason());
                m.put("status", app.getStatus());
                m.put("applicationType", app.getApplicationType());
                // 2026-10-02（W1.14）：拒绝原因回读。这个 Map 是**手写的**，
                // 不 put 就不会出现在响应里 —— 实体上有字段≠前端收得到。
                m.put("rejectReason", app.getRejectReason());
                m.put("createTime", app.getCreateTime());
                result.add(m);
            }
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("获取班级申请列表失败: {}", e.getMessage());
            return ApiResponse.serverError("获取班级申请列表失败: " + e.getMessage());
        }
    }

    /**
     * 搜索班级
     */
    @GetMapping("/class-management/search")
    public ApiResponse<List<Map<String, Object>>> searchClasses(@RequestParam(required = false) String keyword) {
        try {
            List<Map<String, Object>> classes = classInfoService.searchClasses(keyword);
            return ApiResponse.success(classes);
        } catch (Exception e) {
            log.error("搜索班级失败: {}", e.getMessage());
            return ApiResponse.serverError("搜索班级失败: " + e.getMessage());
        }
    }

    /**
     * 获取无辅导员的班级
     */
    @GetMapping("/class-management/without-counselor")
    public ApiResponse<List<Map<String, Object>>> getClassesWithoutCounselor() {
        try {
            List<Map<String, Object>> classes = classInfoService.getClassesWithoutCounselor();
            return ApiResponse.success(classes);
        } catch (Exception e) {
            log.error("获取无辅导员班级失败: {}", e.getMessage());
            return ApiResponse.serverError("获取无辅导员班级失败: " + e.getMessage());
        }
    }

    /**
     * 申请管理班级（申请管理或解除管理）
     *
     * <p>判断与写库都在 {@link ClassManagementService#applyForClass}，那边带事务 ——
     * 这条路径会写 counselor_profile（getByUserId 见到缺失记录会插一行）与
     * counselor_class_application 两张表，半截落库就是"申请单在、人却对不上号"。
     * 业务拒绝以返回值回来，这里的 catch 在事务边界之外，只负责把 DB 异常转成提示。
     */
    @PostMapping("/class-management/apply")
    public ApiResponse<String> applyForClass(@RequestBody Map<String, Object> request,
                                             HttpServletRequest httpRequest) {
        try {
            Long counselorId = ((Number) request.get("counselorId")).longValue();
            Long classId = ((Number) request.get("classId")).longValue();
            String className = (String) request.get("className");
            String reason = (String) request.get("reason");

            // W13：`counselorId` 来自请求体 —— 不核对就能**以别的辅导员的名义提交申请**
            // （批准后班就记在他名下了）。
            ApiResponse<String> denied = denyUnlessSelfCounselor(
                    counselorId, httpRequest, "无权以其他辅导员的名义提交班级申请");
            if (denied != null) return denied;

            // 获取申请类型，默认为0（申请管理）
            Integer applicationType = 0;
            if (request.get("applicationType") != null) {
                applicationType = ((Number) request.get("applicationType")).intValue();
            }

            String errorMessage = classManagementService.applyForClass(
                    counselorId, classId, className, reason, applicationType);
            if (errorMessage != null) {
                return ApiResponse.error(errorMessage);
            }

            String message = applicationType == 1 ? "解除申请提交成功，等待审核" : "申请提交成功，等待审核";
            return ApiResponse.success(message);
        } catch (Exception e) {
            log.error("申请班级失败: {}", e.getMessage());
            return ApiResponse.serverError("申请班级失败: " + e.getMessage());
        }
    }

    /**
     * 解除班级管理权限
     *
     * <p>写库在 {@link ClassManagementService#cancelClassManagement}（带事务）：这条路径
     * 先清 {@code classes.counselor_id}、再把申请单置为"已取消"，两步之间失败就成了
     * "班级没人管、申请单还挂着已通过"。
     */
    @PostMapping("/class-management/cancel")
    public ApiResponse<String> cancelClassManagement(@RequestBody Map<String, Object> request,
                                                     HttpServletRequest httpRequest) {
        try {
            // 前端传递的是用户ID（User.id）
            Long userId = ((Number) request.get("counselorId")).longValue();
            Long classId = ((Number) request.get("classId")).longValue();

            // W13：同 apply —— 不核对就能**解除别人对某班的管理**（写的是 classes.counselor_id）。
            ApiResponse<String> denied = denyUnlessSelfCounselor(
                    userId, httpRequest, "无权解除其他辅导员的班级管理");
            if (denied != null) return denied;

            String errorMessage = classManagementService.cancelClassManagement(userId, classId);
            if (errorMessage != null) {
                return ApiResponse.error(errorMessage);
            }
            return ApiResponse.success("班级管理权限已解除");
        } catch (Exception e) {
            log.error("解除班级管理权限失败: {}", e.getMessage());
            return ApiResponse.serverError("解除班级管理权限失败: " + e.getMessage());
        }
    }

    /**
     * 获取辅导员班级申请列表（供管理员使用）
     */
    @GetMapping("/class-management/applications")
    public ApiResponse<List<CounselorClassApplication>> getCounselorApplications(
            @RequestParam(value = "status", required = false) Integer status,
            HttpServletRequest request) {
        ApiResponse<List<CounselorClassApplication>> denied =
                denyUnlessAdmin(request, "仅管理员可查看全部班级申请");
        if (denied != null) return denied;
        try {
            List<CounselorClassApplication> applications;
            if (status != null) {
                applications = counselorClassApplicationMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CounselorClassApplication>()
                        .eq("status", status)
                        .orderByDesc("create_time")
                );
            } else {
                applications = counselorClassApplicationMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CounselorClassApplication>()
                        .orderByDesc("create_time")
                );
            }
            return ApiResponse.success(applications);
        } catch (Exception e) {
            log.error("获取辅导员班级申请失败: {}", e.getMessage());
            return ApiResponse.serverError("获取辅导员班级申请失败: " + e.getMessage());
        }
    }

    /**
     * 审核辅导员班级申请（供管理员使用）
     *
     * <p>写库在 {@link ClassManagementService#reviewApplication}（带事务）：批准时先改申请单
     * 状态、再把班级交给辅导员，两步不是原子的话会留下"申请已批准、班级还没交出去"。
     */
    @PostMapping("/class-management/applications/{id}/review")
    public ApiResponse<String> reviewCounselorApplication(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        // W13：这是**审批**口，只有管理员能进 —— 见 denyUnlessAdmin 的注释（辅导员自批提权）。
        ApiResponse<String> denied = denyUnlessAdmin(httpRequest, "仅管理员可审核班级申请");
        if (denied != null) return denied;
        try {
            Integer status = (Integer) request.get("status");
            // 2026-10-02（W1.14）：拒绝理由。前端此前发的是 `{status}`，
            // 与管理端 `POST /admin/class-management/reject/{id}?type=counselor` 是同一动作的两条实现，
            // 理由两边都要能存 —— 只修一边的话，"从哪个入口拒的"会决定理由留不留得住。
            Object rawReason = request.get("reason");
            String rejectReason = rawReason == null ? null : String.valueOf(rawReason);

            String errorMessage = classManagementService.reviewApplication(id, status, rejectReason);
            if (errorMessage != null) {
                return ApiResponse.error(errorMessage);
            }

            return ApiResponse.success(status == 1 ? "申请已批准" : "申请已拒绝");
        } catch (Exception e) {
            log.error("审核辅导员班级申请失败: {}", e.getMessage());
            return ApiResponse.serverError("审核辅导员班级申请失败: " + e.getMessage());
        }
    }

    /**
     * 获取学分监控数据。
     *
     * <p>原实现把"学分不足"写死为 0、"达标"写死为全部学生，{@code classMonitors} /
     * {@code excellentClasses} / {@code lowClasses} 三个字段干脆没有返回，
     * 于是前端"班级达标率对比"表永远是空的、"优秀班级/风险班级"恒为 0。
     * 这里全部改为由 {@link #buildClassMonitors} 的真实统计得出。
     */
    @GetMapping("/credit-monitor")
    public ApiResponse<Map<String, Object>> getCreditMonitor(@RequestParam("counselor_id") Long counselorId,
                                                             HttpServletRequest request) {
        ApiResponse<Map<String, Object>> denied =
                denyUnlessSelfCounselor(counselorId, request, "无权查看其他辅导员的学分监控");
        if (denied != null) return denied;
        try {
            List<Map<String, Object>> classMonitors = buildClassMonitors(counselorId);

            int totalStudents = 0;
            int gradedStudents = 0;
            int achievementStudents = 0;
            int excellentClasses = 0;
            int lowClasses = 0;
            for (Map<String, Object> clazz : classMonitors) {
                totalStudents += intOf(clazz.get("studentCount"));
                gradedStudents += intOf(clazz.get("gradedStudentCount"));
                achievementStudents += intOf(clazz.get("creditAchievementCount"));
                double rate = doubleOf(clazz.get("achievementRate"));
                if (rate >= 80) excellentClasses++;
                if (rate < 60) lowClasses++;
            }

            Map<String, Object> creditData = new HashMap<>();
            creditData.put("totalStudents", totalStudents);
            creditData.put("gradedStudents", gradedStudents);
            creditData.put("creditSufficient", achievementStudents);
            creditData.put("creditInsufficient", gradedStudents - achievementStudents);
            creditData.put("insufficientRate", gradedStudents > 0
                    ? Math.round((double) (gradedStudents - achievementStudents) / gradedStudents * 1000) / 10.0
                    : 0);
            creditData.put("excellentClasses", excellentClasses);
            creditData.put("lowClasses", lowClasses);
            // 2026-10-01 第二十一轮: 前端「班级总数」「整体达标率」两个盒子读的是
            // totalClasses / overallAchievementRate —— 后端从来不给这两个 key ⇒ 恒 0 / 恒 0%。
            // 两者都能从上面已经算好的数直接得出, 不需要任何新查询。
            creditData.put("totalClasses", classMonitors.size());
            creditData.put("overallAchievementRate", gradedStudents > 0
                    ? Math.round((double) achievementStudents / gradedStudents * 1000) / 10.0
                    : 0);
            creditData.put("classMonitors", classMonitors);
            return ApiResponse.success(creditData);
        } catch (Exception e) {
            log.error("获取学分监控数据失败: {}", e.getMessage());
            return ApiResponse.serverError("获取学分监控数据失败: " + e.getMessage());
        }
    }

    /**
     * 各班达标情况，列名对齐前端 {@code CounselorCreditMonitor.vue} 的取值。
     * "达标" = 已修课程全部通过（见 {@code ScoreMapper.getClassScoreStatsByCounselorId}）。
     */
    private List<Map<String, Object>> buildClassMonitors(Long counselorId) {
        List<Map<String, Object>> stats = scoreMapper.getClassScoreStatsByCounselorId(counselorId);
        List<Map<String, Object>> monitors = new ArrayList<>();
        for (Map<String, Object> stat : stats) {
            Map<String, Object> item = new HashMap<>();
            item.put("classId", stat.get("class_id"));
            item.put("className", stat.get("name"));
            item.put("studentCount", intOf(stat.get("student_count")));
            item.put("gradedStudentCount", intOf(stat.get("graded_student_count")));
            item.put("creditAchievementCount", intOf(stat.get("credit_achievement_count")));
            item.put("failStudentCount", intOf(stat.get("fail_student_count")));
            // 无成绩记录的新生不计入分母，否则达标率被"还没考试的人"抬高
            item.put("achievementRate", stat.get("achievement_rate") == null
                    ? 0.0 : doubleOf(stat.get("achievement_rate")));
            item.put("avgScore", stat.get("avg_score") == null ? 0.0 : doubleOf(stat.get("avg_score")));
            monitors.add(item);
        }
        return monitors;
    }

    private static int intOf(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static double doubleOf(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0;
    }

    /**
     * 取 Long 型主键。JDBC 驱动对 BIGINT 可能返回 Long 也可能返回 BigInteger，
     * 因此不直接强转；无法识别时返回 null，让调用方走"无该班数据"分支而不是误配到 0 号班。
     */
    private static Long longOf(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : null;
    }

    /**
     * 获取学分不足的学生列表。
     *
     * <p>原实现遍历班级花名册，把班里<b>每一个</b>学生都塞进结果（注释写着"简单统计"，
     * 但那个统计根本没写），于是"学分不足"名单等于全校花名册；同时 {@code page} 参数
     * 被接收却没有用于偏移，翻到第几页看到的都是第 1 页。这里改为真正按
     * "已修学分 &lt; 应修学分（存在挂科）"筛选，并正确分页。
     */
    @GetMapping("/credit-insufficient")
    public ApiResponse<Map<String, Object>> getInsufficientCreditStudents(@RequestParam("counselor_id") Long counselorId,
                                                                  @RequestParam(defaultValue = "1") Integer page,
                                                                  @RequestParam(defaultValue = "20") Integer size,
                                                                  HttpServletRequest request) {
        ApiResponse<Map<String, Object>> denied =
                denyUnlessSelfCounselor(counselorId, request, "无权查看其他辅导员的学分不足名单");
        if (denied != null) return denied;
        try {
            List<Map<String, Object>> rows = scoreMapper.getInsufficientCreditStudents(counselorId);
            List<Map<String, Object>> students = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Map<String, Object> item = new HashMap<>();
                item.put("studentId", row.get("student_no"));      // 学号，前端列头就是"学号"
                item.put("profileId", row.get("profile_id"));      // profile.id，详情页跳转要用
                item.put("studentName", row.get("student_name"));
                item.put("className", row.get("class_name"));
                item.put("score", row.get("earned_credits"));      // 已修得学分
                item.put("attemptedCredits", row.get("attempted_credits"));
                item.put("failCount", row.get("fail_count"));
                item.put("riskLevel", intOf(row.get("fail_count")) >= 3 ? "red" : "yellow");
                item.put("remarks", "已修 " + row.get("earned_credits") + " / 应修 "
                        + row.get("attempted_credits") + " 学分，" + row.get("fail_count") + " 门未通过");
                students.add(item);
            }

            int currentPage = page == null || page < 1 ? 1 : page;
            int pageSize = size == null || size < 1 ? 20 : size;
            int from = Math.min((currentPage - 1) * pageSize, students.size());
            int to = Math.min(from + pageSize, students.size());

            Map<String, Object> result = new HashMap<>();
            result.put("total", students.size());
            result.put("page", currentPage);
            result.put("size", pageSize);
            result.put("list", students.subList(from, to));
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("获取学分不足学生列表失败: {}", e.getMessage());
            return ApiResponse.serverError("获取学分不足学生列表失败: " + e.getMessage());
        }
    }

    /**
     * 按学院统计辅导员数量。
     *
     * <p><b>W15 修的一条静默错</b>：原来 {@code catch} 里回的是 {@code ApiResponse.success(0L)} ——
     * 查库炸了与"这个学院一个辅导员都没有"在响应上长得一模一样，调用方会把一次故障
     * 显示成一个可信的 0。与 W14 修的 {@code StudentController.countByCollege} 同族，现在如实回错误。
     */
    @GetMapping("/count-by-college")
    public ApiResponse<Long> countByCollege(@RequestParam Long collegeId) {
        try {
            long count = counselorService.countByCollege(collegeId);
            return ApiResponse.success(count);
        } catch (Exception e) {
            log.error("统计辅导员数量失败: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    // ==================== 成绩跟踪API ====================

    @GetMapping("/scores/class/{classId}")
    public ApiResponse<List<Map<String, Object>>> getClassScores(
            @PathVariable Long classId,
            @RequestParam("counselor_id") Long counselorId,
            HttpServletRequest request) {
        // W13：W4.12 那条 `isClassOfCounselor(classId, counselorId)` 挡不住"借用别人的 counselor_id"
        // —— 甲辅导员拿乙的 classId 并**同时把 counselor_id 填成乙**，那句判断就会放行。
        // 所以先核对"你是不是 counselor_id 这个人"，再谈"这个班是不是你的"。
        ApiResponse<List<Map<String, Object>>> denied =
                denyUnlessSelfCounselor(counselorId, request, "无权查看其他辅导员班级的成绩");
        if (denied != null) return denied;
        try {
            // 2026-10-02（W4.12）：`counselor_id` 以前**收了但从没用过** —— 任何一个辅导员
            // 拿别人的 classId 就能读整个班的成绩（与学院管理员的跨院花名册同型，见 W1.13）。
            // 现在按「这个班是不是该辅导员带的」放行。
            //
            // 注意：不能因为校验失败就回空列表 —— 那与"这个班没成绩"在页面上长得一样，
            // 会把"越权"讲成"暂无数据"。回 403 是为了让越权与空数据可区分。
            if (!isClassOfCounselor(classId, counselorId)) {
                return ApiResponse.forbidden("该班级不属于当前辅导员");
            }
            List<Map<String, Object>> scores = scoreMapper.getScoresByClassId(classId);
            return ApiResponse.success(scores);
        } catch (Exception e) {
            log.error("获取班级成绩失败: {}", e.getMessage());
            return ApiResponse.serverError("获取班级成绩失败: " + e.getMessage());
        }
    }

    /**
     * 该班级是否属于这个辅导员（{@code classes.counselor_id} 存的是 users.id）。
     *
     * <p>{@code counselorId} 为空时返回 {@code false}（拒绝），而不是放行 ——
     * 缺参数不该等价于"有权限"。
     *
     * <p>取回 {@code null} 时也返回 {@code false}。{@code ClassInfoService} 现在
     * 内部 catch 了异常、最坏回空列表，所以这一分支今天到不了；但**"拒绝"必须是这句判断
     * 得出的，不能靠下面抛 NPE 兜底** —— 那样子类化/换实现之后拒绝会变成 500，
     * 越权与"服务出错了"在客户端就分不清了。（这条是 W4.12 的用例
     * {@code CounselorClassScoreAccessTest.classLookupFails_closed} 逼出来的。）
     */
    private boolean isClassOfCounselor(Long classId, Long counselorId) {
        if (classId == null || counselorId == null) {
            return false;
        }
        List<Map<String, Object>> owned = classInfoService.getClassesByCounselorId(counselorId);
        if (owned == null) {
            return false;
        }
        for (Map<String, Object> clazz : owned) {
            Object id = clazz == null ? null : clazz.get("id");
            if (id instanceof Number && ((Number) id).longValue() == classId.longValue()) {
                return true;
            }
        }
        return false;
    }

    @GetMapping("/scores/distribution/{courseId}")
    public ApiResponse<Map<String, Object>> getCourseScoreDistribution(
            @PathVariable Long courseId,
            @RequestParam(value = "counselor_id", required = false) Long counselorId,
            HttpServletRequest request) {
        // W13：`counselor_id` 可空且以前不核对 —— 甲把 counselor_id 填成乙即可读乙名下班级的成绩分布。
        // 为空时保持原状（回空表，不泄露），非空则必须是自己。
        if (counselorId != null) {
            ApiResponse<Map<String, Object>> denied =
                    denyUnlessSelfCounselor(counselorId, request, "无权查看其他辅导员的成绩分布");
            if (denied != null) return denied;
        }
        try {
            List<Map<String, Object>> scores;
            if (counselorId != null) {
                scores = scoreMapper.getScoresByCourseIdAndCounselor(courseId, counselorId);
            } else {
                scores = new ArrayList<>();
            }
            Map<String, Object> distribution = new HashMap<>();
            int excellent = 0, good = 0, normal = 0, pass = 0, fail = 0;
            double totalScore = 0;

            for (Map<String, Object> score : scores) {
                Object scoreTotalObj = score.get("score_total");
                if (scoreTotalObj instanceof Number) {
                    double s = ((Number) scoreTotalObj).doubleValue();
                    totalScore += s;
                    if (s >= 90) excellent++;
                    else if (s >= 80) good++;
                    else if (s >= 70) normal++;
                    else if (s >= 60) pass++;
                    else fail++;
                }
            }

            // 分母用各档位之和而非 scores.size()：两者在数据源切换后未必相等，
            // 用后者会出现"各档相加 ≠ 总数"，前端饼图百分比对不上。
            int total = excellent + good + normal + pass + fail;
            distribution.put("total", total);
            distribution.put("excellent", excellent);
            distribution.put("good", good);
            distribution.put("normal", normal);
            distribution.put("pass", pass);
            distribution.put("fail", fail);
            distribution.put("average", total > 0 ? Math.round(totalScore / total * 100.0) / 100.0 : 0);

            return ApiResponse.success(distribution);
        } catch (Exception e) {
            log.error("获取成绩分布失败: {}", e.getMessage());
            return ApiResponse.serverError("获取成绩分布失败: " + e.getMessage());
        }
    }

    /**
     * 低分关注名单。
     *
     * <p>关注线是 {@link ScoreCaliber#ATTENTION_LINE}（70 分），<b>不是挂科线</b>：
     * 这条线要捞出的是"成绩偏低但还没挂科"的学生，属于辅导员日常盯防范围。
     * 挂科门数一律走 {@link ScoreCaliber#PASS_LINE}，两者不可混用。
     */
    @GetMapping("/scores/low-score")
    public ApiResponse<List<Map<String, Object>>> getLowScoreStudents(
            @RequestParam("counselor_id") Long counselorId,
            HttpServletRequest request) {
        ApiResponse<List<Map<String, Object>>> denied =
                denyUnlessSelfCounselor(counselorId, request, "无权查看其他辅导员的低分名单");
        if (denied != null) return denied;
        try {
            return ApiResponse.success(
                    scoreMapper.getLowScoresByCounselorId(counselorId, ScoreCaliber.ATTENTION_LINE));
        } catch (Exception e) {
            log.error("获取低分学生失败: {}", e.getMessage());
            return ApiResponse.serverError("获取低分学生失败: " + e.getMessage());
        }
    }

    @GetMapping("/scores/student/{studentId}/trend")
    public ApiResponse<List<Map<String, Object>>> getStudentScoreTrend(@PathVariable Long studentId,
                                                                      HttpServletRequest request) {
        // W13：`studentId` 是 `student_profile.id`（见 ScoreMapper.getScoresByStudentId 的注释）。
        ApiResponse<List<Map<String, Object>>> denied =
                denyUnlessMyStudent(studentId, request, "该学生不在你带的班级里");
        if (denied != null) return denied;
        try {
            List<Map<String, Object>> scores = scoreMapper.getScoresByStudentId(studentId);
            return ApiResponse.success(scores);
        } catch (Exception e) {
            log.error("获取学生成绩趋势失败: {}", e.getMessage());
            return ApiResponse.serverError("获取学生成绩趋势失败: " + e.getMessage());
        }
    }

    // ==================== 数据分析API ====================

    /**
     * 学分不足率。
     *
     * <p>原实现把每班的成绩<b>行数</b>当人数累加（"总人数"于是成了成绩条数），
     * 再按"班级平均分 &lt; 60 则全计入不足"的方式统计 —— 一个班平均分低于 60
     * 会把整班几十人都算作学分不足。这里改为按人去重统计：
     * 分母是有成绩的学生数，分子是其中存在挂科的学生数。
     */
    @GetMapping("/analytics/credit-insufficient")
    public ApiResponse<Map<String, Object>> getCreditInsufficientRate(
            @RequestParam("counselor_id") Long counselorId,
            HttpServletRequest request) {
        ApiResponse<Map<String, Object>> denied =
                denyUnlessSelfCounselor(counselorId, request, "无权查看其他辅导员的学分不足率");
        if (denied != null) return denied;
        try {
            List<Map<String, Object>> classStats = scoreMapper.getClassScoreStatsByCounselorId(counselorId);
            int totalStudents = 0;
            int insufficientStudents = 0;

            for (Map<String, Object> stat : classStats) {
                totalStudents += intOf(stat.get("graded_student_count"));
                insufficientStudents += intOf(stat.get("fail_student_count"));
            }

            Map<String, Object> result = new HashMap<>();
            result.put("totalStudents", totalStudents);
            result.put("insufficientStudents", insufficientStudents);
            result.put("insufficientRate", totalStudents > 0
                    ? Math.round((double) insufficientStudents / totalStudents * 100) : 0);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("获取学分不足率失败: {}", e.getMessage());
            return ApiResponse.serverError("获取学分不足率失败: " + e.getMessage());
        }
    }

    @GetMapping("/analytics/warning-distribution")
    public ApiResponse<List<Map<String, Object>>> getWarningDistribution(
            @RequestParam("counselor_id") Long counselorId,
            HttpServletRequest request) {
        ApiResponse<List<Map<String, Object>>> denied =
                denyUnlessSelfCounselor(counselorId, request, "无权查看其他辅导员的预警分布");
        if (denied != null) return denied;
        try {
            List<Map<String, Object>> distribution = warningMapper.getWarningDistributionByCounselor(counselorId);
            return ApiResponse.success(distribution);
        } catch (Exception e) {
            log.error("获取预警分布失败: {}", e.getMessage());
            return ApiResponse.serverError("获取预警分布失败: " + e.getMessage());
        }
    }

    @GetMapping("/analytics/handling-efficiency")
    public ApiResponse<Map<String, Object>> getWarningHandlingEfficiency(
            @RequestParam("counselor_id") Long counselorId,
            HttpServletRequest request) {
        ApiResponse<Map<String, Object>> denied =
                denyUnlessSelfCounselor(counselorId, request, "无权查看其他辅导员的预警处理效率");
        if (denied != null) return denied;
        try {
            Map<String, Object> efficiency = warningMapper.getAvgHandleTimeByCounselor(counselorId);
            if (efficiency == null || efficiency.get("avg_handle_days") == null) {
                efficiency = new HashMap<>();
                efficiency.put("avg_handle_days", 0);
            }
            return ApiResponse.success(efficiency);
        } catch (Exception e) {
            log.error("获取预警处理效率失败: {}", e.getMessage());
            return ApiResponse.serverError("获取预警处理效率失败: " + e.getMessage());
        }
    }

    /**
     * 班级学分达标率排名。
     *
     * <p>原实现把班级<b>平均分</b>直接当成"达标率"百分比（{@code Math.min(100, avgScore)}）：
     * 平均 85 分就报"达标率 85%"。平均分和达标率是两个量纲，凑巧都在 0~100 之间而已。
     * 现在用真正的达标率（无挂科学生数 / 有成绩学生数）。
     */
    @GetMapping("/analytics/credit-achievement-ranking")
    public ApiResponse<List<Map<String, Object>>> getCreditAchievementRanking(
            @RequestParam("counselor_id") Long counselorId,
            HttpServletRequest request) {
        ApiResponse<List<Map<String, Object>>> denied =
                denyUnlessSelfCounselor(counselorId, request, "无权查看其他辅导员的学分达标排名");
        if (denied != null) return denied;
        try {
            List<Map<String, Object>> classStats = scoreMapper.getClassScoreStatsByCounselorId(counselorId);
            List<Map<String, Object>> ranking = new ArrayList<>();
            for (Map<String, Object> stat : classStats) {
                Map<String, Object> item = new HashMap<>();
                item.put("className", stat.get("name"));
                item.put("studentCount", intOf(stat.get("graded_student_count")));
                item.put("creditAchievementCount", intOf(stat.get("credit_achievement_count")));
                item.put("achievementRate", stat.get("achievement_rate") == null
                        ? 0.0 : doubleOf(stat.get("achievement_rate")));
                ranking.add(item);
            }
            return ApiResponse.success(ranking);
        } catch (Exception e) {
            log.error("获取学分达标排名失败: {}", e.getMessage());
            return ApiResponse.serverError("获取学分达标排名失败: " + e.getMessage());
        }
    }

    @GetMapping("/analytics/warning-trend")
    public ApiResponse<List<Map<String, Object>>> getWarningTrend(
            @RequestParam("counselor_id") Long counselorId,
            HttpServletRequest request) {
        ApiResponse<List<Map<String, Object>>> denied =
                denyUnlessSelfCounselor(counselorId, request, "无权查看其他辅导员的预警趋势");
        if (denied != null) return denied;
        try {
            List<Map<String, Object>> trend = warningMapper.getWarningTrendByCounselor(counselorId);
            return ApiResponse.success(trend);
        } catch (Exception e) {
            log.error("获取预警趋势失败: {}", e.getMessage());
            return ApiResponse.serverError("获取预警趋势失败: " + e.getMessage());
        }
    }

    @GetMapping("/analytics/assistance-completion")
    public ApiResponse<Map<String, Object>> getAssistancePlanCompletionRate(
            @RequestParam("counselor_id") Long counselorId,
            HttpServletRequest request) {
        ApiResponse<Map<String, Object>> denied =
                denyUnlessSelfCounselor(counselorId, request, "无权查看其他辅导员的帮扶完成率");
        if (denied != null) return denied;
        try {
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.academic.common.entity.AssistancePlan> wrapper =
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            wrapper.eq(com.academic.common.entity.AssistancePlan::getCounselorId, counselorId);
            List<com.academic.common.entity.AssistancePlan> plans = assistancePlanService.list(wrapper);

            int total = plans.size();
            int completed = 0;
            for (com.academic.common.entity.AssistancePlan plan : plans) {
                if (plan.getStatus() != null && plan.getStatus() == 2) {
                    completed++;
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("totalPlans", total);
            result.put("completedPlans", completed);
            result.put("completionRate", total > 0 ? Math.round((double) completed / total * 100) : 0);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("获取帮扶完成率失败: {}", e.getMessage());
            return ApiResponse.serverError("获取帮扶完成率失败: " + e.getMessage());
        }
    }

    // ==================== P0新增：预警处理 ====================
    /**
     * 处理单个预警，并通知学生「你的预警已被处理」。
     *
     * <p>此前这里只改 {@code academic_warning} 一行 —— 辅导员点完"处理"，
     * 学生那边毫无感知，得自己再去翻预警页才知道辅导员已经介入。
     * 现在处理成功后给学生发一条通知（正文带上处理措施）。
     *
     * <p>通知走 {@link com.academic.common.notify.StudentNotifier}：
     * {@code warning.studentId} 是 {@code student_profile.id}，由它换成 {@code users.id}
     * 再写 {@code notifications}（那张表的 {@code user_id} 有真外键，转错就 ERROR 1452）。
     * 学生若关掉了预警类通知，通知会正常跳过，<b>不影响处理本身</b>。
     */
    @PostMapping("/warnings/{warningId}/process")
    public ApiResponse<Map<String, Object>> processWarning(@PathVariable Long warningId, @RequestBody Map<String, Object> data,
                                                            HttpServletRequest request) {
        // W13：处理动作会写库并给学生发通知 —— 只能处理自己班上的学生的那条预警。
        ApiResponse<Map<String, Object>> denied =
                denyUnlessMyWarning(warningId, request, "该预警不属于你带的班级");
        if (denied != null) return denied;
        try {
            log.info("处理预警: warningId={}, data={}", warningId, data);
            com.academic.common.entity.AcademicWarning warning = warningMapper.selectById(warningId);
            if (warning == null) return ApiResponse.notFound("预警不存在");
            warning.setStatus(1); // 已处理
            if (data.containsKey("handleResult")) warning.setHandleResult(data.get("handleResult").toString());
            if (data.containsKey("handledBy")) warning.setHandledBy(data.get("handledBy").toString());
            warning.setHandledAt(java.time.LocalDateTime.now());
            warning.setUpdatedAt(java.time.LocalDateTime.now());
            warningMapper.updateById(warning);
            notifyWarningHandled(warning, "辅导员已处理你的预警");
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("message", "预警处理成功");
            return ApiResponse.success(resp);
        } catch (Exception e) {
            log.error("处理预警失败: {}", e.getMessage());
            return ApiResponse.serverError("处理预警失败: " + e.getMessage());
        }
    }

    /** 批量处理预警，逐条通知学生。 */
    @PostMapping("/warnings/batch-process")
    public ApiResponse<Map<String, Object>> batchProcessWarnings(@RequestBody List<Long> warningIds,
                                                                  HttpServletRequest request) {
        // W13：逐条核对归属，只要有**一条**不属于本人就整单拒绝（fail-closed）——
        // 半单成功会让"越权被拒"和"部分成功"长得一样，客户端分不清。
        if (warningIds != null) {
            for (Long wid : warningIds) {
                ApiResponse<Map<String, Object>> denied =
                        denyUnlessMyWarning(wid, request, "预警 " + wid + " 不属于你带的班级");
                if (denied != null) return denied;
            }
        }
        try {
            log.info("批量处理预警: {}", warningIds);
            int processed = 0;
            for (Long wid : warningIds) {
                com.academic.common.entity.AcademicWarning warning = warningMapper.selectById(wid);
                if (warning != null) {
                    warning.setStatus(1);
                    warning.setHandledAt(java.time.LocalDateTime.now());
                    warning.setUpdatedAt(java.time.LocalDateTime.now());
                    warningMapper.updateById(warning);
                    notifyWarningHandled(warning, "辅导员已批量处理你的预警");
                    processed++;
                }
            }
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("processed", processed);
            resp.put("total", warningIds.size());
            return ApiResponse.success(resp);
        } catch (Exception e) {
            log.error("批量处理预警失败: {}", e.getMessage());
            return ApiResponse.serverError("批量处理预警失败: " + e.getMessage());
        }
    }

    /**
     * 给预警学生发一条"已处理"通知。
     *
     * <p>刻意包 try/catch：处理动作本身已经落库成功了，通知发不出去（学生关了开关、
     * 档案没绑账号、库抖动）都不该让辅导员看到 500 —— 那会让他以为处理没生效而重复点。
     */
    private void notifyWarningHandled(com.academic.common.entity.AcademicWarning warning, String title) {
        try {
            String result = warning.getHandleResult();
            String content = "你此前的预警「" + warning.getTitle() + "」已被处理。"
                    + (result == null || result.isBlank() ? "" : "处理措施：" + result);
            studentNotifier.notifyStudent(warning.getStudentId(),
                    com.academic.common.notify.NotificationPreferences.Category.WARNING,
                    warning.getWarningLevel(), title, content, warning.getId());
        } catch (Exception e) {
            log.warn("预警 {} 的处理通知发送失败（处理本身已生效）: {}", warning.getId(), e.getMessage());
        }
    }

    /**
     * 通知学生。
     *
     * <p>此前这里只 log 一行就回 {@code {success:true,message:"通知发送成功"}} —— 不写任何库，
     * 页面上却弹「通知已发送」。现在真落两张表：{@code communication_logs}（辅导员侧记录）
     * + {@code notifications}（学生通知中心真的会读到）。写在一个事务里，见
     * {@link com.academic.service.CounselorNotificationService}。
     */
    @PostMapping("/students/notify")
    public ApiResponse<Map<String, Object>> notifyStudents(@RequestBody Map<String, Object> data,
                                                            HttpServletRequest request) {
        // W17：发送人身份**只认请求头**，请求体里写的身份一律不算数。
        //
        // W13 在这里装过一道守卫，但它只读 `data.get("counselor_id")`（下划线），
        // 而**两个真实调用方发的都是 `counselorId`（驼峰）**
        // （`CounselorNotifications.vue`、`CounselorCourses.vue`），
        // 服务层 `firstLong(counselorId, counselor_id)` 又恰好**驼峰优先** ⇒
        // "传驼峰 = 守卫不生效，但身份照样被采纳"。后果两条：
        //   ① 任何已认证用户都能以别的辅导员名义群发：
        //      `{"counselorId":53,"recipients":["all"],"content":"…"}` ⇒ 发给 53 名下全部学生，
        //      `communication_logs.counselor_id` 还记成 53（冒名 + 栽赃）；
        //   ② 不传身份只传 `student_ids` 时服务层 `roster == null`，
        //      那句"学生 X 不在该辅导员名下，已跳过"**根本不执行** ⇒ 可给任意学生发通知。
        // 修法（两条一起，缺一条都还留着半个洞）：
        //   ① **认的身份**＝ body 里两种拼写合并出来的那个；不是本人 ⇒ 403，
        //      不再像 W13 那样只看下划线（那正是被绕开的那一处）。
        //   ② **缺身份**时把本人 id 回填进去 —— 服务层只有拿到 counselorId 才会去取
        //      rosters，「不在该辅导员名下，已跳过」那句才真正执行。回填是关掉②的唯一办法：
        //      光拒绝"写了别人的"挡不住"什么都不写"。
        // `callerId` 取自网关注入的 `X-User-Id`，取不到即拒（fail-closed）。
        // 管理员保持原状（跨辅导员运维用），与 `denyUnlessSelfCounselor` 的口径一致。
        Map<String, Object> payload = data == null ? new java.util.HashMap<>() : new java.util.HashMap<>(data);
        if (!isAdmin(request)) {
            Long me = callerId(request);
            if (me == null) {
                return ApiResponse.error(403, "无法确定发送人身份，拒绝发送通知");
            }
            Long claimed = asLong(payload.get("counselorId"));
            if (claimed == null) {
                claimed = asLong(payload.get("counselor_id"));
            }
            if (claimed != null && !claimed.equals(me)) {
                return ApiResponse.error(403, "无权以其他辅导员身份发送通知");
            }
            payload.remove("counselor_id");
            payload.put("counselorId", me);
        }
        try {
            return ApiResponse.success(counselorNotificationService.send(payload));
        } catch (IllegalArgumentException e) {
            // 内容为空 / 接收人解析不出来 / 缺少 counselor_id —— 都是请求的问题，不是 500
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("通知学生失败: {}", e.getMessage(), e);
            return ApiResponse.serverError("通知学生失败: " + e.getMessage());
        }
    }

    // ==================== P0新增：通知中心 ====================
    /**
     * 获取通知历史。
     *
     * <p>此前恒回 {@code {total:0, list:[]}} —— 页面永远「暂无通知记录」，哪怕刚发过。
     * 现在读 {@code communication_logs} 里 category 属于通知类别的行，按「同一次发送」归并。
     */
    @GetMapping("/notifications/history")
    public ApiResponse<Map<String, Object>> getNotificationHistory(@RequestParam("counselor_id") Long counselorId,
                                                                    @RequestParam(defaultValue = "1") Integer page,
                                                                    @RequestParam(defaultValue = "10") Integer size,
                                                                    HttpServletRequest request) {
        ApiResponse<Map<String, Object>> denied =
                denyUnlessSelfCounselor(counselorId, request, "无权查看其他辅导员的通知历史");
        if (denied != null) return denied;
        try {
            return ApiResponse.success(counselorNotificationService.history(
                    counselorId, page == null ? 1 : page, size == null ? 10 : size));
        } catch (Exception e) {
            log.error("获取通知历史失败: {}", e.getMessage(), e);
            return ApiResponse.serverError("获取通知历史失败: " + e.getMessage());
        }
    }

    /**
     * 获取通知模板。
     *
     * <p>此前硬编码 3 条（学业预警通知 / 学期提醒 / 帮扶计划提醒）—— 库里没有任何模板表，
     * 这 3 条是写给页面看的假数据，改一条得改代码。本系统没有"可配置模板"这个功能，
     * 如实回空列表。（全仓没有任何视图在调这个接口。）
     */
    @GetMapping("/notifications/templates")
    public ApiResponse<List<Map<String, Object>>> getNotificationTemplates() {
        try {
            return ApiResponse.success(new ArrayList<>());
        } catch (Exception e) {
            log.error("获取通知模板失败: {}", e.getMessage(), e);
            return ApiResponse.serverError("获取通知模板失败: " + e.getMessage());
        }
    }

    /**
     * 获取每周通知数量。此前三个字段恒 0；现在按 {@code communication_logs} 的真实行数统计
     * （见 {@link com.academic.service.CounselorNotificationService#weeklyCount}）。
     */
    @GetMapping("/notifications/weekly-count")
    public ApiResponse<Map<String, Object>> getWeeklyNotificationCount(@RequestParam("counselor_id") Long counselorId,
                                                                        HttpServletRequest request) {
        ApiResponse<Map<String, Object>> denied =
                denyUnlessSelfCounselor(counselorId, request, "无权查看其他辅导员的通知统计");
        if (denied != null) return denied;
        try {
            return ApiResponse.success(counselorNotificationService.weeklyCount(counselorId));
        } catch (Exception e) {
            log.error("获取每周通知数量失败: {}", e.getMessage(), e);
            return ApiResponse.serverError("获取每周通知数量失败: " + e.getMessage());
        }
    }

    // ==================== P0新增：选课 ====================
    /** 获取选课信息 */
    @GetMapping("/enrollments")
    public ApiResponse<List<Map<String, Object>>> getEnrollments(@RequestParam(value = "counselor_id", required = false) Long counselorId,
                                                                 HttpServletRequest request) {
        // W13：`counselor_id` 可空。为空时保持原状（回空表，不泄露）；非空必须是自己。
        if (counselorId != null) {
            ApiResponse<List<Map<String, Object>>> denied =
                    denyUnlessSelfCounselor(counselorId, request, "无权查看其他辅导员的选课信息");
            if (denied != null) return denied;
        }
        try {
            if (counselorId == null) return ApiResponse.success(new ArrayList<>());
            List<Map<String, Object>> classes = classInfoService.getClassesByCounselorId(counselorId);
            Set<Long> classIds = new HashSet<>();
            for (Map<String, Object> c : classes) {
                Object id = c.get("id");
                if (id != null) classIds.add(((Number) id).longValue());
            }
            List<Map<String, Object>> result = new ArrayList<>();
            for (Long classId : classIds) {
                result.addAll(scoreMapper.getScoresByClassId(classId));
            }
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("获取选课信息失败: {}", e.getMessage());
            return ApiResponse.serverError("获取选课信息失败: " + e.getMessage());
        }
    }

    // ==================== P0新增：班级管理 ====================
    /** 获取辅导员管理的班级列表 */
    @GetMapping("/classes")
    public ApiResponse<List<Map<String, Object>>> getClasses(@RequestParam("counselor_id") Long counselorId,
                                                             HttpServletRequest request) {
        ApiResponse<List<Map<String, Object>>> denied =
                denyUnlessSelfCounselor(counselorId, request, "无权查看其他辅导员的班级列表");
        if (denied != null) return denied;
        try {
            List<Map<String, Object>> classes = classInfoService.getClassesByCounselorId(counselorId);
            return ApiResponse.success(classes);
        } catch (Exception e) {
            log.error("获取班级列表失败: {}", e.getMessage());
            return ApiResponse.serverError("获取班级列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取班级活动。
     *
     * <p>此前写死两条，其中一条的日期是 {@code LocalDate.now().plusDays(3)} —— 每次都"三天后
     * 有个座谈会"，看着像真数据。库里没有班级活动表，本系统也没有"活动管理"功能，如实回空列表。
     * （全仓没有任何视图在调这个接口。）
     */
    @GetMapping("/classes/activities")
    public ApiResponse<List<Map<String, Object>>> getClassActivities(@RequestParam("counselor_id") Long counselorId,
                                                                     HttpServletRequest request) {
        // W13：本接口恒回空列表（库里没有活动表），本无泄露；加闸只为让这一族口径一致。
        ApiResponse<List<Map<String, Object>>> denied =
                denyUnlessSelfCounselor(counselorId, request, "无权查看其他辅导员的班级活动");
        if (denied != null) return denied;
        try {
            return ApiResponse.success(new ArrayList<>());
        } catch (Exception e) {
            log.error("获取班级活动失败: {}", e.getMessage(), e);
            return ApiResponse.serverError("获取班级活动失败: " + e.getMessage());
        }
    }

    /** 获取班级详情 */
    @GetMapping("/classes/{classId}/detail")
    public ApiResponse<Map<String, Object>> getClassDetail(@PathVariable Long classId,
                                                            HttpServletRequest request) {
        ApiResponse<Map<String, Object>> denied =
                denyUnlessMyClass(classId, request, "该班级不属于当前辅导员");
        if (denied != null) return denied;
        try {
            ClassInfo classInfo = classInfoMapper.selectById(classId);
            if (classInfo == null) return ApiResponse.notFound("班级不存在");
            Map<String, Object> detail = new HashMap<>();
            detail.put("id", classInfo.getId());
            detail.put("name", classInfo.getName());
            detail.put("collegeId", classInfo.getCollegeId());
            detail.put("majorId", classInfo.getMajorId());
            detail.put("counselorId", classInfo.getCounselorId());
            detail.put("teacherId", classInfo.getTeacherId());
            detail.put("studentCount", classInfoMapper.getStudentsByClassIdMap(classId).size());
            return ApiResponse.success(detail);
        } catch (Exception e) {
            log.error("获取班级详情失败: {}", e.getMessage());
            return ApiResponse.serverError("获取班级详情失败: " + e.getMessage());
        }
    }

    /** 获取班级学生 */
    @GetMapping("/classes/{classId}/students")
    public ApiResponse<List<Map<String, Object>>> getClassStudents(@PathVariable Long classId,
                                                                   HttpServletRequest request) {
        ApiResponse<List<Map<String, Object>>> denied =
                denyUnlessMyClass(classId, request, "该班级不属于当前辅导员");
        if (denied != null) return denied;
        try {
            List<Map<String, Object>> students = classInfoMapper.getStudentsByClassIdMap(classId);
            return ApiResponse.success(students);
        } catch (Exception e) {
            log.error("获取班级学生失败: {}", e.getMessage());
            return ApiResponse.serverError("获取班级学生失败: " + e.getMessage());
        }
    }

    /** 获取班级预警概览 */
    @GetMapping("/classes/{classId}/warnings")
    public ApiResponse<Map<String, Object>> getClassWarningOverview(@PathVariable Long classId,
                                                                     HttpServletRequest request) {
        ApiResponse<Map<String, Object>> denied =
                denyUnlessMyClass(classId, request, "该班级不属于当前辅导员");
        if (denied != null) return denied;
        try {
            Map<String, Object> overview = new HashMap<>();
            overview.put("classId", classId);
            // 查询该班级学生
            List<Map<String, Object>> students = classInfoMapper.getStudentsByClassIdMap(classId);
            int totalWarnings = 0, highLevel = 0, mediumLevel = 0, lowLevel = 0, handledCount = 0, pendingCount = 0;
            if (students != null) {
                for (Map<String, Object> student : students) {
                    // 2026-10-01 第二十一轮修: 这里原用的是 `student.get("studentId")` ——
                    // 那是 `student_profile.student_id`, 即**学号字符串**(如 '2023020701'),
                    // 而 `academic_warning.student_id` 是 **bigint 且存 `student_profile.id`**
                    // (实测按 profile.id 匹配 4/4、按学号 0/4;`WHERE student_id='2023020701'`
                    // 在 MySQL 里把字符串转成数字比较, **不报错、静默返回 0 行**)
                    // ⇒ 本端点从前**恒返回全 0**。现改用同一查询新带的 profileId。
                    Object profileId = student.get("profileId");
                    if (profileId != null) {
                        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.academic.common.entity.AcademicWarning> qw =
                            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
                        qw.eq("student_id", profileId);
                        List<com.academic.common.entity.AcademicWarning> warns = warningMapper.selectList(qw);
                        if (warns != null) {
                            for (com.academic.common.entity.AcademicWarning w : warns) {
                                totalWarnings++;
                                int lv = w.getWarningLevel() != null ? w.getWarningLevel() : 1;
                                if (lv >= 3) highLevel++;
                                else if (lv == 2) mediumLevel++;
                                else lowLevel++;
                                if (w.getStatus() != null && w.getStatus() == 1) handledCount++;
                                else pendingCount++;
                            }
                        }
                    }
                }
            }
            overview.put("totalWarnings", totalWarnings);
            overview.put("highLevel", highLevel);
            overview.put("mediumLevel", mediumLevel);
            overview.put("lowLevel", lowLevel);
            overview.put("handledCount", handledCount);
            overview.put("pendingCount", pendingCount);
            return ApiResponse.success(overview);
        } catch (Exception e) {
            log.error("获取班级预警失败: {}", e.getMessage());
            return ApiResponse.serverError("获取班级预警失败: " + e.getMessage());
        }
    }

    /** 班级预警对比 */
    @GetMapping("/classes/warnings/compare")
    public ApiResponse<List<Map<String, Object>>> compareClassWarnings(@RequestParam("counselor_id") Long counselorId,
                                                                       HttpServletRequest request) {
        ApiResponse<List<Map<String, Object>>> denied =
                denyUnlessSelfCounselor(counselorId, request, "无权查看其他辅导员的班级预警对比");
        if (denied != null) return denied;
        try {
            List<Map<String, Object>> classes = classInfoService.getClassesByCounselorId(counselorId);
            // 两个聚合各查一次，再在内存里按 classId 拼装 —— 避免在 for 循环里逐班查库。
            Map<Long, Map<String, Object>> warningsByClass = new HashMap<>();
            for (Map<String, Object> row : warningMapper.getWarningCountByClass(counselorId)) {
                Long classId = longOf(row.get("classId"));
                if (classId != null) {
                    warningsByClass.put(classId, row);
                }
            }
            List<Map<String, Object>> comparison = new ArrayList<>();
            for (Map<String, Object> clazz : classes) {
                Long classId = longOf(clazz.get("id"));
                Map<String, Object> item = new HashMap<>();
                item.put("classId", classId);
                item.put("className", clazz.get("name"));
                Map<String, Object> agg = classId == null ? null : warningsByClass.get(classId);
                item.put("warningCount", agg == null ? 0 : intOf(agg.get("warningCount")));
                item.put("pendingCount", agg == null ? 0 : intOf(agg.get("pendingCount")));
                item.put("highRiskCount", agg == null ? 0 : intOf(agg.get("highRiskCount")));
                Double avgGpa = classId == null ? null : scoreMapper.getClassAvgGpa(classId);
                item.put("avgGpa", avgGpa == null ? null
                        : java.math.BigDecimal.valueOf(avgGpa).setScale(2, java.math.RoundingMode.HALF_UP));
                comparison.add(item);
            }
            return ApiResponse.success(comparison);
        } catch (Exception e) {
            log.error("获取班级预警对比失败: {}", e.getMessage());
            return ApiResponse.serverError("获取班级预警对比失败: " + e.getMessage());
        }
    }

    // ========== Gateway 头解析工具方法 ==========

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

    // ==================== 归属闸（W13，2026-10-04） ====================
    //
    // **这一整族的共同病根**：`counselor_id` / `{counselorId}` 以及各种资源 id
    // **完全由客户端给**，控制器收到就照查，从不问一句"这是不是你的"。
    // 网关只保证"打进来的 token 是辅导员"（`PREFIX_COUNSELOR` 那段要求 role=3），
    // 挡得住学生/教师，**挡不住辅导员之间互相看/改** —— 而这条前缀下有近 60 个端点。
    //
    // 最典型的是 `GET /scores/class/{classId}?counselor_id=`：W4.12 已经加了一句
    // `isClassOfCounselor(classId, counselorId)`，**但那个 counselorId 是客户端传的**
    // ⇒ 换个辅导员的 id 传进去，"这个班是不是你的"就变成"这个班是不是**他**的"，
    // 一句话绕过。判据必须建立在**网关注入的身份**上，不能建立在请求参数上。
    //
    // id 空间（2026-10-04 活库直查；xcx `services/api.js:363-380` 记着同一份）：
    //   * `counselor_id` / `{counselorId}`（数值）＝ **`users.id`**（司明=53）——
    //     `classes.counselor_id` / `assistance_plans.counselor_id` /
    //     `communication_logs.counselor_id` 活库全是 53，`counselor_profiles.id` 是 1。
    //   * `/counselors/students/{id}` 一族的 `studentId` ＝ **`student_profile.id`**（司明=3）
    //   * `counselor_profiles.counselor_id` 是**业务编号字符串**（`'C53'`），
    //     只被 `GET /counselors/{counselorId}` 用 —— 那条要拿 `profile.userId` 比。
    //   * `assistance_plans.student_id` 与 `academic_warning.student_id` 都是 profile.id。
    //
    // 一律 **fail-closed**：缺身份、id 为空、资源查不到 —— 全部**拒绝**。
    // 尤其"查不到也拒绝"是刻意的：不区分"没这条"与"不是你的"，免得变成存在性预言
    // （同 W12 的取舍）。拒绝形状沿用本仓惯例 **HTTP 200 + body `code:403`**
    // （`ApiResponse.forbidden/error(403,…)`），前端按 `code` 判。

    /** 登录人的 `users.id`；取不到返回 {@code null} —— 调用方一律判为拒绝。 */
    private Long callerId(HttpServletRequest request) {
        return getUserIdFromGateway(request);
    }

    /**
     * 请求体里传来的 id 往往是 {@code Integer}（Jackson 反序列化 JSON 数字的默认类型），
     * 也可能是字符串。用它当身份基准之前必须归一化成 {@code Long}，否则
     * {@code Integer(53).equals(Long(53))} 恒为 false ⇒ 本人也被拒。
     * （同型坑见 W12 的 `53.equals(1)`：两套 id 都是数字，比错了不报错。）
     */
    private Long asLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            String s = ((String) value).trim();
            if (!s.isEmpty()) {
                try {
                    return Long.valueOf(s);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * 管理员专用。放行返回 {@code null}。
     *
     * <p>用在**审批**这一族上：`/class-management/applications/**` 两条接口在注释里
     * 写着"供管理员使用"，但它们挂在 `/api/counselors/**` 下 ⇒ 网关那段
     * `PREFIX_COUNSELOR && role != 3 → 拒绝` 恰好把**管理员以外的人全放进来**的是别人、
     * 把**辅导员自己**放行。于是辅导员可以调 `POST …/applications/{id}/review {status:1}`
     * **批掉自己刚提交的申请**，白拿一个班 —— 这是提权，比"看到别人的数据"更重。
     */
    private <T> ApiResponse<T> denyUnlessAdmin(HttpServletRequest request, String message) {
        return isAdmin(request) ? null : ApiResponse.error(403, message);
    }

    /** `counselor_id` 型参数（= `users.id`）必须是本人。放行返回 {@code null}。 */
    private <T> ApiResponse<T> denyUnlessSelfCounselor(Long counselorId, HttpServletRequest request, String message) {
        if (isAdmin(request)) {
            return null;
        }
        Long me = callerId(request);
        if (me == null || counselorId == null || !me.equals(counselorId)) {
            return ApiResponse.error(403, message);
        }
        return null;
    }

    /** 这条帮扶计划是不是本人建的（`assistance_plans.counselor_id` = users.id）。 */
    private <T> ApiResponse<T> denyUnlessMyPlan(Long planId, HttpServletRequest request, String message) {
        if (isAdmin(request)) {
            return null;
        }
        Long me = callerId(request);
        AssistancePlan plan = planId == null ? null : assistancePlanService.getById(planId);
        if (me != null && plan != null && me.equals(plan.getCounselorId())) {
            return null;
        }
        return ApiResponse.error(403, message);
    }

    /** 这条沟通记录是不是本人写的（`communication_logs.counselor_id` = users.id）。 */
    private <T> ApiResponse<T> denyUnlessMyLog(Long logId, HttpServletRequest request, String message) {
        if (isAdmin(request)) {
            return null;
        }
        Long me = callerId(request);
        CommunicationLog record = logId == null ? null : communicationLogService.getById(logId);
        if (me != null && record != null && me.equals(record.getCounselorId())) {
            return null;
        }
        return ApiResponse.error(403, message);
    }

    /**
     * 这个班是不是本人带的。复用既有的 {@link #isClassOfCounselor(Long, Long)}，
     * 但**基准换成登录人自己的 `users.id`**，不再信请求里那个数。
     */
    private <T> ApiResponse<T> denyUnlessMyClass(Long classId, HttpServletRequest request, String message) {
        if (isAdmin(request)) {
            return null;
        }
        if (isClassOfCounselor(classId, callerId(request))) {
            return null;
        }
        return ApiResponse.error(403, message);
    }

    /**
     * 这名学生（`student_profile.id`）是否挂在本人带的班上。
     *
     * <p>班级取自**档案行自己的 `class_id`**，不是客户端传的 classId —— 否则又是一句
     * "拿着别人的班 id 说这是我的学生"。
     */
    private <T> ApiResponse<T> denyUnlessMyStudent(Long studentId, HttpServletRequest request, String message) {
        if (isAdmin(request)) {
            return null;
        }
        Long me = callerId(request);
        Student profile = studentId == null ? null : studentService.getById(studentId);
        if (me != null && profile != null && isClassOfCounselor(profile.getClassId(), me)) {
            return null;
        }
        return ApiResponse.error(403, message);
    }

    /**
     * 这条预警对应的学生是否挂在本人带的班上。
     *
     * <p>`academic_warning.student_id` 是 `student_profile.id`，预警表**没有**班级/辅导员列，
     * 所以走 档案 → `class_id` → `classes.counselor_id` 两步。
     */
    private <T> ApiResponse<T> denyUnlessMyWarning(Long warningId, HttpServletRequest request, String message) {
        if (isAdmin(request)) {
            return null;
        }
        Long me = callerId(request);
        AcademicWarning warning = warningId == null ? null : warningMapper.selectById(warningId);
        if (me == null || warning == null || warning.getStudentId() == null) {
            return ApiResponse.error(403, message);
        }
        Student profile = studentService.getById(warning.getStudentId());
        if (profile != null && isClassOfCounselor(profile.getClassId(), me)) {
            return null;
        }
        return ApiResponse.error(403, message);
    }
}
