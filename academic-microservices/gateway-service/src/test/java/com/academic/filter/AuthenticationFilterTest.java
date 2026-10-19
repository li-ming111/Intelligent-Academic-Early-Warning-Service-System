package com.academic.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 网关准入判定（W3.3，2026-10-02）。**本模块此前一个测试都没有。**
 *
 * <p>这一层的判据只有两个结果（放行 / 403），写错了的症状是"某类用户全体打不开页面"
 * 或者"全体放行"，都不会报错，所以必须钉死。
 *
 * <p><b>本轮的核心约束</b>：内置角色 1–5 的判定必须与改动前**逐条相同**。
 * 所以下面那组用例是**回归护栏**：它们**带着已加载的 RBAC 数据**跑 ——
 * 也就是说，即使权限表被成功加载进来，也一个字都不能改变内置角色的结果。
 * （改动前那段 RBAC 一旦加载就会 403 掉 role 1/2/3 的全部业务接口，就是这里在防的事。）
 */
class AuthenticationFilterTest {

    private static final int STUDENT = 1;
    private static final int TEACHER = 2;
    private static final int COUNSELOR = 3;
    private static final int ADMIN = 4;
    private static final int COLLEGE_ADMIN = 5;
    /** 管理员在权限管理页新建的角色。 */
    private static final int CUSTOM = 6;

    private AuthenticationFilter filter;

    /** 造一个缓存，roles 全表 + 指定绑定；`custom` 角色 id=6。 */
    private AuthenticationFilter filterWith(Map<String, List<String>> bindings) {
        Map<String, String> roles = new LinkedHashMap<>();
        roles.put("1", "ROLE_STUDENT");
        roles.put("2", "ROLE_TEACHER");
        roles.put("3", "ROLE_COUNSELOR");
        roles.put("4", "ROLE_ADMIN");
        roles.put("5", "ROLE_COLLEGE_ADMIN");
        roles.put("6", "ROLE_ASSISTANT");

        RolePermissionCache cache = new RolePermissionCache();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("roles", roles);
        data.put("map", bindings);
        cache.applyPayload(data);
        return new AuthenticationFilter(cache);
    }

    /** 与"生产库里 5 个内置角色的真实绑定"同形（role 1–5 都持有若干 PERM_*）。 */
    private AuthenticationFilter filterWithBuiltInBindings() {
        Map<String, List<String>> bindings = new LinkedHashMap<>();
        bindings.put("ROLE_STUDENT", List.of("PERM_STUDENT_VIEW", "PERM_COURSE_VIEW",
                "PERM_WARNING_VIEW", "PERM_ASSISTANCE_VIEW"));
        bindings.put("ROLE_TEACHER", List.of("PERM_STUDENT_VIEW", "PERM_COURSE_VIEW",
                "PERM_WARNING_VIEW"));
        bindings.put("ROLE_COUNSELOR", List.of("PERM_STUDENT_VIEW", "PERM_WARNING_MANAGE",
                "PERM_ASSISTANCE_MANAGE"));
        bindings.put("ROLE_ADMIN", List.of("PERM_SYSTEM_MANAGE", "PERM_COURSE_MANAGE"));
        bindings.put("ROLE_COLLEGE_ADMIN", List.of("PERM_WARNING_VIEW", "PERM_STUDENT_VIEW"));
        return filterWith(bindings);
    }

    private boolean allow(int role, HttpMethod method, String path) {
        return filter.hasPermission(
                MockServerHttpRequest.method(method, path).build(), role, (long) role);
    }

    /** 本人 id 与 token 里的 userId 一致/不一致两种。 */
    private boolean allowAs(int role, long userId, HttpMethod method, String path) {
        return filter.hasPermission(
                MockServerHttpRequest.method(method, path).build(), role, userId);
    }

    // ==================== 内置角色：回归护栏 ====================

