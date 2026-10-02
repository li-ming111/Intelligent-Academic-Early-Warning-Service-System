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
                .requestMatchers("/roles/**", "/permissions/**", "/role-permissions/**")
                    .hasRole("ADMIN")
                .anyRequest().authenticated())
            .addFilterBefore(gatewayAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable());

        return http.build();
    }
}
