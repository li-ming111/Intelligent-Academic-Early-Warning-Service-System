package com.academic.config;

import com.academic.common.config.GatewayAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public GatewayAuthFilter gatewayAuthFilter() {
        return new GatewayAuthFilter();
    }

    /**
     * 谁能调课程管理面（`/courses/**`）：管理员（4）或自定义角色（role ≥ 6）。
     *
     * <p>与网关 `AuthenticationFilter.hasPermission` 对 `/api/courses/**` 的**最终结果同解**
     * —— 那边 4 在函数开头 `return true`、≥ 6 走权限表、1/2/3/5 拒（W31 补的闸）。
     * 这里刻意重写一遍而不共享常量：两个模块没有公共依赖，且口径必须各自可读。
     *
     * <p>写成 `static` 是为了能被单测直接打（不必起 Spring 上下文）——
     * 与网关那边 `hasPermission` 做成包级可见是同一个理由：判据埋在 mock 里看不清。
     */
    static boolean isCourseManager(String role) {
        if (role == null) {
            return false;
        }
        if ("4".equals(role) || "admin".equals(role)) {
            return true;
        }
        try {
            return Integer.parseInt(role) >= 6;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, GatewayAuthFilter gatewayAuthFilter) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                // ==================== 课程管理面（W31，2026-10-08） ====================
                //
                // 本控制器（`CourseController`，`@RequestMapping("/courses")`）是**课程管理面**
                // （分页课程表 + 增/改/删），此前整个服务只有 `anyRequest().authenticated()`
                // ——`GatewayAuthFilter` 是**按请求头建认证、不验签**的，所以"能带上
                // X-User-Id/X-Role 头"就等于"是那个角色"。而网关对 `/api/courses/**`
                // 对内置角色 1/2/3 本来也不拦（同轮已就地补闸），两层都没有 ⇒ **任何已登录
                // 用户（含学生）可增/改/删课程**（2026-10-08 直连实测见网关那一段注释）。
                //
                // 这里补**第二层**，理由是 auth-service `SecurityConfig` 里那句同样成立的
                // 话：**网关不是网络边界** —— 本机可以直连 8075，所以"网关拦住了"不足以
                // 当作授权结论。两层都写，任一层单独失效时不至于全开。
                //
                // 放行面 = 管理员（4）+ 自定义角色（≥ 6）。后者刻意以数字判定而不查权限表：
                // 权限表在**网关**那层（`RolePermissionCache`），本服务没有它；
                // 直连时无凭据可查，这与原状态一致（直连的场景下 X-Role 头本来就是自证的）。
                // 学院管理员（5）不放行 —— 它管本院事务，课程目录是全局表；
                // 它在网关那层对 `/api/courses/**` 本来也是 `return false`（`hasPermission` 的
                // `role == ROLE_COLLEGE_ADMIN` 段末尾），两层口径一致。
                .requestMatchers("/courses/**").access((authentication, context) ->
                        new AuthorizationDecision(isCourseManager(context.getRequest().getHeader("X-Role"))))
                // ==================== 排课面（W48，2026-10-17） ====================
                //
                // `CoursePlanController`（`@RequestMapping("/course-plans")`）此前整个面
                // **既无网关路由、也无前端入口** —— 但同时它也不在任何闸内：`/course-plans/**`
                // 落进下面的 `anyRequest().authenticated()`，而 `GatewayAuthFilter` 是按请求头
                // 建认证、不验签的 ⇒ **本机直连 8075 带一个 X-Role 头就能建/改/删排课**。
                // 与 `/courses/**` 是同一族（网关那层已同步补 `PREFIX_COURSE_PLANS`）。
                //
                // 放行面与 `/courses/**` 一致：管理员（4）+ 自定义角色（≥ 6）。排课是课程管理
                // 的一部分，口径不该分叉。学生/教师不直连这里 —— 他们的「我的课表」走
                // student-service / teacher-service 的自助端点（那边各自有归属闸）。
                .requestMatchers("/course-plans/**").access((authentication, context) ->
                        new AuthorizationDecision(isCourseManager(context.getRequest().getHeader("X-Role"))))
                .anyRequest().authenticated()
            )
            .addFilterBefore(gatewayAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
