package com.academic.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手拦截器。
 *
 * <p><b>⚠️ 2026-10-02（W3.2）更正了本类的注释</b>：原来写的是「用于在 WebSocket 连接建立前
 * 验证 Token」，但它**从来没有验证过任何东西** —— {@code beforeHandshake} 恒
 * {@code return true}，只把 token 塞进 attributes 就完了，而**没有任何人读那个 attribute**。
 *
 * <p>而且那段"从请求头取 Authorization"是**取不到的**：浏览器的 WebSocket API 不允许设置
 * 自定义 HTTP 头，{@code @stomp/stompjs} 的 {@code connectHeaders} 是发在 **STOMP CONNECT 帧**
 * 里的（socket 建立之后），握手请求里根本没有 {@code Authorization}。
 * 所以这里只留查询参数那条路（给非浏览器客户端/探针脚本用），并在日志里说明真相。
 *
 * <p><b>身份不在这一层装</b>：真正的验票在 {@link JwtStompAuthInterceptor}
 * （STOMP CONNECT 帧上取 {@code Authorization}，验完给会话装 {@code Principal}）。
 * 换句话说，本类现在的职责只剩"记录一次握手"。
 */
@Slf4j
public class WebSocketInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        log.info("WebSocket handshake attempt from: {}", request.getRemoteAddress());

        // 浏览器端**不会**带 Authorization 头（WebSocket API 不允许设自定义头），
        // 这里只对非浏览器客户端/探针脚本有意义：`ws://host/ws?token=xxx`。
        String token = request.getHeaders().getFirst("Authorization");
        if (token == null || token.isEmpty()) {
            if (request instanceof ServletServerHttpRequest servletRequest) {
                token = servletRequest.getServletRequest().getParameter("token");
            }
        }
        log.info("WebSocket 握手：{}（身份不在这里装，见 JwtStompAuthInterceptor）",
                token != null ? "带了令牌" : "没带令牌");

        // 允许连接。**这不是"认证通过"** —— 验票发生在 STOMP CONNECT 帧上。
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        log.info("WebSocket handshake completed");
    }
}
