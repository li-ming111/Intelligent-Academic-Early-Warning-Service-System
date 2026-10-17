package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.common.util.ExceptionMessages;
import com.academic.dto.StudentDashboardResponse;
import com.academic.service.StudentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Student Controller - 学生角色全部功能端点
 */
@Slf4j
@RestController
@RequestMapping("/students")
public class StudentController {

    private final StudentService studentService;

    /**
     * 老师推荐选课的读出口（W14）。走 mapper 直连而不是塞进 {@code StudentService}：
     * 它只查这一张新表，且 {@code teacher} 侧的同名 Mapper 也是这么写的。
     */
    @org.springframework.beans.factory.annotation.Autowired
    private com.academic.mapper.CourseRecommendationMapper courseRecommendationMapper;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    // ==================== Dashboard ====================
    @GetMapping("/dashboard/{studentId}")
    public ApiResponse<StudentDashboardResponse> getStudentDashboard(@PathVariable String studentId,
                                                                     HttpServletRequest request) {
        ApiResponse<StudentDashboardResponse> deny = denyUnlessSelf(Long.parseLong(studentId), request, "只能查看本人的学习看板");
        if (deny != null) {
            return deny;
        }
        try {
            log.info("Get student dashboard: {}", studentId);
            return ApiResponse.success(studentService.getStudentDashboard(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get student dashboard failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    // ==================== 成绩 ====================
    @GetMapping("/scores/{studentId}")
    public ApiResponse<Object> getStudentScores(@PathVariable String studentId, @RequestParam(required = false) String semester,
                                                HttpServletRequest request) {
        ApiResponse<Object> deny = denyUnlessSelf(Long.parseLong(studentId), request, "只能查看本人的成绩");
        if (deny != null) {
            return deny;
        }
        try {
            log.info("Get student scores: {}, semester: {}", studentId, semester);
            return ApiResponse.success(studentService.getStudentScores(Long.parseLong(studentId), semester));
        } catch (Exception e) {
            log.error("Get student scores failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    /**
     * 导出本人成绩单为真实 xlsx（W1.8）。
     *
     * <p>路径参数是 {@code users.id}。**只允许导出自己的**：改之前这个端点回的是
     * JSON 摘要（所以谁也没法借它拿到别人的成绩），一旦真开始吐文件，就必须有归属
     * 校验，否则等于新开一个"换个 id 就能下载别人成绩单"的口子。
     *
     * <p>错误一律以 JSON 回（前端 {@code responseType:'blob'} 下靠
     * {@code blob.type} 分辨），并且**带真实 HTTP 状态码** —— 本项目反复踩过
     * "HTTP 200 里包着 code:500" 的坑。
     */
    @GetMapping("/export/scores/{studentId}/excel")
    public void exportScoresToExcel(@PathVariable String studentId, HttpServletResponse response) {
        try {
            Long userId = Long.parseLong(studentId);
            Long current = currentUserId();
            if (current == null) {
                writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "无法识别当前登录用户");
                return;
            }
            if (!current.equals(userId)) {
                writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "只能导出本人的成绩单");
                return;
            }
            byte[] content = studentService.exportScoresWorkbook(userId);
            if (content == null) {
                writeJsonError(response, HttpServletResponse.SC_NOT_FOUND, "学生不存在");
                return;
            }
            String fileName = "scores_" + studentId + ".xlsx";
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition",
                    "attachment; filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
            response.setContentLength(content.length);
            response.getOutputStream().write(content);
            response.getOutputStream().flush();
            log.info("Export scores to Excel ok: userId={}, bytes={}", userId, content.length);
        } catch (Exception e) {
            log.error("Export scores to Excel failed: {}", e.getMessage(), e);
            writeJsonError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "导出失败: " + ExceptionMessages.describe(e));
        }
    }

    /** 当前登录用户的 {@code users.id}（网关注入后由 GatewayAuthFilter 放进 SecurityContext）。 */
    private static Long currentUserId() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(auth.getPrincipal()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 路径参数里的 id 是不是**当前登录本人**。这些学生自助面收的都是 {@code users.id}
     * （与 {@link #currentUserId()} 同一套），所以直接比。
     *
     * <p>⚠️ 不适用于 {@code /subscription/**} —— 那一族要的是 {@code student_profile.id}，
     * 是**另一套 id 空间**，见 {@link #isSelfProfile(java.lang.Long, java.lang.Long)}。
     */
    private static boolean isSelf(Long userId) {
        Long me = currentUserId();
        return me != null && me.equals(userId);
    }

    /** 管理员（网关透传的 {@code X-Role}）不受本人限制 —— 与 teacher-service 的归属闸同形。 */
    private static boolean isAdmin(HttpServletRequest request) {
        String role = request == null ? null : request.getHeader("X-Role");
        return "4".equals(role) || "admin".equals(role);
    }

    /**
     * W43：管理口径端点（学院统计 / 全院花名册）的闸。
     *
     * <p>放行**系统管理员(4)**与**学院管理员(5)** —— 这两条正是"按学院看学生"的使用者
     * （学院看板、`AdminController.getAllStudents`）。缺头、学生(1)、教师(2)、辅导员(3)
     * 一律拒（fail-closed）。**刻意不放行教师**：教师看名单走 `/teachers/students/{id}`，
     * 不该从 `/api/students/**` 这个学生自助前缀拿全院花名册。
     */
    private static boolean isAdminOrCollegeAdmin(HttpServletRequest request) {
        String role = request == null ? null : request.getHeader("X-Role");
        return "4".equals(role) || "admin".equals(role) || "5".equals(role) || "college_admin".equals(role);
    }

    /**
     * 订阅族专用：路径里的 id 是 {@code student_profile.id}，要比对的是**调用者自己的档案 id**。
     *
     * <p>写成一个独立入口而不是就地拼，是因为这两套 id 不相交（档案 3 / 用户 27），
     * 拿 users.id 直接比会**永远不相等** ⇒ 把自己的请求也拦掉。
     */
    private boolean isSelfProfile(Long pathProfileId, HttpServletRequest request) {
        if (isAdmin(request)) {
            return true;
        }
        Long me = currentUserId();
        return me != null && pathProfileId != null
                && pathProfileId.equals(studentService.getProfileIdByUserId(me));
    }

    /** 归属拒绝的统一形状：HTTP 200 + body {@code code:403}（本仓惯用，前端按 code 判）。 */
    private static ApiResponse<Object> forbidden(String message) {
        return ApiResponse.error(403, message);
    }

    /**
     * 读侧归属闸（W11）：非本人且非管理员 ⇒ 回一个 403 信封；本人 / 管理员 ⇒ {@code null}（放行）。
     *
     * <p>写成"返回 null 就继续"的形状，是为了让每个读端点只要两行：
     * <pre>{@code ApiResponse<Object> deny = denyUnlessSelf(id, request, "只能…");
     * if (deny != null) return deny;}</pre>
     * 比在每个方法里重复 {@code !isSelf(...) && !isAdmin(...)} 更不容易写错（漏一个 {@code isAdmin}
     * 就会把管理员挡在外面）。
     *
     * <p>泛型是因为本控制器有 {@code ApiResponse<StudentDashboardResponse>}、
     * {@code ApiResponse<Long>} 等多种返回类型，钉死 {@code <Object>} 会编译不过。
     */
    private <T> ApiResponse<T> denyUnlessSelf(Long userId, HttpServletRequest request, String message) {
        return (!isSelf(userId) && !isAdmin(request)) ? ApiResponse.error(403, message) : null;
    }

    /**
     * 同上，但用于 {@code student_profile.id} 那一族（订阅 / 评价）。
     *
     * <p>⚠️ 与 {@link #denyUnlessSelf} 分开是**必须的**，不是风格问题：这两套 id 不相交
     * （司明 users.id=27 / profile.id=3），用错帮手的结果是"本人的请求也被拦"（功能坏了）
     * 或"拿 users.id 反而放行去读档案 27"。
     */
    private <T> ApiResponse<T> denyUnlessSelfProfile(Long pathProfileId, HttpServletRequest request, String message) {
        return isSelfProfile(pathProfileId, request) ? null : ApiResponse.error(403, message);
    }

    // ========== 资源归属闸（W12，2026-10-04） ==========
    //
    // W11 收口的是「属主 id 型」参数：路径里直接给的就是 `users.id`/`student_profile.id`，
    // 比一次就行。这一族不一样 —— 路径里给的是一个**东西的 id**（一条申诉 / 一条通知 /
    // 一份帮扶计划），"它是不是我的"只能**按 id 查一次库**才知道。所以三个帮手都长成
    // "先问 service 这条资源的主人是谁，再和身份比"。
    //
    // 两个容易写错的点：
    //  ① **三个帮手的比对基准不是同一个 id 空间**：appeal/plan 的主人存的是
    //     `student_profile.id`，而 notification 的主人存的是 `users.id`。混用的话
    //     不是"拦不住"就是"把自己的请求也拦了"，两个方向都不报错。
    //  ② **资源不存在也一律 403**（不区分"没有这条"和"不是你的"）。这样接口不会
    //     变成一个"id 存在性探测器"；且 `*Owner*` 在降级时返回 null（fail-closed），
    //     天然落进同一条分支。

    /** 申诉归属闸：{@code score_appeals.id} → 它的 {@code student_id}（档案 id）是否 == 我的档案 id。 */
    private <T> ApiResponse<T> denyUnlessMyAppeal(Long appealId, HttpServletRequest request, String message) {
        if (isAdmin(request)) {
            return null;
        }
        Long me = currentUserId();
        Long myProfile = me == null ? null : studentService.getProfileIdByUserId(me);
        Long owner = studentService.getAppealOwnerProfileId(appealId);
        return myProfile != null && owner != null && owner.equals(myProfile) ? null : ApiResponse.error(403, message);
    }

    /** 帮扶计划归属闸：{@code assistance_plans.id} → 它的 {@code student_id}（档案 id）是否 == 我的档案 id。 */
    private <T> ApiResponse<T> denyUnlessMyPlan(Long planId, HttpServletRequest request, String message) {
        if (isAdmin(request)) {
            return null;
        }
        Long me = currentUserId();
        Long myProfile = me == null ? null : studentService.getProfileIdByUserId(me);
        Long owner = studentService.getPlanOwnerProfileId(planId);
        return myProfile != null && owner != null && owner.equals(myProfile) ? null : ApiResponse.error(403, message);
    }

    /** 通知归属闸：{@code notifications.id} → 它的 {@code user_id} 是否 == 我的 {@code users.id}（**不是档案 id**）。 */
    private <T> ApiResponse<T> denyUnlessMyNotification(Long notificationId, HttpServletRequest request, String message) {
        if (isAdmin(request)) {
            return null;
        }
        Long me = currentUserId();
        Long owner = studentService.getNotificationOwnerUserId(notificationId);
        return me != null && owner != null && owner.equals(me) ? null : ApiResponse.error(403, message);
    }

    /**
     * 「**新建记录**」型端点的归属闸（W42）：请求体里那个自证的 {@code studentId}
     * 必须**就是登录人自己**。
     *
     * <p>与上面三条资源闸的区别：那三条收的是"已经存在的东西的 id"，要查一次库才知道主人；
     * 这一条收的是**即将被创建的行**，库里的真值就是请求体写进去的那个 —— 所以判据只能是
     * "请求体说的这个人，是不是登录人"。**不能换算成档案 id 再比**：本仓两套 id 空间大量
     * 重叠，一旦"拿 userId 去当档案 id 比"，伪造者只要把 {@code studentId} 填成"档案 id
     * 恰好等于他 userId"的受害者就能绕过（这是 W42 之外、W11/W12 两轮都反复强调的坑）。
     *
     * <p>{@code studentId} 缺失 / 非数字时**放行**，交给服务层按既有语义回
     * {@code {error:"缺少 studentId"}} —— 本闸只回答"是不是你"，不接管参数校验。
     */
    private <T> ApiResponse<T> denyUnlessBodyStudentIsSelf(Object body, HttpServletRequest request) {
        if (isAdmin(request)) {
            return null;
        }
        Long claimed = bodyLong(body, "studentId");
        if (claimed == null) {
            return null;
        }
        Long me = currentUserId();
        return me != null && me.equals(claimed)
                ? null
                : ApiResponse.error(403, "只能为本人提交，不能代他人提交");
    }

    /**
     * 从请求体（{@code Map}）里取一个整数型字段；取不到 / 认不出返回 {@code null}。
     *
     * <p>不用 {@code Long.valueOf(toString())} 直接抛：前端各种页面这几处传过
     * 数字、字符串、甚至 {@code null}，抛出去会变成 HTTP 500（本仓 W33 踩过
     * "该报 403 却冒成 500"），而这里"认不出"的正确含义是"交给服务层报参数错"。
     */
    private static Long bodyLong(Object body, String key) {
        if (!(body instanceof Map<?, ?> map)) {
            return null;
        }
        Object raw = map.get(key);
        if (raw == null || "".equals(raw.toString().trim())) {
            return null;
        }
        try {
            return Long.valueOf(raw.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 以 JSON 回一个错误。
     *
     * <p>响应已经被声明成 {@code responseType:'blob'}，前端拿到的是 Blob，
     * 只能靠 {@code Content-Type} 分辨这是文件还是错误 —— 所以这里必须显式设
     * {@code application/json}，别让 Spring 按 {@code text/plain} 兜底。
     */
    private static void writeJsonError(HttpServletResponse response, int status, String message) {
        try {
            if (response.isCommitted()) {
                return;
            }
            response.reset();
            response.setStatus(status);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"code\":" + status + ",\"message\":\"" + message.replace("\"", "'") + "\"}");
            response.getWriter().flush();
        } catch (Exception ignore) {
            // 响应已提交/连接已断，无处可写
        }
    }

    // ==================== 预警 ====================
    @GetMapping("/warnings/{studentId}")
    public ApiResponse<Object> getStudentWarnings(@PathVariable String studentId, HttpServletRequest request) {
        ApiResponse<Object> deny = denyUnlessSelf(Long.parseLong(studentId), request, "只能查看本人的预警");
        if (deny != null) {
            return deny;
        }
        try {
            log.info("Get student warnings: {}", studentId);
            return ApiResponse.success(studentService.getStudentWarnings(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get student warnings failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    // ==================== 学生信息 ====================
    @GetMapping("/info/{studentId}")
    public ApiResponse<Object> getStudentInfo(@PathVariable String studentId, HttpServletRequest request) {
        ApiResponse<Object> deny = denyUnlessSelf(Long.parseLong(studentId), request, "只能查看本人的信息");
        if (deny != null) {
            return deny;
        }
        try {
            log.info("Get student info: {}", studentId);
            return ApiResponse.success(studentService.getStudentInfo(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get student info failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    @GetMapping("/{studentId}")
    public ApiResponse<Object> getStudentInfoByPath(@PathVariable String studentId, HttpServletRequest request) {
        ApiResponse<Object> deny = denyUnlessSelf(Long.parseLong(studentId), request, "只能查看本人的信息");
        if (deny != null) {
            return deny;
        }
        try {
            log.info("Get student info by path: {}", studentId);
            return ApiResponse.success(studentService.getStudentInfo(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get student info by path failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    @GetMapping("/student-by-user/{userId}")
    public ApiResponse<Object> getStudentByUserId(@PathVariable String userId, HttpServletRequest request) {
        ApiResponse<Object> deny = denyUnlessSelf(Long.parseLong(userId), request, "只能查看本人的信息");
        if (deny != null) {
            return deny;
        }
        try {
            log.info("Get student by user ID: {}", userId);
            return ApiResponse.success(studentService.getStudentInfo(Long.parseLong(userId)));
        } catch (Exception e) {
            log.error("Get student by user ID failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    @GetMapping("/{studentId}/gpa")
    public ApiResponse<Object> getStudentGpa(@PathVariable String studentId, HttpServletRequest request) {
        ApiResponse<Object> deny = denyUnlessSelf(Long.parseLong(studentId), request, "只能查看本人的绩点");
        if (deny != null) {
            return deny;
        }
        try {
            log.info("Get student GPA: {}", studentId);
            return ApiResponse.success(studentService.getHistoryBenchmark(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get student GPA failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    @GetMapping("/{studentId}/class-info")
    public ApiResponse<Object> getClassInfo(@PathVariable String studentId, HttpServletRequest request) {
        ApiResponse<Object> deny = denyUnlessSelf(Long.parseLong(studentId), request, "只能查看本人的班级信息");
        if (deny != null) {
            return deny;
        }
        try {
            log.info("Get class info: {}", studentId);
            return ApiResponse.success(studentService.getClassInfo(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get class info failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    // ==================== 老师推荐选课（W14） ====================
    /**
     * 我（学生）能看到的「老师推荐选课」。
     *
     * <p>修之前这条路**只到教师端点就断了**：后端是个不落库的回声桩，学生侧没有任何
     * 地方能看到它。W14 建了 {@code course_recommendation} 表（迁移
     * {@code db_backups/migration_w14_course_recommendation.sql}），这里就是它的出口。
     *
     * <p>路径参数 {@code userId} 是 {@code users.id}（与 {@code /students/**} 其余端点同一套）。
     * 可见性规则写在 {@link com.academic.mapper.CourseRecommendationMapper} 的 SQL 里：
     * 点名给某个班的 ⇒ 该班可见；{@code target_class_id IS NULL} 的 ⇒ 只有**推荐人自己带的班**可见。
     */
    @GetMapping("/recommendations/{userId}")
    public ApiResponse<Object> getCourseRecommendations(@PathVariable String userId,
                                                        HttpServletRequest request) {
        ApiResponse<Object> deny = denyUnlessSelf(Long.parseLong(userId), request, "只能查看本人的选课推荐");
        if (deny != null) {
            return deny;
        }
        try {
            log.info("Get course recommendations for user: {}", userId);
            return ApiResponse.success(courseRecommendationMapper.listForStudent(Long.parseLong(userId)));
        } catch (Exception e) {
            log.error("Get course recommendations failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    // ==================== 帮扶 ====================
    @GetMapping("/assistance/{studentId}")
    public ApiResponse<Object> getAssistance(@PathVariable String studentId, HttpServletRequest request) {
        ApiResponse<Object> deny = denyUnlessSelf(Long.parseLong(studentId), request, "只能查看本人的帮扶计划");
        if (deny != null) {
            return deny;
        }
        try {
            log.info("Get assistance: {}", studentId);
            return ApiResponse.success(studentService.getAssistancePlans(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get assistance failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    @GetMapping("/assistance-plans/{studentId}")
    public ApiResponse<Object> getAssistancePlans(@PathVariable String studentId, HttpServletRequest request) {
        ApiResponse<Object> deny = denyUnlessSelf(Long.parseLong(studentId), request, "只能查看本人的帮扶计划");
        if (deny != null) {
            return deny;
        }
        try {
            log.info("Get assistance plans: {}", studentId);
            return ApiResponse.success(studentService.getAssistancePlans(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get assistance plans failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    /**
     * 学生更新自己帮扶计划的进度（+ 完成情况说明）。
     *
     * <p>2026-10-02 新增。此前 PC 学生端打的是
     * {@code /counselors/assistance-plans/{id}/progress}，被网关的前缀角色闸挡下
     * （role≠3 一律 403）—— 学生点一次失败一次，且弹窗里那个必填的「完成情况」
     * 前端从来没发出来。端点移到学生自己的前缀下，身份与归属由服务端从网关注入的
     * {@code X-User-Id} 判定（**不信任 body 里的 id**）。
     *
     * <p>⚠️ 这里**刻意不 catch**：参数非法/计划不属于本人由 service 抛
     * {@link IllegalArgumentException}，交给 {@code GlobalExceptionHandler} 回 **HTTP 400**。
     * 若像同文件其它方法那样 catch 后返回 {@code ApiResponse.error}，HTTP 仍是 200、
     * 前端只看状态码就会把失败当成功（本仓反复踩过的那一族）。
     */
    @PostMapping("/assistance/{planId}/progress")
    public ApiResponse<Object> updatePlanProgress(@PathVariable Long planId,
                                                  @RequestBody(required = false) Map<String, Object> request) {
        log.info("Update assistance plan progress: planId={}", planId);
        return ApiResponse.success(studentService.updatePlanProgress(planId, request));
    }

    // ==================== 学业建议 ====================
    @GetMapping("/suggestions/{studentId}")
    public ApiResponse<Object> getSuggestions(@PathVariable String studentId, HttpServletRequest request) {
        ApiResponse<Object> deny = denyUnlessSelf(Long.parseLong(studentId), request, "只能查看本人的学业建议");
        if (deny != null) {
            return deny;
        }
        try {
            log.info("Get suggestions: {}", studentId);
            return ApiResponse.success(studentService.getSuggestions(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get suggestions failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    // ==================== 申诉 (P0新增) ====================
    /**
     * 提交成绩申诉。
     *
     * <p>⚠️ 2026-10-13（W42）补上**归属闸**。此前签名是
     * {@code submitAppeal(@RequestBody Object request)} —— 没有 {@code HttpServletRequest}，
     * 于是身份**只取自请求体里的 {@code studentId}**，从不看登录人是谁。实测（经网关）：
     * 邱琳紫（{@code users.id=30}）发 {@code {studentId:27,...}} 即**成功写出**一条挂在
     * 司明名下的申诉。与同族写端点（withdraw / mark-read / settings …）相比，
     * 这一族"**新建记录**"的端点当时集体漏了闸（本方法与下面的提交评价同批）。
     *
     * <p>闸的判据：请求体里的 {@code studentId} 认的是 **{@code users.id}**（PC 五处
     * 提交点发的是 {@code localStorage.userId} / {@code student-by-user} 得到的 id；
     * 小程序发 {@code userId}），所以直接与网关注入的登录人比一次即可 ——
     * **不做换算**：写成"查档案再比"会让伪造者只要把 id 换成"档案 id 恰好等于他 userId"
     * 的受害者就能绕过（本仓两套 id 大量重叠）。简言之：
     * <b>{@code studentId} 是谁，就必须是登录人自己。</b>
     *
     * <p>缺 {@code studentId} 时**不在这里拦** —— 服务层会如实回
     * {@code {error:"缺少 studentId"}}（HTTP 200），保持既有语义。管理员（{@code X-Role:4}）
     * 旁路放行，与其余归属闸一致。
     *
     * <p>被拒形状：HTTP 200 + body {@code code:403}（本控制器惯用；前端 axios 因此走
     * reject 分支，页面 catch 里提示失败）。
     */
    @PostMapping("/appeals/submit")
    public ApiResponse<Object> submitAppeal(@RequestBody Object request, HttpServletRequest httpRequest) {
        ApiResponse<Object> deny = denyUnlessBodyStudentIsSelf(request, httpRequest);
        if (deny != null) {
            return deny;
        }
        try {
            log.info("Submit appeal request");
            return ApiResponse.success(studentService.submitAppeal(request));
        } catch (Exception e) {
            log.error("Submit appeal failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    /** 兼容旧路径（同样受上面的归属闸保护 —— 它转发到同一方法）。 */
    @PostMapping("/appeal")
    public ApiResponse<Object> submitAppealLegacy(@RequestBody Object request, HttpServletRequest httpRequest) {
        return submitAppeal(request, httpRequest);
    }

    @GetMapping("/appeals/{studentId}/list")
    public ApiResponse<Object> getStudentAppeals(@PathVariable String studentId, HttpServletRequest request) {
        ApiResponse<Object> deny = denyUnlessSelf(Long.parseLong(studentId), request, "只能查看本人的申诉");
        if (deny != null) {
            return deny;
        }
        try {
            log.info("Get student appeals: {}", studentId);
            return ApiResponse.success(studentService.getStudentAppeals(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get student appeals failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    @GetMapping("/appeals/{appealId}/detail")
    public ApiResponse<Object> getAppealDetail(@PathVariable Long appealId, HttpServletRequest request) {
        ApiResponse<Object> deny = denyUnlessMyAppeal(appealId, request, "只能查看本人的申诉");
        if (deny != null) {
            return deny;
        }
        try {
            log.info("Get appeal detail: {}", appealId);
            return ApiResponse.success(studentService.getAppealDetail(appealId));
        } catch (Exception e) {
            log.error("Get appeal detail failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    /**
     * 撤销申诉。
     *
     * <p>2026-10-03（W10）：{@code studentId} 此前是**客户端说了算**的查询参数，
     * 服务端拿它去 {@code resolveStudentProfileId} 之后直接写库 —— 换个数字就能撤销**别人**的申诉。
     * 实测（经网关）：司明（users.id 27）带 {@code studentId=30} 调用即作用于同学。
     * 现在先比对登录人，再落库。
     */
    @PostMapping("/appeals/{appealId}/withdraw")
    public ApiResponse<Object> withdrawAppeal(@PathVariable Long appealId, @RequestParam Long studentId,
                                              HttpServletRequest request) {
        if (!isSelf(studentId) && !isAdmin(request)) {
            return forbidden("只能撤销本人的申诉");
        }
        try {
            log.info("Withdraw appeal: appealId={}, studentId={}", appealId, studentId);
            return ApiResponse.success(studentService.withdrawAppeal(appealId, studentId));
        } catch (Exception e) {
            log.error("Withdraw appeal failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    @GetMapping("/appeals/{studentId}/pending")
    public ApiResponse<Object> getPendingAppeals(@PathVariable String studentId, HttpServletRequest request) {
        ApiResponse<Object> deny = denyUnlessSelf(Long.parseLong(studentId), request, "只能查看本人的申诉");
        if (deny != null) {
            return deny;
        }
        try {
            log.info("Get pending appeals: {}", studentId);
            return ApiResponse.success(studentService.getPendingAppeals(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get pending appeals failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    @GetMapping("/appeals/{studentId}/statistics")
    public ApiResponse<Object> getAppealStatistics(@PathVariable String studentId, HttpServletRequest request) {
        ApiResponse<Object> deny = denyUnlessSelf(Long.parseLong(studentId), request, "只能查看本人的申诉统计");
        if (deny != null) {
            return deny;
        }
        try {
            log.info("Get appeal statistics: {}", studentId);
            return ApiResponse.success(studentService.getAppealStatistics(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get appeal statistics failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    @GetMapping("/appeals/{studentId}/success-rate")
    public ApiResponse<Object> getAppealSuccessRate(@PathVariable String studentId, HttpServletRequest request) {
        ApiResponse<Object> deny = denyUnlessSelf(Long.parseLong(studentId), request, "只能查看本人的申诉统计");
        if (deny != null) {
            return deny;
        }
        try {
            log.info("Get appeal success rate: {}", studentId);
            return ApiResponse.success(studentService.getAppealSuccessRate(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get appeal success rate failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    @GetMapping("/appeals/{studentId}/by-status")
    public ApiResponse<Object> getAppealsByStatus(@PathVariable String studentId, @RequestParam Integer status,
                                                  HttpServletRequest request) {
        ApiResponse<Object> deny = denyUnlessSelf(Long.parseLong(studentId), request, "只能查看本人的申诉");
        if (deny != null) {
            return deny;
        }
        try {
            log.info("Get appeals by status: studentId={}, status={}", studentId, status);
            return ApiResponse.success(studentService.getAppealsByStatus(Long.parseLong(studentId), status));
        } catch (Exception e) {
            log.error("Get appeals by status failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    @GetMapping("/appeals/{studentId}/history")
    public ApiResponse<Object> getAppealHistory(@PathVariable String studentId,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int pageSize,
                                                 HttpServletRequest request) {
        ApiResponse<Object> deny = denyUnlessSelf(Long.parseLong(studentId), request, "只能查看本人的申诉");
        if (deny != null) {
            return deny;
        }
        try {
            log.info("Get appeal history: studentId={}, page={}, pageSize={}", studentId, page, pageSize);
            return ApiResponse.success(studentService.getAppealHistory(Long.parseLong(studentId), page, pageSize));
        } catch (Exception e) {
            log.error("Get appeal history failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    @GetMapping("/appeals/{studentId}/reason-statistics")
    public ApiResponse<Object> getAppealReasonStatistics(@PathVariable String studentId, HttpServletRequest request) {
        ApiResponse<Object> deny = denyUnlessSelf(Long.parseLong(studentId), request, "只能查看本人的申诉统计");
        if (deny != null) {
            return deny;
        }
        try {
            log.info("Get appeal reason statistics: {}", studentId);
            return ApiResponse.success(studentService.getAppealReasonStatistics(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get appeal reason statistics failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    // ==================== 对标分析 (P0新增) ====================
    @GetMapping("/benchmark/{studentId}/latest")
    public ApiResponse<Object> getLatestBenchmark(@PathVariable String studentId, HttpServletRequest request) {
        ApiResponse<Object> deny = denyUnlessSelf(Long.parseLong(studentId), request, "只能查看本人的对标分析");
        if (deny != null) {
            return deny;
        }
        try {
            log.info("Get latest benchmark: {}", studentId);
            return ApiResponse.success(studentService.getLatestBenchmark(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get latest benchmark failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    @GetMapping("/benchmark/{studentId}/semester")
    public ApiResponse<Object> getSemesterBenchmark(@PathVariable String studentId, @RequestParam String semester,
                                                    HttpServletRequest request) {
        ApiResponse<Object> deny = denyUnlessSelf(Long.parseLong(studentId), request, "只能查看本人的对标分析");
        if (deny != null) {
            return deny;
        }
        try {
            log.info("Get semester benchmark: {}, semester: {}", studentId, semester);
            return ApiResponse.success(studentService.getSemesterBenchmark(Long.parseLong(studentId), semester));
        } catch (Exception e) {
            log.error("Get semester benchmark failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    @GetMapping("/benchmark/{studentId}/history")
    public ApiResponse<Object> getHistoryBenchmark(@PathVariable String studentId, HttpServletRequest request) {
        ApiResponse<Object> deny = denyUnlessSelf(Long.parseLong(studentId), request, "只能查看本人的对标分析");
        if (deny != null) {
            return deny;
        }
        try {
            log.info("Get history benchmark: {}", studentId);
            return ApiResponse.success(studentService.getHistoryBenchmark(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get history benchmark failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    @GetMapping("/benchmark/{studentId}/class-ranking")
    public ApiResponse<Object> getBenchmarkClassRanking(@PathVariable String studentId,
                                                         @RequestParam(required = false) Long classId,
                                                         @RequestParam(required = false) String semester,
                                                         HttpServletRequest request) {
        ApiResponse<Object> deny = denyUnlessSelf(Long.parseLong(studentId), request, "只能查看本人的对标分析");
        if (deny != null) {
            return deny;
        }
        try {
            log.info("Get benchmark class ranking: studentId={}, classId={}, semester={}", studentId, classId, semester);
            return ApiResponse.success(studentService.getBenchmarkClassRanking(Long.parseLong(studentId), classId, semester));
        } catch (Exception e) {
            log.error("Get benchmark class ranking failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    @GetMapping("/benchmark/{studentId}/major-ranking")
    public ApiResponse<Object> getBenchmarkMajorRanking(@PathVariable String studentId,
                                                         @RequestParam(required = false) Long majorId,
                                                         @RequestParam(required = false) String semester,
                                                         HttpServletRequest request) {
        ApiResponse<Object> deny = denyUnlessSelf(Long.parseLong(studentId), request, "只能查看本人的对标分析");
        if (deny != null) {
            return deny;
        }
        try {
            log.info("Get benchmark major ranking: studentId={}, majorId={}, semester={}", studentId, majorId, semester);
            return ApiResponse.success(studentService.getBenchmarkMajorRanking(Long.parseLong(studentId), majorId, semester));
        } catch (Exception e) {
            log.error("Get benchmark major ranking failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    @GetMapping("/benchmark/class/{classId}/comparison")
    public ApiResponse<Object> getClassBenchmarkComparison(@PathVariable Long classId, @RequestParam(required = false) String semester) {
        try {
            log.info("Get class benchmark comparison: classId={}, semester={}", classId, semester);
            return ApiResponse.success(studentService.getClassBenchmarkComparison(classId, semester));
        } catch (Exception e) {
            log.error("Get class benchmark comparison failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    @GetMapping("/benchmark/major/{majorId}/comparison")
    public ApiResponse<Object> getMajorBenchmarkComparison(@PathVariable Long majorId, @RequestParam(required = false) String semester) {
        try {
            log.info("Get major benchmark comparison: majorId={}, semester={}", majorId, semester);
            return ApiResponse.success(studentService.getMajorBenchmarkComparison(majorId, semester));
        } catch (Exception e) {
            log.error("Get major benchmark comparison failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    @GetMapping("/benchmark/{studentId}/progress-report")
    public ApiResponse<Object> getProgressReport(@PathVariable String studentId, HttpServletRequest request) {
        ApiResponse<Object> deny = denyUnlessSelf(Long.parseLong(studentId), request, "只能查看本人的对标分析");
        if (deny != null) {
            return deny;
        }
        try {
            log.info("Get progress report: {}", studentId);
            return ApiResponse.success(studentService.getProgressReport(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get progress report failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    // ==================== 通知中心 (P0新增) ====================
    @GetMapping("/notification-center/{userId}/list")
    public ApiResponse<Object> getNotificationList(@PathVariable String userId,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "10") int pageSize,
                                                    HttpServletRequest request) {
        ApiResponse<Object> deny = denyUnlessSelf(Long.parseLong(userId), request, "只能查看本人的通知");
        if (deny != null) {
            return deny;
        }
        try {
            log.info("Get notification list: userId={}, page={}, pageSize={}", userId, page, pageSize);
            return ApiResponse.success(studentService.getNotificationCenterList(Long.parseLong(userId), page, pageSize));
        } catch (Exception e) {
            log.error("Get notification list failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    @GetMapping("/notification-center/{userId}/unread-count")
    public ApiResponse<Object> getNotificationUnreadCount(@PathVariable String userId, HttpServletRequest request) {
        ApiResponse<Object> deny = denyUnlessSelf(Long.parseLong(userId), request, "只能查看本人的通知");
        if (deny != null) {
            return deny;
        }
        try {
            log.info("Get notification unread count: {}", userId);
            return ApiResponse.success(studentService.getNotificationUnreadCount(Long.parseLong(userId)));
        } catch (Exception e) {
            log.error("Get notification unread count failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    @GetMapping("/notification-center/{userId}/unread")
    public ApiResponse<Object> getUnreadNotifications(@PathVariable String userId, HttpServletRequest request) {
        ApiResponse<Object> deny = denyUnlessSelf(Long.parseLong(userId), request, "只能查看本人的通知");
        if (deny != null) {
            return deny;
        }
        try {
            log.info("Get unread notifications: {}", userId);
            return ApiResponse.success(studentService.getUnreadNotifications(Long.parseLong(userId)));
        } catch (Exception e) {
            log.error("Get unread notifications failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    @PostMapping("/notification-center/{userId}/mark-batch-read")
    public ApiResponse<Object> markNotificationsBatchRead(@PathVariable String userId,
                                                          @RequestBody List<Long> notificationIds,
                                                          HttpServletRequest request) {
        // 2026-10-03（W10）：路径里的 userId 一直是客户端给的，服务端直接拿它去 UPDATE
        // notifications —— 换个数字就把**别人**的通知标成已读。加归属闸（users.id 空间）。
        if (!isSelf(Long.parseLong(userId)) && !isAdmin(request)) {
            return forbidden("只能操作本人的通知");
        }
        try {
            log.info("Mark notifications batch read: userId={}, ids={}", userId, notificationIds);
            return ApiResponse.success(studentService.markNotificationsBatchRead(Long.parseLong(userId), notificationIds));
        } catch (Exception e) {
            log.error("Mark notifications batch read failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    /**
     * 单条通知标记已读。
     *
     * <p>原来这里直接返回 {@code Map.of("success", true, "message", "标记已读成功")} ——
     * 一行库都不写。现在真写（见 {@code StudentServiceImpl.markNotificationRead}），
     * 并如实返回影响行数：id 不存在时 {@code success=false}，不再假装成功。
     * 批量那条 {@code mark-batch-read} 一直是真实现，两条现在口径一致了。
     */
    @PostMapping("/notification-center/{notificationId}/mark-read")
    public ApiResponse<Object> markSingleNotificationRead(@PathVariable Long notificationId,
                                                          HttpServletRequest request) {
        ApiResponse<Object> deny = denyUnlessMyNotification(notificationId, request, "只能操作本人的通知");
        if (deny != null) {
            return deny;
        }
        try {
            log.info("Mark single notification read: notificationId={}", notificationId);
            return ApiResponse.success(studentService.markNotificationRead(notificationId));
        } catch (Exception e) {
            log.error("Mark single notification read failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    @PostMapping("/notification-center/{notificationId}/delete")
    public ApiResponse<Object> deleteNotification(@PathVariable Long notificationId,
                                                  HttpServletRequest request) {
        ApiResponse<Object> deny = denyUnlessMyNotification(notificationId, request, "只能操作本人的通知");
        if (deny != null) {
            return deny;
        }
        try {
            log.info("Delete notification: notificationId={}", notificationId);
            return ApiResponse.success(studentService.deleteNotification(notificationId));
        } catch (Exception e) {
            log.error("Delete notification failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    @PostMapping("/notification-center/{userId}/clear-read")
    public ApiResponse<Object> clearReadNotifications(@PathVariable String userId,
                                                      HttpServletRequest request) {
        // 2026-10-03（W10）：同上，清空的是**路径里那个 userId** 的已读通知。
        if (!isSelf(Long.parseLong(userId)) && !isAdmin(request)) {
            return forbidden("只能操作本人的通知");
        }
        try {
            log.info("Clear read notifications: userId={}", userId);
            return ApiResponse.success(studentService.clearReadNotifications(Long.parseLong(userId)));
        } catch (Exception e) {
            log.error("Clear read notifications failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    // 兼容原有路径
    @GetMapping("/notification-center/{studentId}")
    public ApiResponse<Object> getNotificationCenter(@PathVariable String studentId, HttpServletRequest request) {
        ApiResponse<Object> deny = denyUnlessSelf(Long.parseLong(studentId), request, "只能查看本人的通知");
        if (deny != null) {
            return deny;
        }
        try {
            log.info("Get notification center (legacy): {}", studentId);
            return ApiResponse.success(studentService.getNotificationCenterList(Long.parseLong(studentId), 1, 10));
        } catch (Exception e) {
            log.error("Get notification center failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    @PutMapping("/notification-center/{studentId}/read/{notificationId}")
    public ApiResponse<Object> markNotificationAsReadLegacy(@PathVariable String studentId,
                                                            @PathVariable Long notificationId,
                                                            HttpServletRequest request) {
        // 2026-10-03（W10）：老路径同样把 userId 从路径里读进来就写库，一并加闸。
        if (!isSelf(Long.parseLong(studentId)) && !isAdmin(request)) {
            return forbidden("只能操作本人的通知");
        }
        try {
            log.info("Mark notification as read (legacy): studentId={}, notificationId={}", studentId, notificationId);
            return ApiResponse.success(studentService.markNotificationsBatchRead(Long.parseLong(studentId), List.of(notificationId)));
        } catch (Exception e) {
            log.error("Mark notification as read failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    // ==================== 订阅管理 (P0新增) ====================
    @GetMapping("/subscription/{studentId}/preferences")
    public ApiResponse<Object> getSubscriptionPreferences(@PathVariable String studentId, HttpServletRequest request) {
        // ⚠️ 这一族收的是 student_profile.id（不是 users.id）—— 归属走 isSelfProfile 换算。
        ApiResponse<Object> deny = denyUnlessSelfProfile(Long.parseLong(studentId), request, "只能查看本人的订阅设置");
        if (deny != null) {
            return deny;
        }
        try {
            log.info("Get subscription preferences: {}", studentId);
            return ApiResponse.success(studentService.getSubscriptionPreferences(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get subscription preferences failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    /**
     * 更新订阅偏好。
     *
     * <p>2026-10-03（W10）：⚠️ 这一族的路径参数是 **{@code student_profile.id}**（不是 users.id）——
     * 见 {@code StudentServiceImpl.updateSubscriptionPreferences} 里那句"这里要的是 student_profile.id"。
     * 归属校验因此要走 {@link #isSelfProfile}：先把登录人（users.id）换算成档案 id 再比。
     * 直接用 {@code isSelf} 比会**永远不相等**，把自己的请求也拦掉。
     */
    @PostMapping("/subscription/{studentId}/update")
    public ApiResponse<Object> updateSubscriptionPreferences(@PathVariable String studentId,
                                                             @RequestBody Object preferences,
                                                             HttpServletRequest request) {
        if (!isSelfProfile(Long.parseLong(studentId), request)) {
            return forbidden("只能修改本人的订阅设置");
        }
        try {
            log.info("Update subscription preferences: {}", studentId);
            return ApiResponse.success(studentService.updateSubscriptionPreferences(Long.parseLong(studentId), preferences));
        } catch (Exception e) {
            log.error("Update subscription preferences failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    @PostMapping("/subscription/{studentId}/subscribe-level")
    public ApiResponse<Object> subscribeWarningLevel(@PathVariable String studentId, @RequestParam String level,
                                                     HttpServletRequest request) {
        if (!isSelfProfile(Long.parseLong(studentId), request)) {
            return forbidden("只能修改本人的订阅设置");
        }
        try {
            log.info("Subscribe warning level: studentId={}, level={}", studentId, level);
            return ApiResponse.success(studentService.subscribeWarningLevel(Long.parseLong(studentId), level));
        } catch (Exception e) {
            log.error("Subscribe warning level failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    @PostMapping("/subscription/{studentId}/unsubscribe-level")
    public ApiResponse<Object> unsubscribeWarningLevel(@PathVariable String studentId, @RequestParam String level,
                                                       HttpServletRequest request) {
        if (!isSelfProfile(Long.parseLong(studentId), request)) {
            return forbidden("只能修改本人的订阅设置");
        }
        try {
            log.info("Unsubscribe warning level: studentId={}, level={}", studentId, level);
            return ApiResponse.success(studentService.unsubscribeWarningLevel(Long.parseLong(studentId), level));
        } catch (Exception e) {
            log.error("Unsubscribe warning level failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    @PostMapping("/subscription/{studentId}/set-channel")
    public ApiResponse<Object> setPushChannel(@PathVariable String studentId, @RequestParam String channel,
                                              @RequestParam Boolean enabled, HttpServletRequest request) {
        if (!isSelfProfile(Long.parseLong(studentId), request)) {
            return forbidden("只能修改本人的订阅设置");
        }
        try {
            log.info("Set push channel: studentId={}, channel={}, enabled={}", studentId, channel, enabled);
            return ApiResponse.success(studentService.setPushChannel(Long.parseLong(studentId), channel, enabled));
        } catch (Exception e) {
            log.error("Set push channel failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    // 兼容原有路径
    @GetMapping("/subscription/{studentId}")
    public ApiResponse<Object> getSubscriptions(@PathVariable String studentId, HttpServletRequest request) {
        return getSubscriptionPreferences(studentId, request);
    }

    @PutMapping("/subscription/{studentId}")
    public ApiResponse<Object> updateSubscription(@PathVariable String studentId, @RequestBody Object body,
                                                  HttpServletRequest request) {
        return updateSubscriptionPreferences(studentId, body, request);
    }

    // ==================== 帮扶反馈 (P0新增) ====================
    /**
     * 提交 / 更新帮扶评价。
     *
     * <p>⚠️ 注意这里的**双重信封**：本方法把 service 的返回（可能是
     * {@code {"success":false,"message":"..."}}）再包一层放进 `data`，
     * 而 HTTP 与 `code` 都是 200 —— 判断成败必须看 `data.success`，不是看请求有没有成功。
     */
    /**
     * 提交 / 更新帮扶评价。
     *
     * <p>⚠️ 2026-10-13（W42）补上**归属闸**。此前它把"这条评价属于谁"**完全交给服务层
     * 拿请求体里的 {@code studentId} 去和 plan 比**（{@code StudentServiceImpl:1756}）
     * —— 服务层那片逻辑本身没错，但它**从不看登录人是谁**，于是 {@code studentId} 填谁的
     * 都行。实测（经网关）：邱琳紫（{@code users.id=30}）把 {@code studentId} 填成
     * {@code 52}（张健），即**成功写出**一条挂在张健帮扶计划（{@code id=3}）上的评价。
     *
     * <p>闸的判据复用 {@link #denyUnlessMyPlan}：**看 {@code planId} 指的那份计划是不是
     * 登录人自己的**。这是服务层表达不出的一维 —— 请求体里的 {@code studentId} 只是
     * 自证，伪造它可以自洽；而"计划挂在谁的档案上"由登录身份与库中真值两方决定。
     * 顺带一提：小程序提交评价时**根本不发 {@code studentId}**（靠 planId 关联），
     * 所以这一维也是唯一对两端都成立的判据。
     *
     * <p>管理员（{@code X-Role:4}）旁路放行。被拒形状：HTTP 200 + body {@code code:403}。
     */
    @PostMapping("/evaluations/submit")
    public ApiResponse<Object> submitEvaluation(@RequestBody Object evaluation,
                                                HttpServletRequest request) {
        Long planId = bodyLong(evaluation, "planId");
        if (planId != null) {
            // planId 缺失时不在这里拦：服务层会如实回「缺少 planId（要评价哪条帮扶计划）」。
            ApiResponse<Object> deny = denyUnlessMyPlan(planId, request, "只能提交本人帮扶计划的评价");
            if (deny != null) {
                return deny;
            }
        }
        try {
            log.info("Submit evaluation");
            return ApiResponse.success(studentService.submitEvaluation(evaluation));
        } catch (Exception e) {
            log.error("Submit evaluation failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    /**
     * 某学生的评价列表。
     *
     * <p>⚠️ 路径里的 `{studentId}` 是 **`student_profile.id`**（司明 = 3），
     * 不是 `users.id`（司明 = 27）—— 与 `/students/**` 其它接口的口径**不一样**，
     * 因为底层 SQL 走的是 `assistance_plans.student_id`（存的是 profile id）。
     * 两个 id 空间有重叠值，传错**不报错、只会返回空数组**。
     */
    @GetMapping("/evaluations/{studentId}/list")
    public ApiResponse<Object> getEvaluationsByStudent(@PathVariable String studentId, HttpServletRequest request) {
        // ⚠️ 这一族收的是 student_profile.id（见方法注释）—— 归属走 isSelfProfile 换算。
        ApiResponse<Object> deny = denyUnlessSelfProfile(Long.parseLong(studentId), request, "只能查看本人的评价");
        if (deny != null) {
            return deny;
        }
        try {
            log.info("Get evaluations list: {}", studentId);
            return ApiResponse.success(studentService.getEvaluationsByStudent(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get evaluations list failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    /**
     * 某条帮扶计划下的评价。
     *
     * <p>⚠️ 路径里的 `{planId}` 是 **`assistance_plans.id`**，和上面那套 profile id
     * 完全是另一个空间，但两个都是纯数字 —— 调用时别看错路径。没有评价时返回**空数组**（原来回 `{"error":"无评价数据"}`）。
     */
    @GetMapping("/evaluations/{planId}/detail")
    public ApiResponse<Object> getPlanEvaluation(@PathVariable Long planId, HttpServletRequest request) {
        ApiResponse<Object> deny = denyUnlessMyPlan(planId, request, "只能查看本人的帮扶计划评价");
        if (deny != null) {
            return deny;
        }
        try {
            log.info("Get plan evaluation: {}", planId);
            return ApiResponse.success(studentService.getPlanEvaluation(planId));
        } catch (Exception e) {
            log.error("Get plan evaluation failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    /** 评价统计。⚠️ `{studentId}` 同样是 **`student_profile.id`**（见上面的 list 接口）。 */
    @GetMapping("/evaluations/{studentId}/statistics")
    public ApiResponse<Object> getEvaluationStatistics(@PathVariable String studentId, HttpServletRequest request) {
        // ⚠️ 同样是 student_profile.id（见方法注释）。
        ApiResponse<Object> deny = denyUnlessSelfProfile(Long.parseLong(studentId), request, "只能查看本人的评价");
        if (deny != null) {
            return deny;
        }
        try {
            log.info("Get evaluation statistics: {}", studentId);
            return ApiResponse.success(studentService.getEvaluationStatistics(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get evaluation statistics failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    // 兼容原有路径
    @GetMapping("/evaluations/{studentId}")
    public ApiResponse<Object> getEvaluations(@PathVariable String studentId, HttpServletRequest request) {
        return getEvaluationsByStudent(studentId, request);
    }

    // 2026-10-04（W15）删：`POST /students/evaluations/{studentId}`（submitEvaluationLegacy）。
    //   路径里那个 `studentId` **从头到尾没被读过** —— 方法体只有一句
    //   `return submitEvaluation(request);`，身份/归属全由 body 决定，路径参数纯装饰。
    //   唯一调用方（PC `api/index.js`）打的是 `POST /students/evaluations/submit`；
    //   小程序与测试都没有引用它。留着它只会在读签名时误导"这个 id 有意义"。

    // ==================== 设置 (P0新增) ====================
    @GetMapping("/settings/{studentId}")
    public ApiResponse<Object> getStudentSettings(@PathVariable String studentId, HttpServletRequest request) {
        ApiResponse<Object> deny = denyUnlessSelf(Long.parseLong(studentId), request, "只能查看本人的设置");
        if (deny != null) {
            return deny;
        }
        try {
            log.info("Get student settings: {}", studentId);
            return ApiResponse.success(studentService.getStudentSettings(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get student settings failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    /**
     * 保存学生设置。
     *
     * <p>⚠️ 原来 {@code return Map.of("success", true)} —— 一个字段都不落库，
     * 表现是"改完提示成功、退出再进全变回去"。现在真写
     * {@code user_settings}（键 {@code users.id}，与 user-service 的
     * {@code /users/{uid}/settings} 同一张表）+ {@code student_profile.privacy_level}。
     *
     * <p>{@code studentId} 路径参数是 {@code users.id} —— 与
     * {@code /users/{uid}/settings} 的键一致，所以这条路由是那个入口的学生侧别名，
     * 不是第二套设置。未识别的 key 会**报错**而不是静默丢弃。
     */
    @PutMapping("/settings/{studentId}")
    public ApiResponse<Object> updateStudentSettings(@PathVariable String studentId,
                                                    @RequestBody(required = false) Map<String, Object> body,
                                                    HttpServletRequest request) {
        // 2026-10-03（W10）：路径参数是 users.id（见下方类注释口径），此前不看登录人 ⇒
        // 换个 id 就改**别人**的邮箱提醒/系统消息等开关。加归属闸。
        if (!isSelf(Long.parseLong(studentId)) && !isAdmin(request)) {
            return forbidden("只能修改本人的设置");
        }
        try {
            log.info("Update student settings: {}", studentId);
            return ApiResponse.success(studentService.updateStudentSettings(Long.parseLong(studentId), body));
        } catch (Exception e) {
            log.error("Update student settings failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    @PostMapping("/settings/{studentId}/privacy-level")
    public ApiResponse<Object> updatePrivacyLevel(@PathVariable String studentId, @RequestParam Integer privacyLevel,
                                                  HttpServletRequest request) {
        // 2026-10-03（W10）：隐私级别直接写 student_profile，此前不看登录人 ⇒ 改别人的隐私档位。
        if (!isSelf(Long.parseLong(studentId)) && !isAdmin(request)) {
            return forbidden("只能修改本人的设置");
        }
        try {
            log.info("Update privacy level: studentId={}, privacyLevel={}", studentId, privacyLevel);
            return ApiResponse.success(studentService.updatePrivacyLevel(Long.parseLong(studentId), privacyLevel));
        } catch (Exception e) {
            log.error("Update privacy level failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    // 2026-10-08（W29）删除 `GET /settings/{studentId}/security-logs`。
    //
    // 它声称按 {page,pageSize} 分页返回本人的「安全日志」，而 service 体是**写死的**
    // `total=0, logs=[]` —— 一个查询都没有；这是它和本仓另外两条"恒空"端点
    // （`/counselors/classes/activities`、`/counselors/notifications/templates`）的区别：
    // 那两条是"库里根本没有这张表、结构上给不出数据"，回空是**结果上的事实**；这一条
    // `security_logs` 表**是存在的**（`init_schema.sql:306` 建表、admin 端有实体与计数），
    // 只是**全仓至今没有任何写入方**（AdminController 里明写「`security_logs` 至今 0 行且
    // 全仓无写入方，接上去只会再造一个恒空」，所以它在"系统动态"里也刻意没用）。
    //
    // 处置依据是**两条已经拍过板的规矩**：①「零调用方 = 删」（同 W27 三条 `/register`）；
    // ②学生真正会看的「登录历史」**已经有真路径**——`GET /users/{uid}/login-logs`
    // （user-service，读有真写入方的 `login_logs`），PC 设置页「安全日志」页签用的就是它
    // （`Settings.vue` → `studentAPI.getLoginLogs`）。
    // ⇒ 这里是"零调用方 + 恒空 + 能力已被另一条真路径覆盖"的重复端点，**删掉，不给它补实现**
    //   （"让承诺成真"要新造一个写入方，那是另立一个功能，不是修接口）。
    // 前端 PC 的 `studentAPI.getSecurityLogs` 封装同轮删除；小程序本就没有这条封装。

    // ==================== 消息 ====================
    @GetMapping("/messages/{studentId}")
    public ApiResponse<Object> getStudentMessages(@PathVariable String studentId, HttpServletRequest request) {
        ApiResponse<Object> deny = denyUnlessSelf(Long.parseLong(studentId), request, "只能查看本人的消息");
        if (deny != null) {
            return deny;
        }
        try {
            log.info("Get student messages: {}", studentId);
            return ApiResponse.success(studentService.getStudentMessages(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get student messages failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    @GetMapping("/messages/{studentId}/unread-count")
    public ApiResponse<Object> getUnreadMessageCount(@PathVariable String studentId, HttpServletRequest request) {
        ApiResponse<Object> deny = denyUnlessSelf(Long.parseLong(studentId), request, "只能查看本人的消息");
        if (deny != null) {
            return deny;
        }
        try {
            log.info("Get unread message count: {}", studentId);
            return ApiResponse.success(studentService.getUnreadMessageCount(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get unread message count failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    // ==================== 班级 ====================
    @GetMapping("/class-members/{studentId}")
    public ApiResponse<Object> getClassMembers(@PathVariable String studentId, HttpServletRequest request) {
        ApiResponse<Object> deny = denyUnlessSelf(Long.parseLong(studentId), request, "只能查看本人的班级");
        if (deny != null) {
            return deny;
        }
        try {
            log.info("Get class members: {}", studentId);
            // 从班级信息中间接获取，实际可查 student_profile 同班级学生
            return ApiResponse.success(studentService.getClassInfo(Long.parseLong(studentId)));
        } catch (Exception e) {
            log.error("Get class members failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    @GetMapping("/class-ranking/{studentId}")
    public ApiResponse<Object> getClassRanking(@PathVariable String studentId, HttpServletRequest request) {
        ApiResponse<Object> deny = denyUnlessSelf(Long.parseLong(studentId), request, "只能查看本人的班级排名");
        if (deny != null) {
            return deny;
        }
        try {
            log.info("Get class ranking: {}", studentId);
            return ApiResponse.success(studentService.getBenchmarkClassRanking(Long.parseLong(studentId), null, null));
        } catch (Exception e) {
            log.error("Get class ranking failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    // ==================== 注册（已删，2026-10-08 W27） ====================
    // 这里原来有一条 `POST /students/register`，service 体只有一句 `return "注册成功";` ——
    // **一行都不写库**，却回 `success:true`。与 W24 的 `/teachers/scores/warnings` 同一族
    // （说法与事实不符）。真实的注册路径是 user-service 的 `POST /api/auth/register/student`
    // （PC `Register.vue` / 小程序都在走），**不为一条零调用方的假端点补实现**，直接删。

    // ==================== 学院统计 ====================
    /**
     * 按学院统计学生数。
     *
     * <p><b>W14 修的一条静默错</b>：原来 {@code catch} 里回的是
     * {@code ApiResponse.success(0L)} —— 查库炸了和"这个学院一个学生都没有"
     * 在响应上**长得一模一样**，调用方（如学院看板）会把一次故障显示成一个可信的 0。
     * 这正是本仓反复记过的「恒 0 的 total」那一族。现在如实回错误。
     *
     * <p><b>W43 补闸</b>：这是**管理口径**（跨学院 / 全库计数），不是学生自助面。
     * 此前它只靠网关 `path.startsWith(PREFIX_STUDENT) && role != ROLE_STUDENT` 挡——
     * 那条对**任何一个学生**都是放行的，于是学生打 `?collegeId=1` 能读到 24。
     * 同族见下面 {@code /students/list}。
     */
    @GetMapping("/count-by-college")
    public ApiResponse<Long> countByCollege(@RequestParam Long collegeId, HttpServletRequest request) {
        if (!isAdminOrCollegeAdmin(request)) {
            return ApiResponse.error(403, "仅管理员可查看学院统计");
        }
        try {
            log.info("Count students by college: {}", collegeId);
            return ApiResponse.success(studentService.countByCollege(collegeId));
        } catch (Exception e) {
            log.error("Count students by college failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    /**
     * 按学院取学生列表（{@code collegeId} 缺省 = 全部）。
     *
     * <p><b>W14 修的同一条静默错</b>：原来 {@code catch} 回 {@code success(List.of())}，
     * 于是"学生服务炸了"与"这个学院没有学生"回同一个 200 + 空数组。
     *
     * <p><b>W43 补闸（本轮学生端全量走查查出）</b>：这条是所有 {@code /students/**}
     * 读端点里**唯一没有归属闸的** —— 因为它没有 {member id} 可比，服务层返回的是
     * {@code student_profile} 的**整行**（含姓名 / 学号 / 性别 / 班级 / 学院），
     * 是**全院花名册**，不是"我班同学"（同班的公开面另有 {@code class-ranking}，
     * 见 `utils/classRanking.js` 的说明，那是刻意的）。实测：学生 30 打
     * {@code GET /students/list} ⇒ 200 + **24 行**（全库学生，`collegeId` 还缺省成"全部"）。
     *
     * <p>后端真实调用方只有 admin-service 的 Feign `getStudentsByCollege(null)`
     * （`AdminController.getAllStudents`，经 Feign 请求头透传 ⇒ 这里是 {@code role:4}）；
     * 前端**零调用方**（PC / 小程序都没有这条封装，小程序 `services/api.js` 里那个
     * {@code getStudents: () => '/admin/students'} 从无页面在用）。所以闸取"管理员或
     * 学院管理员"（**刻意不含教师**：教师看名单走 `/teachers/students/{id}`）。
     */
    @GetMapping("/list")
    public ApiResponse<List<Object>> getStudentsByCollege(@RequestParam(required = false) Long collegeId,
                                                          HttpServletRequest request) {
        if (!isAdminOrCollegeAdmin(request)) {
            return ApiResponse.error(403, "仅管理员可查看学生名册");
        }
        try {
            log.info("Get students by college: {}", collegeId);
            return ApiResponse.success(studentService.getStudentsByCollege(collegeId));
        } catch (Exception e) {
            log.error("Get students by college failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }
}