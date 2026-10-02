package com.academic.mq;

import com.academic.config.RabbitMQConfig;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.test.util.ReflectionTestUtils;import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MQ 通知消费者（W3.1，2026-10-02）。
 *
 * <p><b>修之前是什么样</b>：全仓 **0 个 {@code @RabbitListener}**。
 * {@code notification.exchange} 与 {@code message.notification.queue} 之间的绑定在
 * {@code RabbitMQConfig} 里声明得好好的，但没有任何消费者 —— 生产侧（{@code WarningNotifier}）
 * 发出去的消息只会堆在队列里。文档写的那条「业务事件 → RabbitMQ → WebSocket 实时推送」
 * 只有前半截。
 *
 * <p><b>这里钉六件事</b>：
 * <br>① 服务端推送到 {@code convertAndSendToUser(userId, "/queue/messages", …)} —— 与
 *     {@code MessageServiceImpl.sendMessage} **同一个目的地**，客户端只需订阅一次。
 *     ⚠️ **客户端要订的是 {@code /user/queue/messages}**，不是
 *     {@code /user/{userId}/queue/messages}（2026-10-02 实测：写成后者连得上、
 *     订阅得上、服务端还打「推送成功」，就是一条收不到）；
 * <br>①′ 「在线判断」只改日志、**不改变投递**：{@code convertAndSendToUser} 在对方没有
 *     会话时是静默 no-op，所以要查 {@code SimpUserRegistry} 才能说实话；但**不能**因此把
 *     推送变成「在线才发」—— 注册表登记有延迟，刚连上的会话会被漏掉；
 * <br>② 成功要 **ack**。容器是 {@code AcknowledgeMode.MANUAL}，不 ack 的消息会一直挂在
 *     unacked 里、连接一断就重投，症状是日志里反复出现同一条通知；
 * <br>③ 没有收件人的消息要**丢弃并 ack**，不是 nack —— 重投多少次都还是没有收件人；
 * <br>④ 推送失败要 {@code nack(requeue=false)}，**绝不重投**（通知已在业务侧落库，
 *     重投只会无限循环）；
 * <br>⑤ {@code @RabbitListener} 上写的队列名必须与 {@code RabbitMQConfig} 里声明的
 *     那个 bean 一致 —— 两边各写一个字面量，改名时漏掉一边就会"监听一个不存在的队列"。
 */
@ExtendWith(MockitoExtension.class)
class NotificationMessageListenerTest {

    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private Channel channel;
    @InjectMocks private NotificationMessageListener listener;

    private Map<String, Object> payload(Object userId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("event", "warning.notify");
        m.put("userId", userId);
        m.put("title", "学习预警");
        m.put("content", "你有 2 门课程不及格");
        m.put("warningId", 7L);
        return m;
    }

    @Test
    @DisplayName("推送到 /user/{userId}/queue/messages，且带上原始内容")
    void pushesToUserQueue() throws IOException {
        listener.onNotification(payload(56L), channel, 1L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSendToUser(eq("56"), eq("/queue/messages"), body.capture());

        assertEquals("warning.notify", body.getValue().get("event"));
        assertEquals("学习预警", body.getValue().get("title"));
        assertEquals("你有 2 门课程不及格", body.getValue().get("content"));
        assertEquals(7L, body.getValue().get("warningId"));
        assertNotNull(body.getValue().get("timestamp"), "应带上推送时刻，前端可用来去重/排序");
    }

    @Test
    @DisplayName("成功要 ack（MANUAL 模式不 ack 会被反复重投）")
    void acksOnSuccess() throws IOException {
        listener.onNotification(payload(56L), channel, 42L);

        verify(channel).basicAck(42L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("没有收件人：丢弃并 ack，不推也不重投")
    void missingUser_dropsAndAcks() throws IOException {
        Map<String, Object> noUser = new LinkedHashMap<>();
        noUser.put("event", "warning.notify");
        noUser.put("title", "x");

        listener.onNotification(noUser, channel, 43L);

        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
        verify(channel).basicAck(43L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("userId 是字符串也要认（JSON 反序列化可能是 String）")
    void acceptsStringUserId() throws IOException {
        listener.onNotification(payload("56"), channel, 44L);

        verify(messagingTemplate).convertAndSendToUser(eq("56"), eq("/queue/messages"), any());
        verify(channel).basicAck(44L, false);
    }

    @Test
    @DisplayName("推送抛异常：nack 且 requeue=false（通知已落库，重投只会死循环）")
    void pushFailure_nacksWithoutRequeue() throws IOException {
        doThrow(new RuntimeException("stomp 断了"))
                .when(messagingTemplate).convertAndSendToUser(anyString(), anyString(), any());

        listener.onNotification(payload(56L), channel, 45L);

        verify(channel).basicNack(45L, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("在线与否都照推、照 ack —— 在线判断只影响日志，不能变成投递闸门")
    void onlineCheckIsDiagnosticOnly() throws IOException {
        // 手工注入：`@InjectMocks` 走了构造器注入之后就不再注入剩下的字段了
        SimpUserRegistry registry = mock(SimpUserRegistry.class);
        ReflectionTestUtils.setField(listener, "simpUserRegistry", registry);

        // 在线
        when(registry.getUser("56")).thenReturn(mock(SimpUser.class));
        listener.onNotification(payload(56L), channel, 46L);
        verify(messagingTemplate).convertAndSendToUser(eq("56"), eq("/queue/messages"), any());
        verify(channel).basicAck(46L, false);

        // 不在线（注册表里查不到，默认就返回 null）—— 照样推、照样 ack
        listener.onNotification(payload(57L), channel, 47L);
        verify(messagingTemplate).convertAndSendToUser(eq("57"), eq("/queue/messages"), any());
        verify(channel).basicAck(47L, false);
    }

    @Test
    @DisplayName("@RabbitListener 的队列名必须与 RabbitMQConfig 声明的那个一致")
    void queueNameMatchesDeclaredBean() {
        String declared = new RabbitMQConfig().messageNotificationQueue().getName();
        assertEquals(declared, NotificationMessageListener.QUEUE,
                "两边各写一个字面量，改名时漏掉一边就会监听一个不存在的队列");
    }
}
