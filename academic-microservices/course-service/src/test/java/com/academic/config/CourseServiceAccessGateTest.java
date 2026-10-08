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
}
