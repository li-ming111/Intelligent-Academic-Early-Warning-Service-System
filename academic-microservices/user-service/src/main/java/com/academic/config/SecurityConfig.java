package com.academic.config;

import com.academic.filter.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // ⚠️ 这里**刻意逐个列出**，不用 `/auth/register/**` 通配 —— 通配会把
                // `/auth/register` 和 `/auth/register/admin` 也放进匿名白名单，而那两个端点
                // 建的是账号（admin 那个建的是 **role=4 系统管理员**）。网关那张白名单
                // (`AuthenticationFilter.WHITE_LIST`) 本来就是逐个列 student|teacher|counselor
                // 的 —— 两张表口径必须一致，否则"网关是唯一真实入口"这个前提一破，
                // 直连 8082 就绕开了网关注册闸。
                // 2026-09-30 实测（服务直连、不带任何 token）: 改前 `POST :8082/api/auth/register/admin`
                // **能走到业务逻辑**（回 `code:500 用户名已存在`）；改后应被这一行挡下。
                // 2026-10-01：`/auth/register/admin` 端点本身已删（零调用方 + 与 `/users/add` 重复），
                // 所以现在这条注释守的是裸的 `/auth/register`（它按最低权限建学生）。
                .requestMatchers("/auth/login",
                        "/auth/register/student", "/auth/register/teacher", "/auth/register/counselor",
                        "/auth/refresh-token", "/auth/colleges", "/auth/majors").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}