    @Test
    @DisplayName("回归护栏：RBAC 数据**已加载**时，内置角色 1–5 的判定仍与改动前逐条相同")
    void builtInRoles_unaffectedByLoadedRbacData() {
        filter = filterWithBuiltInBindings();

        // —— 角色前缀的排他性 ——
        assertTrue(allow(STUDENT, HttpMethod.GET, "/api/students/dashboard/1"), "学生进自己的家");
        assertFalse(allow(STUDENT, HttpMethod.GET, "/api/teachers/list"), "学生进不去教师面");
        assertTrue(allow(TEACHER, HttpMethod.GET, "/api/teachers/list"));
        assertFalse(allow(TEACHER, HttpMethod.GET, "/api/students/dashboard/1"), "教师也进不去学生本人的家");
        assertTrue(allow(COUNSELOR, HttpMethod.GET, "/api/counselors/classes/1"));
        assertFalse(allow(COUNSELOR, HttpMethod.POST, "/api/teachers/warnings/1/process"));

        // —— 管理员面 ——
        assertFalse(allow(TEACHER, HttpMethod.GET, "/api/admin/statistics"));
        assertTrue(allow(ADMIN, HttpMethod.GET, "/api/admin/statistics"));
        assertTrue(allow(ADMIN, HttpMethod.DELETE, "/api/roles/delete/9"), "管理员不受任何前缀限制");

        // —— 学院管理员：**只有** college-admin 面，不碰 admin 面、也不碰全局角色表 ——
        // ⚠️ 2026-10-09（W33）订正：这里原来有一条
        //      assertTrue(allow(COLLEGE_ADMIN, HttpMethod.GET, "/api/admin/statistics"));
        //    —— 它**把缺陷钉住了**：网关对 role 5 放行整个 `/api/admin/**`，而
        //    `/api/admin/permissions/assign-role`（改**全库**任意账号的角色）就在这个前缀下。
        //    admin-service 侧只有 `anyRequest().authenticated()` + **不验签**的
        //    `GatewayAuthFilter`，没有任何角色判断 ⇒ 学院管理员真能改全库账号角色
        //    （建号 `POST /admin/users`、删号 `DELETE /admin/users/{id}` 同病）。
        //    这与 W31 那条 `/api/courses/1` 是同一族毛病：回归护栏把**旧行为当成了要保的行为**。
        //    role 5 是本院管理员，`/api/admin/**` 是全局管理员面，放一起是范畴错误。
        //    role 1/2/3 无需改：它们走不到那一段，下面 `PREFIX_ADMIN → return false` 已经拒了。
        assertTrue(allow(COLLEGE_ADMIN, HttpMethod.GET, "/api/college-admin/classes/1"));
        assertFalse(allow(COLLEGE_ADMIN, HttpMethod.GET, "/api/admin/statistics"));
        assertFalse(allow(COLLEGE_ADMIN, HttpMethod.GET, "/api/roles/count"));
        assertFalse(allow(COLLEGE_ADMIN, HttpMethod.GET, "/api/teachers/list"));

        // —— /api/college-admin 不能被 1/2/3 打到 ——
        assertFalse(allow(STUDENT, HttpMethod.GET, "/api/college-admin/classes/1"));
        assertFalse(allow(TEACHER, HttpMethod.GET, "/api/college-admin/classes/1"));

        // —— 账号/权限管理面 ——
        assertFalse(allow(STUDENT, HttpMethod.GET, "/api/roles/count"));
        assertFalse(allow(TEACHER, HttpMethod.PUT, "/api/roles/update/99999"));

        // —— 归属校验：本人的资料/设置可以，别人的不行 ——
        assertTrue(allowAs(STUDENT, 7L, HttpMethod.GET, "/api/users/7"));
        assertTrue(allowAs(STUDENT, 7L, HttpMethod.PUT, "/api/users/update/7"));
        assertTrue(allowAs(STUDENT, 7L, HttpMethod.GET, "/api/users/7/settings"));
        // 2026-10-03（W8.3）：本人的登录历史（设置页「登录历史」卡片）—— 曾经漏在白名单外，
        // 学生/教师/辅导员一律 403（下游直连同 token 是 200）。本人可查、别人的仍拒。
        assertTrue(allowAs(STUDENT, 7L, HttpMethod.GET, "/api/users/7/login-logs"));
        assertTrue(allowAs(TEACHER, 7L, HttpMethod.GET, "/api/users/7/login-logs"));
        assertFalse(allowAs(STUDENT, 7L, HttpMethod.GET, "/api/users/8/login-logs"));
        assertFalse(allowAs(STUDENT, 7L, HttpMethod.GET, "/api/users/7/login-logs/extra"));
        assertFalse(allowAs(STUDENT, 7L, HttpMethod.GET, "/api/users/8"));
        assertFalse(allowAs(STUDENT, 7L, HttpMethod.POST, "/api/users/add"));
        assertFalse(allowAs(STUDENT, 7L, HttpMethod.GET, "/api/users/by-username/admin"));

        // —— 自助账号路径与角色无关 ——
        assertTrue(allow(STUDENT, HttpMethod.POST, "/api/auth/change-password"));
        assertTrue(allow(COUNSELOR, HttpMethod.POST, "/api/auth/logout"));

        // —— 没被任何前缀管到的路径：已认证即放行（与改动前一致） ——
        assertTrue(allow(STUDENT, HttpMethod.GET, "/api/warnings/scan"));
        // ⚠️ 2026-10-08（W31）：这里原来还有一条
        //      assertTrue(allow(TEACHER, HttpMethod.GET, "/api/courses/1"));
        //    —— 它**把缺陷钉住了**：`/api/courses/**` 对内置角色 1/2/3 当时没有任何检查，
        //    学生/教师可增、改、删课程（同轮直连实测过）。已改为下面的 `courseSurface_*` 组。
        //    `/api/warnings/**` 仍然放行 —— 它的闸在**控制器层**（W26
        //    `IntelligentWarningController.isScanAllowed`），刻意不在网关这层。
    }

