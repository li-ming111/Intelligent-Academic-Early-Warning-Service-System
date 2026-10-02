package com.academic.common.mq;

/**
 * RabbitMQ 常量定义
 * 所有微服务共享的队列、交换机、路由键常量
 */
public final class RabbitMQConstants {

    // ============ 交换机常量 ============
    public static final String EXCHANGE_ADMIN = "admin.exchange";
    public static final String EXCHANGE_NOTIFICATION = "notification.exchange";
    public static final String EXCHANGE_WARNING = "warning.exchange";
    public static final String EXCHANGE_MESSAGE = "message.exchange";
    public static final String EXCHANGE_USER = "user.exchange";

    // ============ 队列常量 ============
    public static final String QUEUE_USER_IMPORT = "admin.user.import.queue";
    public static final String QUEUE_SCORE_IMPORT = "admin.score.import.queue";
    public static final String QUEUE_EXPORT = "admin.export.queue";
    // 2026-10-02（W3.1）：原来的 QUEUE_NOTIFICATION = "notification.queue" 已删。
    // 那条队列只有 admin-service 声明过、绑在 notification.exchange（fanout）上，
    // 却既没有生产者也没有消费者 ⇒ 只会替所有通知留一份永远没人读的副本。
    // 通知链真正的队列是 message-service 的 message.notification.queue。
    public static final String QUEUE_WARNING_ANALYSIS = "warning.analysis.queue";
    public static final String QUEUE_MESSAGE_SEND = "message.send.queue";
    public static final String QUEUE_USER_SYNC = "user.sync.queue";

    // ============ 路由键常量 ============
    public static final String ROUTING_KEY_USER_IMPORT = "admin.user.import";
    public static final String ROUTING_KEY_SCORE_IMPORT = "admin.score.import";
    public static final String ROUTING_KEY_EXPORT = "admin.export";
    public static final String ROUTING_KEY_NOTIFICATION = "notification";
    public static final String ROUTING_KEY_WARNING_ANALYSIS = "warning.analysis";
    public static final String ROUTING_KEY_MESSAGE_SEND = "message.send";
    public static final String ROUTING_KEY_USER_SYNC = "user.sync";

    // ============ 死信队列常量 ============
    public static final String DLX_EXCHANGE = "dlx.exchange";
    public static final String DLQ_USER_IMPORT = "admin.user.import.dlq";
    public static final String DLQ_SCORE_IMPORT = "admin.score.import.dlq";
    public static final String DLQ_EXPORT = "admin.export.dlq";

    private RabbitMQConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}