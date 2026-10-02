package com.academic.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * STOMP 身份拦截器（W3.2，2026-10-02）。
 *
 * <p><b>修之前是什么样</b>：STOMP 会话**没有 Principal**。
 * {@code WebSocketInterceptor} 是个恒 {@code return true}、只打日志的空壳，而且它读的
 * {@code Authorization} **根本不存在** —— 浏览器的 WebSocket API 不允许设自定义 HTTP 头，
 * {@code @stomp/stompjs} 的 {@code connectHeaders} 是发在 STOMP CONNECT 帧里的。
 *
 * <p>后果是整条推送链**静默失效**：
 * {@code convertAndSendToUser(userId, "/queue/messages", …)} 去 {@code SimpUserRegistry}
 * 里找名字等于 userId 的用户，找不到就**一条都不发、也不报错**，日志里还照样打
 * 「实时消息推送成功」。
 *
 * <p>这里钉五件事：<br>
 * ① CONNECT 带合法令牌 ⇒ 会话拿到 Principal，且**名字就是 userId**（名字错了照样找不到人）；<br>
 * ② 没带令牌 ⇒ 不装身份，但**不抛异常**（公开订阅 {@code /topic/**} 还要能用）；<br>
 * ③ 令牌是别的密钥签的 ⇒ 必须验不过（证明它真的在**验签**，不是只解码）；<br>
 * ④ 令牌里没有 userId ⇒ 装不了身份（不能装个名字为 "null" 的 Principal 上去）；<br>
 * ⑤ 非 CONNECT 帧（SUBSCRIBE 等）一律不动。
 */
class JwtStompAuthInterceptorTest {

    /** 与三个服务共用的开发默认密钥（仓库里就有，不是秘密）。 */
    private static final String SECRET = "academic-jwt-secret-key-for-academic-warning-system-2026";

    private JwtStompAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new JwtStompAuthInterceptor();
        ReflectionTestUtils.setField(interceptor, "secret", SECRET);
    }

    private static String token(String secret, Map<String, Object> claims) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .claims(claims)
                .subject(String.valueOf(claims.getOrDefault("username", "u")))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(key)
                .compact();
    }

    private static String validToken(long userId) {
        return token(SECRET, Map.of("userId", userId, "username", "admin", "role", 4));
    }

    /** 造一条 STOMP 帧，返回处理后的 accessor。 */
    private StompHeaderAccessor send(StompCommand command, String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (authorization != null) {
            accessor.setNativeHeader("Authorization", authorization);
        }
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> out = interceptor.preSend(message, null);
        assertNotNull(out, "preSend 不能把消息吞掉");
        return MessageHeaderAccessor.getAccessor(out, StompHeaderAccessor.class);
    }

    @Test
    @DisplayName("CONNECT 带合法令牌 ⇒ Principal 的名字就是 userId")
    void connectWithValidToken_installsPrincipal() {
        StompHeaderAccessor out = send(StompCommand.CONNECT, "Bearer " + validToken(56L));

        assertNotNull(out.getUser(), "没有 Principal ⇒ convertAndSendToUser 找不到人");
        assertEquals("56", out.getUser().getName(),
                "Principal 的名字必须与 convertAndSendToUser 传的那个字符串逐字相同");
    }

    @Test
    @DisplayName("没带 Authorization ⇒ 不装身份，也不抛异常（公开订阅还要能用）")
    void connectWithoutToken_leavesNoPrincipal() {
        assertNull(send(StompCommand.CONNECT, null).getUser());
    }

    @Test
    @DisplayName("★ 令牌是别的密钥签的 ⇒ 必须验不过（证明在验签，不是只解码）")
    void connectWithForeignlySignedToken_isRejected() {
        String forged = token("another-secret-key-that-is-long-enough-for-hs256!!",
                Map.of("userId", 56L, "username", "admin", "role", 4));

        assertNull(send(StompCommand.CONNECT, "Bearer " + forged).getUser(),
                "换个密钥签出来的令牌必须被拒 —— 否则谁都能冒充任意用户收推送");
    }

    @Test
    @DisplayName("令牌里没有 userId ⇒ 装不了身份（不能装个名字叫 null 的 Principal）")
    void connectWithoutUserIdClaim_leavesNoPrincipal() {
        String noUser = token(SECRET, Map.of("username", "admin", "role", 4));

        assertNull(send(StompCommand.CONNECT, "Bearer " + noUser).getUser());
    }

    @Test
    @DisplayName("垃圾令牌 / 缺 Bearer 前缀 ⇒ 不装身份，不抛")
    void connectWithGarbageToken_leavesNoPrincipal() {
        assertNull(send(StompCommand.CONNECT, "Bearer not-a-jwt").getUser());
        assertNull(send(StompCommand.CONNECT, validToken(56L)).getUser(), "少了 Bearer 前缀就不认");
        assertNull(send(StompCommand.CONNECT, "Bearer ").getUser(), "空令牌");
    }

    @Test
    @DisplayName("非 CONNECT 帧一律不动（SUBSCRIBE/SEND 不该被这个拦截器改写）")
    void nonConnectFramesAreUntouched() {
        assertNull(send(StompCommand.SUBSCRIBE, "Bearer " + validToken(56L)).getUser());
        assertNull(send(StompCommand.SEND, "Bearer " + validToken(56L)).getUser());
    }
}
