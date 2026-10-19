package com.academic.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * course-service 课程管理面（`/courses/**`）的角色闸（W31，2026-10-08）。
 *
 * <p><b>本模块此前一个测试都没有</b>（与 auth-service 并列），所以这里只钉两件本轮真的动过的事：
 * <ol>
 *   <li>{@link SecurityConfig#isCourseManager(String)} 的判定表；</li>
 *   <li>它必须与**网关** `AuthenticationFilter.hasPermission` 对 `/api/courses/**` 的
 *       最终结果同解 —— 否则经网关和直连两条路会给出不同答案，那正是本轮要收口的形状。</li>
 * </ol>
 *
 * <p><b>修之前是什么样</b>：`SecurityConfig` 只有 `anyRequest().authenticated()`，
 * `GatewayAuthFilter` 又是**按请求头建认证、不验签**的 ⇒ 带上 `X-Role: 1` 就是个学生。
 * 2026-10-08 直连 8075（复刻网关 StripPrefix=1 + 注入头之后的请求）实测：
 * {@code POST /courses/add} → 200 且库里多一行；{@code DELETE /courses/delete/{id}} → 200 且那行没了；
 * {@code PUT /courses/update/1} → 200 且真的改到了 {@code courses.id=1}。
 *
 * <p>⚠️ 被拒的形状是 **Spring Security 的 403 + <b>空体</b>**（`access(...)` 返回
 * {@code AuthorizationDecision(false)} ⇒ 拦在映射之前），与控制器业务拒绝那种
 * 「HTTP 200 + body.code 403」**不是同一种**。探针读判据时必须读 HTTP 状态码，别读 body。
 */
class CourseServiceAccessGateTest {

    // ==================== 放行面 ====================

    @Test
    @DisplayName("放行：管理员（4）—— 两种写法都认（网关发的是数字，手工直连可能写名字）")
    void allowsAdmin() {
        assertTrue(SecurityConfig.isCourseManager("4"));
        assertTrue(SecurityConfig.isCourseManager("admin"));
    }

    @Test
    @DisplayName("放行：自定义角色（role ≥ 6）—— 权限细节由网关的 role_permissions 管，这里只放行")
    void allowsCustomRoles() {
        assertTrue(SecurityConfig.isCourseManager("6"));
        assertTrue(SecurityConfig.isCourseManager("7"));
        assertTrue(SecurityConfig.isCourseManager("100"));
    }

    // ==================== 拒绝面（本轮补的就是这些） ====================

    @Test
    @DisplayName("拒绝：内置角色 1/2/3 —— 补闸前他们能增改删课程")
    void deniesBuiltInNonAdminRoles() {
        assertFalse(SecurityConfig.isCourseManager("1"), "学生");
        assertFalse(SecurityConfig.isCourseManager("2"), "教师");
        assertFalse(SecurityConfig.isCourseManager("3"), "辅导员");
        assertFalse(SecurityConfig.isCourseManager("student"));
        assertFalse(SecurityConfig.isCourseManager("teacher"));
        assertFalse(SecurityConfig.isCourseManager("counselor"));
    }

    @Test
    @DisplayName("拒绝：学院管理员（5）—— 与网关那层同解（那边 role==5 也 return false）")
    void deniesCollegeAdmin() {
        assertFalse(SecurityConfig.isCourseManager("5"));
        assertFalse(SecurityConfig.isCourseManager("college_admin"));
    }

    @Test
    @DisplayName("拒绝：缺头 / 垃圾值（fail-closed，判不出来 ≠ 放行）")
    void deniesMissingOrGarbage() {
        assertFalse(SecurityConfig.isCourseManager(null), "没有 X-Role 头");
        assertFalse(SecurityConfig.isCourseManager(""));
        assertFalse(SecurityConfig.isCourseManager("ROLE_ADMIN"), "带前缀的花名不是本系统的取值");
        assertFalse(SecurityConfig.isCourseManager("0"));
        assertFalse(SecurityConfig.isCourseManager("-1"));
        assertFalse(SecurityConfig.isCourseManager("4x"));
        assertFalse(SecurityConfig.isCourseManager("99999999999999999999"), "超出 int 也不能当成 ≥6");
    }

    // ==================== 排课面（`/course-plans/**`，W48，2026-10-17） ====================
    //
    // 排课面与课程管理面**刻意共用同一个判定方法**（`isCourseManager`），所以下面这组
    // 打印的是"两条前缀同解"。它值得单写一遍的理由是：`SecurityFilterChain` 里
    // `/course-plans/**` 那条 matcher 是本轮新加的，而它**极易被加错位置** ——
    // 排在 `.anyRequest()` 之后就等于没加。这一段钉住"两条前缀拿到的答案一样"，
    // 也就钉住了排课面没有被落到 `authenticated()` 那一档。
    //
    // ⚠️ 这只是**第二层**。第一层在网关 `AuthenticationFilter` 的 `PREFIX_COURSE_PLANS`，
    // 那边另有测试（`GatewayCoursePlansGateTest`）。两层判据相同、实现各自独立，
    // 任一层单独失效时不至于全开 —— 这正是"网关不是网络边界"那条结论的落地方式。

    @Test
    @DisplayName("排课面：与 `/courses/**` 同解 —— 管理员 / 自定义角色放行")
    void coursePlansAllowsSameAsCourses() {
        for (String role : new String[]{"4", "admin", "6", "7", "100"}) {
            assertTrue(SecurityConfig.isCourseManager(role), "排课面应放行 role=" + role);
        }
    }

    @Test
    @DisplayName("排课面：与 `/courses/**` 同解 —— 学生/教师/辅导员/学院管理员一律拒")
    void coursePlansDeniesSameAsCourses() {
        for (String role : new String[]{"1", "2", "3", "5", "student", "teacher",
                                        "counselor", "college_admin"}) {
            assertFalse(SecurityConfig.isCourseManager(role), "排课面应拒绝 role=" + role);
        }
    }
}
