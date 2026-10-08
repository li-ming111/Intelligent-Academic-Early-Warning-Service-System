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

        // —— 学院管理员：college-admin 与 admin 两面，但**不碰全局角色表** ——
        assertTrue(allow(COLLEGE_ADMIN, HttpMethod.GET, "/api/college-admin/classes/1"));
        assertTrue(allow(COLLEGE_ADMIN, HttpMethod.GET, "/api/admin/statistics"));
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
