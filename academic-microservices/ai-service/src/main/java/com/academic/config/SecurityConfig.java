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

/**
 * AI Service 安全配置
 *
 * ⚠️ 2026-09-30：这个类原来长这样 ——
 * <pre>
 *   .requestMatchers("/actuator/health", "/api/ai/**").permitAll()
 *   .anyRequest().authenticated()
 * </pre>
 * 而它**没有注册 GatewayAuthFilter**（另外 11 个服务都注册了），于是没有任何东西会把
 * Gateway 注入的 X-User-Id/X-Role 变成 Spring Security 的认证 → `anyRequest().authenticated()`
 * 恒不满足 → **整个 ai-service 恒 403**。
 *
 * 那句 `permitAll("/api/ai/**")` 是第二层错觉：网关 StripPrefix=1 之后到达本服务的路径是
 * `/ai/chat`，**不带** `/api` 前缀，这条 matcher 一次都没匹配上。所以"AI 功能不可用"
 * 与 DashScope key 无关 —— 请求在进 Controller 之前就被拒了。
 * 实测（经网关，role=4/3/1/2 四个账号）：`POST /api/ai/chat` 一律
 * `403`，响应体为空、带 Spring Security 的 X-Frame-Options/X-Content-Options 头
 * （不是网关那个会写 `{"code":403,"message":"权限不足"}` 的 403 —— 那个有 body）。
 *
 * 对齐其余 11 个服务的写法：只放行健康检查，认证交给 GatewayAuthFilter，
 * 由网关注入的请求头建立认证上下文。直连本服务（无那些头）仍然 403，与全仓口径一致。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public GatewayAuthFilter gatewayAuthFilter() {
        return new GatewayAuthFilter();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, GatewayAuthFilter gatewayAuthFilter) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(gatewayAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
