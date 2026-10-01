package com.academic.config;

import com.academic.common.config.GatewayAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
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
     * 公开目录接口（**登录前**的注册页要读，必须匿名可达）。
     *
     * <h3>为什么这里必须显式 permitAll</h3>
     * 网关的 {@code AuthenticationFilter.WHITE_LIST} 里有这三个地址，含义是"不查 token"。
     * 但**网关放行 ≠ 下游放行**：白名单命中的请求在网关是**直接路过**的，
     * 网关照常**不会**注入 {@code X-User-Id}/{@code X-Role}（它压根没解析 token），
     * 而本服务的 {@code GatewayAuthFilter} 只在"有头"时才建 {@code Authentication}。
     * 于是请求以匿名身份落到下面这条 {@code .anyRequest().authenticated()} 上 → **403**。
     *
     * <p>2026-09-30 用 {@code PublicDirectoryAccessTest} 实测确认（不是读码推断）：
     * 匿名 {@code GET /majors/all} → {@code 403}，同一路径带上网关照常注入的三个头 → {@code 200}。
     * 后果：登录前的注册页里，**学院/专业下拉框永远是空的** —— 而教师/辅导员注册表单
     * 把学院设为必填，那两个页面根本提交不了；学生注册页的"输入学号自动填充专业"也永远
     * 显示"（加载失败）"。注册页在登录页之前，换任何 token 都救不了。
     *
     * <p>只放开这三条**只读目录**，不放开 {@code /colleges/**} 整个前缀：
     * {@code /majors/list}（分页管理）、{@code /majors/add|update|delete} 等仍然要认证
     * —— 测试里有一条专门钉这个边界。
     */
    private static final String[] PUBLIC_DIRECTORY = {
            "/colleges/all",   // 学院目录（PC 端注册页/个人设置里的学院下拉）
            "/majors/all",     // 专业目录（注册页按学号自动匹配专业）
            "/classes/all"     // 班级目录（小程序注册页选班级；见 ClassController）
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, GatewayAuthFilter gatewayAuthFilter) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers(PUBLIC_DIRECTORY).permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(gatewayAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
