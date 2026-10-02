package com.academic.mq;

import com.academic.common.mq.RabbitMQConstants;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 消费 {@code notification.exchange} 上的通知事件，做 WebSocket 实时推送（W3.1，2026-10-02）。
 *
 * <h3>这个类为什么现在才出现</h3>
 * 文档里写的那条链是「业务事件 → RabbitMQ 异步 → message-service → WebSocket 实时推送」。
 * 在 2026-10-02 之前，这条链**两头都是断的**：
 * <ul>
 *   <li>生产侧：全仓 **0 次** {@code convertAndSend} —— 交换机、队列、绑定在
 *       {@code RabbitMQConfig} 里都声明好了，但从来没有人往里发过东西；</li>
 *   <li>消费侧：全仓 **0 个** {@code @RabbitListener} —— {@code message.notification.queue}
 *       早就绑在 {@code notification.exchange} 上，却没有任何消费者。</li>
 * </ul>
 * 现在生产侧由 {@code warning-service} 的 {@code WarningNotifier} 负责（它同时**直写
 * notifications 表**，保证 broker 不在时功能也完整），消费侧就是本类。
 *
 * <h3>它是"加速器"，不是"必经之路"</h3>
 * 进到这里的每条通知，业务侧都已经写进 {@code notifications} 表了（学生/辅导员打开
 * 通知中心一定能看到）。本类只负责**让已经开着页面的浏览器/小程序立刻收到**，
 * 所以：
 * <ul>
 *   <li>处理失败 → {@code basicNack(requeue=false)}，**不重投**。重投也还是推不出去
 *       （客户端没连着就是没连着），只会把队列堵死；</li>
 *   <li>broker 没起 → 监听容器自己重连重试，只打日志，**不影响 message-service 启动**
 *       （实测见手册 §6.30；当前 5672 就是关的）。</li>
 * </ul>
 *
 * <h3>⚠️ 手工 ack</h3>
 * {@code RabbitMQConfig.rabbitListenerContainerFactory} 设的是
 * {@code AcknowledgeMode.MANUAL} —— 也就是说**方法正常返回并不会自动 ack**。
 * 忘了 ack 的症状不是报错，而是这条消息一直挂在 unacked 里、连接一断就被重新投递，
 * 于是在日志里反复出现同一条通知。所以下面每个出口都要显式 ack 或 nack。
 *
 * <h3>推送目标</h3>
 * {@code /user/{userId}/queue/messages} —— 与
 * {@code MessageServiceImpl.sendMessage} 用的是同一个目的地，客户端只需订阅一次
 * （PC 端 {@code AdminMessages.vue} 已经在订，学生/教师/辅导员的通知中心同理）。
 */
@Slf4j
@Component
public class NotificationMessageListener {

    /** 与 {@code RabbitMQConfig.messageNotificationQueue()} 里声明的名字必须一致。 */
    public static final String QUEUE = "message.notification.queue";

    private final SimpMessagingTemplate messagingTemplate;

    public NotificationMessageListener(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @RabbitListener(queues = QUEUE)
    public void onNotification(Map<String, Object> payload,
                               Channel channel,
                               @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            Long userId = toLong(payload == null ? null : payload.get("userId"));
            if (userId == null) {
                // 收件人缺失是**消息本身**的问题，重投多少次都一样 ⇒ 直接丢弃并 ack
                log.warn("收到没有收件人的通知事件，已丢弃: {}", payload);
                channel.basicAck(deliveryTag, false);
                return;
            }

            Map<String, Object> push = new LinkedHashMap<>();
            push.put("event", payload.getOrDefault("event", "notification"));
            push.put("title", payload.get("title"));
            push.put("content", payload.get("content"));
            push.put("warningId", payload.get("warningId"));
            push.put("timestamp", System.currentTimeMillis());

            messagingTemplate.convertAndSendToUser(String.valueOf(userId), "/queue/messages", push);
            log.info("MQ 通知已实时推送给 userId={}（warningId={}）", userId, payload.get("warningId"));
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            // 不 requeue：通知在业务侧已经落库，重投只会无限循环刷日志。
            log.warn("通知事件推送失败（通知已落库，不影响功能）: {}", e.getMessage());
            channel.basicNack(deliveryTag, false, false);
        }
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
