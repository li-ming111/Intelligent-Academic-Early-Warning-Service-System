package com.academic.config;

import com.academic.common.config.GatewayAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置
 * 仅放行登录和公开注册接口，其余请求需通过 GateWay 认证。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public GatewayAuthFilter gatewayAuthFilter() {
        return new GatewayAuthFilter();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, GatewayAuthFilter gatewayAuthFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/login").permitAll()
                .requestMatchers("/auth/register/student").permitAll()
                .requestMatchers("/auth/register/teacher").permitAll()
                .requestMatchers("/auth/register/counselor").permitAll()
                // `/auth/courses` 于 2026-10-02（W1.15）从这一行里删掉：本服务没有这个映射
                // （`auth-service/.../controller/AuthController.java` 是个 **0 字节空文件**，
                // 真正的登录/注册控制器在 user-service，那边也没有 `/courses`），全仓零调用方。
                // 与网关 WHITE_LIST 里那条是**同一次改动的两半** —— 只删一处会留下一条
                // 永远不成立的放行。`/auth/colleges`、`/auth/majors` 是真路由（网关
                // StripPrefix=2 + RewritePath 打到 college-service），保留。
                .requestMatchers("/auth/colleges", "/auth/majors").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                // 2026-10-01 第二十四轮：账号与权限管理面只对系统管理员开放。
                //
                // 网关那层也补了同样的闸，这里是**第二层** —— 因为网关不是网络边界：
                // 本机可以直连 8082/8072，而 GatewayAuthFilter 是**按请求头**建 Authentication 的
                // （不验签，见 academic-common 的 GatewayAuthFilter），所以"网关拦住了"不足以当作
                // 授权结论。两层都写，任一层单独失效时不至于全开。
                //
                // 角色名来自 GatewayAuthFilter.roleMapping：role=4 → ROLE_ADMIN、5 → ROLE_COLLEGE_ADMIN。
                // 学院管理员**不含**在内：它管本院事务，不管全局角色。
                //
                // 无副作用：这三条前缀全仓零调用方；网关自己的 RolePermissionCache 取 RBAC 是
                // 直连本服务且**不带任何请求头**，本来就落在 anyRequest().authenticated() 上被 401/403，
                // 这里收紧后仍是拒绝 —— 行为不变。
                // ⚠️ 下面这条 permitAll **必须写在 `/role-permissions/**` 那条之前** ——
                // Spring Security 按声明顺序取**第一条命中**的规则，放后面就永远轮不到它。
                //
                // 2026-10-02（W3.3）：`/role-permissions/rbac-map` 是**网关**在**启动时**读的
                // 角色→权限字典。网关那一刻还没有任何用户身份（没有 token、没有 X-User-Id），
                // 所以它拿不到 ROLE_ADMIN；从前这个端点因此**必然 403**，网关的 RBAC 层
                // 自上线起就是死的（详见 `RolePermissionCache` 类注释）。
                //
                // 放开它是安全的、也是必需的：
                //  ① 内容是一份**功能模块清单**（`ROLE_STUDENT → PERM_STUDENT_VIEW`），
                //     不含账号、不含凭据、不含任何个人信息；
                //  ② 它是**只读**的（GET），写面（`/role-permissions/add|update|delete`）
                //     仍然要求 ROLE_ADMIN；
                //  ③ 全仓**只有网关一个调用方**（grep `rbac-map` 只有两处：本端点与网关的
                //     `RolePermissionCache`），不存在"给别的调用方开口子"的连带影响。
                //
                // ⚠️ 曾经担心的"放开它会导致 role 1/2/3 全面 403 雪崩"已经不成立：
                //    那种雪崩的成因是**权限码词表两处不匹配**（库 `PERM_STUDENT_VIEW` vs
                //    代码 `student:read`）。W3.3 把这层词表整个拿掉了 —— 网关只对
                //    **内置角色以外的自定义角色（id ≥ 6）** 查权限表，内置角色 1–5
                //    仍然完全由路径前缀层决定，行为一字未改。
                .requestMatchers("/role-permissions/rbac-map").permitAll()
                .requestMatchers("/roles/**", "/permissions/**", "/role-permissions/**")
                    .hasRole("ADMIN")
                .anyRequest().authenticated())
            .addFilterBefore(gatewayAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable());

        return http.build();
    }
}