    // ==================== /api/courses/**：W31 补的内置角色闸（2026-10-08） ====================
    //
    // 补闸前：`PREFIX_COURSE` 只出现在 `customRoleAllows`（role ≥ 6）里，内置角色 1/2/3
    // 落到 `hasPermission` 末尾的 `return true` ⇒ 整套课程写接口对任何已登录用户开放。
    // 下游 course-service 只 `authenticated()`，所以那不是理论问题（同轮直连实测：学生
    // POST/DELETE 真的落库、PUT 真的改到 courses.id=1）。

    @Test
    @DisplayName("W31：内置角色 1/2/3 进不去课程管理面（补闸前是全部放行）")
    void courseSurface_deniedForBuiltInNonAdminRoles() {
        filter = filterWithBuiltInBindings();

        // 三个写端点是题眼：补闸前 1/2/3 全 200，且真的落库。
        assertFalse(allow(STUDENT, HttpMethod.POST, "/api/courses/add"));
        assertFalse(allow(STUDENT, HttpMethod.PUT, "/api/courses/update/1"));
        assertFalse(allow(STUDENT, HttpMethod.DELETE, "/api/courses/delete/1"));
        assertFalse(allow(TEACHER, HttpMethod.POST, "/api/courses/add"));
        assertFalse(allow(COUNSELOR, HttpMethod.DELETE, "/api/courses/delete/1"));

        // 读也一起拒：这是管理面的分页课程表；教师/学生的课表走 /api/teachers/courses。
        assertFalse(allow(TEACHER, HttpMethod.GET, "/api/courses/1"));
        assertFalse(allow(TEACHER, HttpMethod.GET, "/api/courses/list"));

        // 学院管理员在**这一层**补闸前就已经被拒（`role == ROLE_COLLEGE_ADMIN` 段末尾
        // `return false`）—— 钉住它，免得后人误以为缺口在 5：被放行的恰恰是 1/2/3。
        assertFalse(allow(COLLEGE_ADMIN, HttpMethod.GET, "/api/courses/list"));
    }

