package com.academic.config;

import com.academic.common.mq.RabbitMQConstants;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        rabbitTemplate.setMandatory(true);
        return rabbitTemplate;
    }

    /**
     * 监听容器工厂。
     *
     * <p><b>⚠️ 2026-10-02（W3.1）补的两行，都是被实测逼出来的</b>：加上第一个
     * {@code @RabbitListener} 之后，在**没有 broker** 的机器上（本工程演示环境就是，
     * 5672 一直关着）这个容器会**热重启** —— 实测 60 秒内 120 次连接尝试、
     * 每条都带完整堆栈，日志里刷出 {@code Container#0-106} 这种一路涨到三位数的
     * consumer 序号。原因是这里手搓的工厂**没有设恢复间隔**，消费者初始化失败后
     * 立刻重启，形成死循环。
     * <ul>
     *   <li>{@code recoveryInterval(30000)}：broker 不在时 30 秒才重试一次，
     *       从"刷屏"降成"偶尔一行"。broker 起来后照常接管，只是最多晚 30 秒。</li>
     *   <li>{@code missingQueuesFatal(false)}：队列尚未声明出来时不要判为致命错误。</li>
     * </ul>
     * 这里**刻意不做 fail-fast / auto-startup=false**：本工程没有启动脚本、IDE 直接 run，
     * 拒绝启动会让演示当场起不来（同 §7 对 JWT 缺省值的那条口径）。
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(20);
        factory.setConcurrentConsumers(5);
        factory.setMaxConcurrentConsumers(15);
        factory.setRecoveryInterval(30_000L);
        factory.setMissingQueuesFatal(false);
        return factory;
    }

    @Bean
    public FanoutExchange notificationExchange() {
        return new FanoutExchange(RabbitMQConstants.EXCHANGE_NOTIFICATION, true, false);
    }

    @Bean
    public DirectExchange messageExchange() {
        return new DirectExchange(RabbitMQConstants.EXCHANGE_MESSAGE, true, false);
    }

    @Bean
    public Queue messageSendQueue() {
        return QueueBuilder.durable(RabbitMQConstants.QUEUE_MESSAGE_SEND)
                .withArgument("x-dead-letter-exchange", "message.dlx")
                .build();
    }

    @Bean
    public Queue messageNotificationQueue() {
        return QueueBuilder.durable("message.notification.queue").build();
    }

    @Bean
    public Binding messageSendBinding() {
        return BindingBuilder.bind(messageSendQueue())
                .to(messageExchange())
                .with(RabbitMQConstants.ROUTING_KEY_MESSAGE_SEND);
    }

    @Bean
    public Binding messageNotificationBinding() {
        return BindingBuilder.bind(messageNotificationQueue())
                .to(notificationExchange());
    }
}