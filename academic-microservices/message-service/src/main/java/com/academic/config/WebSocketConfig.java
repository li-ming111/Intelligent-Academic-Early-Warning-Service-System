package com.academic.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket 配置类
 * 配置 STOMP 消息代理和端点
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtStompAuthInterceptor jwtStompAuthInterceptor;

    public WebSocketConfig(JwtStompAuthInterceptor jwtStompAuthInterceptor) {
        this.jwtStompAuthInterceptor = jwtStompAuthInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 启用简单消息代理，配置前缀
        config.enableSimpleBroker("/topic", "/queue");
        // 配置应用前缀，客户端发送消息需要以此开头
        config.setApplicationDestinationPrefixes("/app");
        // 配置用户目标前缀
        config.setUserDestinationPrefix("/user");
    }

    /**
     * 在 STOMP CONNECT 帧上装身份（2026-10-02，W3.2）。
     *
     * <p>没有这一步时，会话没有 {@code Principal}，
     * {@code convertAndSendToUser(userId, "/queue/messages", …)} 在
     * {@code SimpUserRegistry} 里找不到叫这个名字的用户 ⇒ **一条都不发、也不报错**。
     * 症状是"连得上、订阅得上、日志正常，页面永远不动"。
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtStompAuthInterceptor);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 注册 WebSocket 端点，客户端通过此端点连接
        // 注册 Token 握手拦截器用于身份验证
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(new WebSocketInterceptor());
        // 添加 SockJS 支持作为备选
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(new WebSocketInterceptor())
                .withSockJS();
    }
}