    @Test
    @DisplayName("W31：管理员照常通行（新闸不能误伤 4）")
    void courseSurface_adminUnaffected() {
        filter = filterWithBuiltInBindings();
        assertTrue(allow(ADMIN, HttpMethod.GET, "/api/courses/list"));
        assertTrue(allow(ADMIN, HttpMethod.POST, "/api/courses/add"));
        assertTrue(allow(ADMIN, HttpMethod.DELETE, "/api/courses/delete/1"));
    }

    @Test
    @DisplayName("W31：自定义角色的课程面不受新闸影响（仍由 role_permissions 决定）")
    void courseSurface_customRoleStillRbacDriven() {
        // MANAGE ⇒ 读写都行
        filter = filterWith(Map.of("ROLE_ASSISTANT", List.of("PERM_COURSE_MANAGE")));
        assertTrue(allow(CUSTOM, HttpMethod.GET, "/api/courses/list"));
        assertTrue(allow(CUSTOM, HttpMethod.POST, "/api/courses/add"));
        // VIEW ⇒ 只读
        filter = filterWith(Map.of("ROLE_ASSISTANT", List.of("PERM_COURSE_VIEW")));
        assertTrue(allow(CUSTOM, HttpMethod.GET, "/api/courses/list"));
        assertFalse(allow(CUSTOM, HttpMethod.POST, "/api/courses/add"));
    }

    // ==================== /api/course-plans/**：W48 接线时同步补的闸（2026-10-17） ====================
    //
    // 背景：排课面（下游 `CoursePlanController`）此前**无网关路由、无前端入口**，
    // 但**同时不被任何前缀检查命中** —— `/api/course-plans` 与 `/api/courses` 差一个
    // `-plans`，`path.startsWith(PREFIX_COURSE)` 对它恒为假。于是它和当年的
    // `/api/college-admin` 一模一样：内置角色 1/2/3 一路落到 `hasPermission` 末尾的
    // `return true`。
    //
    // 为什么本轮才暴露：**没有路由 ⇒ 走不到下游**，所以它一直是"零调用方的洞"。
    // 本轮给前端接课表时加了 `course-plans-service` 路由，一旦只加路由不补闸，
    // 就等于把一个「建/改/删排课」的写面开给每个登录用户。故**先闸后路由**。
    //
    // 与 W31 的 `/api/courses/**` 同族、同解，但**必须分开测**：`-plans` 这个后缀
    // 就是本条的全部要害，拿 `/api/courses/**` 那组用例是照不到它的。

    @Test
    @DisplayName("W48：内置角色 1/2/3 进不去排课管理面（`-plans` 让 PREFIX_COURSE 照不到它）")
    void coursePlansSurface_deniedForBuiltInNonAdminRoles() {
        filter = filterWithBuiltInBindings();

        // 三个写端点是题眼：只加路由不补闸的话，这三个对任何登录用户都通。
        assertFalse(allow(STUDENT, HttpMethod.POST, "/api/course-plans/create"));
        assertFalse(allow(STUDENT, HttpMethod.PUT, "/api/course-plans/1"));
        assertFalse(allow(STUDENT, HttpMethod.DELETE, "/api/course-plans/1"));
        assertFalse(allow(TEACHER, HttpMethod.POST, "/api/course-plans/create"));
        assertFalse(allow(COUNSELOR, HttpMethod.DELETE, "/api/course-plans/1"));

        // 读也一起拒：这是排课管理面。学生/教师看自己的课表走自助端点，
        // 前端打的是 `/api/students/timetable/{id}` 与 `/api/teachers/courses/timetable`
        // ——**不是**这一条，别把两个面搞混。
        assertFalse(allow(STUDENT, HttpMethod.GET, "/api/course-plans/all"));
        assertFalse(allow(TEACHER, HttpMethod.GET, "/api/course-plans/all"));
        assertFalse(allow(TEACHER, HttpMethod.GET, "/api/course-plans/1"));
        assertFalse(allow(TEACHER, HttpMethod.GET, "/api/course-plans/semester"));

        // 学院管理员与 `/api/courses/**` 同解：课程目录是全局表，本院管理员不管。
        assertFalse(allow(COLLEGE_ADMIN, HttpMethod.GET, "/api/course-plans/all"));
    }

