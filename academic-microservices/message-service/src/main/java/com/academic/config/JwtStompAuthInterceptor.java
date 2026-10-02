package com.academic.config;

import com.academic.common.config.JwtSecretGuard;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Principal;

/**
 * 给 STOMP 会话装上「身份」（W3.2，2026-10-02）。
 *
 * <h3>为什么必须有这个类</h3>
 * 服务端推实时通知走的是
 * {@code messagingTemplate.convertAndSendToUser(userId, "/queue/messages", payload)}
 * （见 {@code NotificationMessageListener} 与 {@code MessageServiceImpl.sendMessage}）。
 * Spring 解析这个目的地时，靠的是**会话的 {@link Principal}**：
 * 它会去 {@code SimpUserRegistry} 里找名字等于那个 userId 的用户，找不到就
 * **一条都不发、也不报错**。
 *
 * <p>而在本类出现之前，本服务的 STOMP 会话**根本没有 Principal** ——
 * {@code WebSocketInterceptor} 是个只打日志、恒 {@code return true} 的空壳。
 * 于是整条推送链是"连得上、订阅得上、日志正常、页面永远不动"。
 *
 * <h3>为什么不在握手时验票</h3>
 * 浏览器的 WebSocket API **不允许设置自定义 HTTP 头**，所以握手请求里**永远不会有**
 * {@code Authorization}（{@code WebSocketInterceptor} 里那段读 Authorization 的代码
 * 因此是死的）。@stomp/stompjs 的 {@code connectHeaders} 是**STOMP CONNECT 帧**的头，
 * 发在 socket 建立**之后**。所以身份只能在 CONNECT 帧上取 —— 就是本类做的事。
 *
 * <h3>和网关的关系</h3>
 * 网关的 {@code /ws/**} 在 WHITE_LIST 里，命中后是 {@code chain.filter(exchange)} 原样放行、
 * **不注入** {@code X-User-Id}/{@code X-Role}（同 §7 那条口径）。所以这里既不能指望
 * 网关注入的头，也不能指望它验票 —— 验票必须在本服务自己做。密钥与 auth-service/gateway
 * 是同一份（默认值也刻意保持一致，三方对齐）。
 *
 * <p><b>验不过票时不拒绝连接</b>：只是没有 Principal。这样公开订阅（{@code /topic/**}）
 * 照常可用，而 {@code /user/**} 的推送会自然落空 —— 与"连接直接断开"相比，行为更容易排查。
 */
@Slf4j
@Component
public class JwtStompAuthInterceptor implements ChannelInterceptor {

    /**
     * 与 {@code auth-service/JwtUtil} 和 {@code gateway/AuthenticationFilter} 用同一份密钥。
     * 默认值刻意逐字相同：三方任何一方改了默认值，登录能过、推送静默失效，最难查。
     */
    @Value("${jwt.secret:${JWT_SECRET:academic-jwt-secret-key-for-academic-warning-system-2026}}")
    private String secret;

    @PostConstruct
    void warnIfUsingDevSecret() {
        JwtSecretGuard.warnIfDefault(secret, log, "message-service JwtStompAuthInterceptor");
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }
        Principal user = principalOf(accessor.getFirstNativeHeader("Authorization"));
        if (user != null) {
            accessor.setUser(user);
        } else {
            log.warn("WebSocket CONNECT 未带有效令牌，本次会话没有身份（/user/** 推送将收不到）");
        }
        return message;
    }

    /** 解析 {@code Bearer <token>}；任何一步不成立都返回 null（而不是抛出去把连接打断）。 */
    private Principal principalOf(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        String token = authorization.substring(7).trim();
        if (token.isEmpty()) {
            return null;
        }
        try {
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            Long userId = claims.get("userId", Long.class);
            if (userId == null) {
                log.warn("WebSocket 令牌里没有 userId，无法定位收件人");
                return null;
            }
            // Principal 的名字**必须**是 userId 的字符串形式 ——
            // convertAndSendToUser(userId, …) 就是按这个名字找会话的。
            return () -> String.valueOf(userId);
        } catch (Exception e) {
            log.warn("WebSocket 令牌解析失败: {}", e.getMessage());
            return null;
        }
    }
}
