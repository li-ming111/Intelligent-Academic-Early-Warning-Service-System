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

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(10);
        factory.setConcurrentConsumers(3);
        factory.setMaxConcurrentConsumers(8);
        return factory;
    }

    @Bean
    public FanoutExchange notificationExchange() {
        return new FanoutExchange(RabbitMQConstants.EXCHANGE_NOTIFICATION, true, false);
    }

    @Bean
    public DirectExchange warningExchange() {
        return new DirectExchange(RabbitMQConstants.EXCHANGE_WARNING, true, false);
    }

    @Bean
    public Queue warningAnalysisQueue() {
        return QueueBuilder.durable(RabbitMQConstants.QUEUE_WARNING_ANALYSIS)
                .withArgument("x-dead-letter-exchange", "warning.dlx")
                .build();
    }

    @Bean
    public Binding warningAnalysisBinding() {
        return BindingBuilder.bind(warningAnalysisQueue())
                .to(warningExchange())
                .with(RabbitMQConstants.ROUTING_KEY_WARNING_ANALYSIS);
    }

    // 2026-10-02（W3.1）：删掉 `warning.notification.queue` 与它的绑定。
    // 本服务是通知的**生产侧**（{@code WarningNotifier} 往 notification.exchange 发），
    // 消费侧在 message-service（{@code NotificationMessageListener} 监听
    // message.notification.queue）。原来这里还绑了一个自己的队列却没有任何消费者，
    // 而 notification.exchange 是 **fanout** —— 等于给自己订阅了一份永远没人读的通知，
    // broker 一起来就无限堆积。
    // ⚠️ notificationExchange() 这个 bean **必须留**：生产侧要先声明交换机，
    // 否则本服务单独起（message-service 没起）时 RabbitAdmin 没有交换机可声明。
}