    @Test
    @DisplayName("W48：管理员照常通行（新闸不能误伤 4）")
    void coursePlansSurface_adminUnaffected() {
        filter = filterWithBuiltInBindings();
        assertTrue(allow(ADMIN, HttpMethod.GET, "/api/course-plans/all"));
        assertTrue(allow(ADMIN, HttpMethod.POST, "/api/course-plans/create"));
        assertTrue(allow(ADMIN, HttpMethod.PUT, "/api/course-plans/1"));
        assertTrue(allow(ADMIN, HttpMethod.DELETE, "/api/course-plans/1"));
    }

    @Test
    @DisplayName("W48：自定义角色仍由 role_permissions 决定（MANAGE 读写都行 / VIEW 只读）")
    void coursePlansSurface_customRoleStillRbacDriven() {
        filter = filterWith(Map.of("ROLE_ASSISTANT", List.of("PERM_COURSE_MANAGE")));
        assertTrue(allow(CUSTOM, HttpMethod.GET, "/api/course-plans/all"));
        assertTrue(allow(CUSTOM, HttpMethod.POST, "/api/course-plans/create"));
        filter = filterWith(Map.of("ROLE_ASSISTANT", List.of("PERM_COURSE_VIEW")));
        assertTrue(allow(CUSTOM, HttpMethod.GET, "/api/course-plans/all"));
        assertFalse(allow(CUSTOM, HttpMethod.POST, "/api/course-plans/create"));
    }

    @Test
    @DisplayName("W48：排课面归入 COURSE 模块 —— 否则自定义角色会被 fail-closed 一律拒掉")
    void coursePlansSurface_familyIsCourse() {
        // 上面那条自定义角色用例能通过，靠的正是这个映射：`rbacFamilyOf` 返回 null 时
        // `customRoleAllows` 直接 false，自定义角色就连读都读不到（fail-closed 的另一面）。
        assertEquals("COURSE", AuthenticationFilter.rbacFamilyOf("/api/course-plans/all"));
        assertEquals("COURSE", AuthenticationFilter.rbacFamilyOf("/api/course-plans/1"));
        // 与课程管理面同族，但两条前缀各自独立映射 —— 别把它们合并成一条 startsWith。
        assertEquals("COURSE", AuthenticationFilter.rbacFamilyOf("/api/courses/1"));
    }

    // ==================== /api/admin/**：W33 收掉的学院管理员越权面（2026-10-09） ====================
    //
    // 补闸前：`role == ROLE_COLLEGE_ADMIN` 那一支写的是
    //   `path.startsWith(PREFIX_COLLEGE_ADMIN) || path.startsWith(PREFIX_ADMIN)`
    // —— 注释把理由写成"college-admin 与 admin 前缀"，但**学院管理员是本院管理员**，
    // `/api/admin/**` 是全局管理员面。而针对 `/api/permissions/**` 的那道闸救不了它：
    // `PREFIX_PERMISSIONS` 是 `/api/permissions`，真实路径是 `/api/admin/permissions/...`，
    // 前缀对不上。下游 admin-service 不做角色判断 ⇒ 改角色/建号/删号全通。
    //
    // ⚠️ 被拒的形状与控制器层不同：这里是**网关 403 + body `{"code":403,...}`**，
    // 拦在路由之前。控制器层（`assignRole` 自己的 `X-Role` 闸）是 **HTTP 200 + body code 403**。
    // 两个都要在，因为"网关不是网络边界"（admin-service 本机可直连，且它不验签）。

    @Test
    @DisplayName("W33：学院管理员进不去 /api/admin/**（改角色/建号/删号同族），但学院端自己的面照常")
    void adminSurface_deniedForCollegeAdmin() {
        filter = filterWithBuiltInBindings();

        // —— 题眼：把账号改成学院管理员的那支，补闸前 role 5 自己能调 ——
        assertFalse(allow(COLLEGE_ADMIN, HttpMethod.POST, "/api/admin/permissions/assign-role"));
        // 同病的三条，一并钉住
        assertFalse(allow(COLLEGE_ADMIN, HttpMethod.POST, "/api/admin/users"));
        assertFalse(allow(COLLEGE_ADMIN, HttpMethod.DELETE, "/api/admin/users/1"));
        assertFalse(allow(COLLEGE_ADMIN, HttpMethod.GET, "/api/admin/users/page"));
        // `/api/admin/colleges` 也是全局学院目录（本院信息走 /api/college-admin/profile）
        assertFalse(allow(COLLEGE_ADMIN, HttpMethod.POST, "/api/admin/colleges"));

        // ★ 正对照：改的是"admin 面"这一支，**没有连坐**关掉学院管理端自己的面。
        //   正对照要挑**确实存在**的样本（`/college-admin` 路由组下 9 个页面只打这些），
        //   否则"对照没打中"会被误读成"搜索器坏了"。
        assertTrue(allow(COLLEGE_ADMIN, HttpMethod.GET, "/api/college-admin/dashboard/5"));
        assertTrue(allow(COLLEGE_ADMIN, HttpMethod.GET, "/api/college-admin/warnings/5"));
        assertTrue(allow(COLLEGE_ADMIN, HttpMethod.DELETE, "/api/college-admin/delete-warning/5/9"));
        // 以及"仅限本人"的自助面（小程序「我的」页要用）
        assertTrue(allowAs(COLLEGE_ADMIN, 5L, HttpMethod.GET, "/api/users/info/5"));
        assertTrue(allowAs(COLLEGE_ADMIN, 5L, HttpMethod.GET, "/api/users/5/settings"));
        // 别人的资料权限仍按老规矩拒
        assertFalse(allowAs(COLLEGE_ADMIN, 5L, HttpMethod.GET, "/api/users/7"));

        // ★ 反面：管理员(4) 不受新闸误伤
        assertTrue(allow(ADMIN, HttpMethod.POST, "/api/admin/permissions/assign-role"));
        assertTrue(allow(ADMIN, HttpMethod.GET, "/api/admin/statistics"));
    }

    // ==================== 自定义角色：本轮新增的能力 ====================

    @Test
    @DisplayName("自定义角色：勾了「系统管理」就能真的用管理面（这是改动前做不到的事）")
    void customRole_withSystemManage_canUseAdminSurface() {
        filter = filterWith(Map.of("ROLE_ASSISTANT", List.of("PERM_SYSTEM_MANAGE")));

        assertTrue(allow(CUSTOM, HttpMethod.GET, "/api/admin/statistics"));
        assertTrue(allow(CUSTOM, HttpMethod.POST, "/api/admin/users/add"));
        assertTrue(allow(CUSTOM, HttpMethod.GET, "/api/roles/count"), "全局角色表属于系统管理面");
    }

    @Test
    @DisplayName("自定义角色：没勾「系统管理」⇒ 管理面 403（同一份代码，只差一条绑定）")
    void customRole_withoutSystemManage_isDenied() {
        filter = filterWith(Map.of("ROLE_ASSISTANT", List.of("PERM_COURSE_VIEW")));

        assertFalse(allow(CUSTOM, HttpMethod.GET, "/api/admin/statistics"));
        assertFalse(allow(CUSTOM, HttpMethod.GET, "/api/roles/count"));
    }

    @Test
    @DisplayName("自定义角色：只有「查看课程」⇒ 能读不能写")
    void customRole_viewOnly_cannotWrite() {
        filter = filterWith(Map.of("ROLE_ASSISTANT", List.of("PERM_COURSE_VIEW")));

        assertTrue(allow(CUSTOM, HttpMethod.GET, "/api/courses/1"));
        assertFalse(allow(CUSTOM, HttpMethod.POST, "/api/courses/add"));
    }

    @Test
    @DisplayName("自定义角色：权限表没覆盖的面一律拒绝（fail-closed）")
    void customRole_uncoveredSurface_isDenied() {
        filter = filterWith(Map.of("ROLE_ASSISTANT", List.of("PERM_SYSTEM_MANAGE")));

        // /api/students/** 是学生**本人**的自助面，不是"学生信息管理面"，
        // 所以刻意不映射到 PERM_STUDENT_VIEW —— 自定义角色拿不到（见 rbacFamilyOf 的注释）
        assertFalse(allow(CUSTOM, HttpMethod.GET, "/api/students/dashboard/1"));
        assertFalse(allow(CUSTOM, HttpMethod.GET, "/api/teachers/list"));
        assertFalse(allow(CUSTOM, HttpMethod.GET, "/api/counselors/classes/1"));
    }

    @Test
    @DisplayName("自定义角色：本人的资料/设置仍然可以（与内置角色同一口径）")
    void customRole_selfAccountStillWorks() {
        filter = filterWith(Map.of());

        assertTrue(allowAs(CUSTOM, 6L, HttpMethod.GET, "/api/users/6"));
        assertTrue(allowAs(CUSTOM, 6L, HttpMethod.POST, "/api/auth/change-password"));
        assertFalse(allowAs(CUSTOM, 6L, HttpMethod.GET, "/api/users/7"), "别人的资料照样不行");
    }

    @Test
    @DisplayName("自定义角色 + RBAC 未加载 ⇒ 拒绝（不是碰巧 false）")
    void customRole_withoutRbacData_isDenied() {
        filter = new AuthenticationFilter(new RolePermissionCache());

        assertFalse(allow(CUSTOM, HttpMethod.GET, "/api/admin/statistics"));
        assertFalse(allow(CUSTOM, HttpMethod.GET, "/api/courses/1"));
    }

    @Test
    @DisplayName("URL 前缀 → 功能模块的映射：只映射权限表真有的模块")
    void rbacFamilyOf_onlyMapsRealModules() {
        assertEquals("SYSTEM", AuthenticationFilter.rbacFamilyOf("/api/admin/statistics"));
        assertEquals("SYSTEM", AuthenticationFilter.rbacFamilyOf("/api/roles/count"));
        assertEquals("SYSTEM", AuthenticationFilter.rbacFamilyOf("/api/role-permissions/rbac-map"));
        assertEquals("COURSE", AuthenticationFilter.rbacFamilyOf("/api/courses/1"));
        assertEquals("WARNING", AuthenticationFilter.rbacFamilyOf("/api/warnings/scan"));
        // 刻意不映射：这三条是各角色**本人**的自助面
        assertNull(AuthenticationFilter.rbacFamilyOf("/api/students/dashboard/1"));
        assertNull(AuthenticationFilter.rbacFamilyOf("/api/teachers/list"));
        assertNull(AuthenticationFilter.rbacFamilyOf("/api/counselors/classes/1"));
        // `/api/college-admin` 与 `/api/admin` 不构成前缀关系，别被 startsWith 吃掉
        assertNull(AuthenticationFilter.rbacFamilyOf("/api/college-admin/classes/1"));
    }

    /** 让 IDE 知道 MockServerWebExchange 是可用的（探针里用它造 exchange，这里保留 import）。 */
    @SuppressWarnings("unused")
    private void unusedExchangeHelper() {
        assertNotNull(MockServerWebExchange.from(MockServerHttpRequest.get("/").build()));
    }
}
