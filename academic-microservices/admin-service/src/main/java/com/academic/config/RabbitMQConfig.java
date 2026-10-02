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

/**
 * RabbitMQ 配置类
 * 定义队列、交换机、绑定关系
 */
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
        factory.setMaxConcurrentConsumers(10);
        return factory;
    }

    // ============ 交换机配置 ============
    
    @Bean
    public DirectExchange adminExchange() {
        return new DirectExchange(RabbitMQConstants.EXCHANGE_ADMIN, true, false);
    }

    // 2026-10-02（W3.1）：这里原来还声明了 notification.exchange + notification.queue
    // 以及两者的绑定，但 admin-service **既不往这个交换机发、也没有消费者**。
    // ⚠️ 它和别的死队列不一样：`notification.exchange` 是 **fanout**，绑上去就等于
    // 订阅了「所有通知」——broker 一起来，每个服务发出的每条通知都会往这个没人读的
    // 队列里堆一份，永久堆积。所以删的是这一条，而不是"顺手清理所有没人用的队列"
    // （其余 direct 队列没有生产者，收不到东西，是惰性的，留着不涨）。
    // 通知链的两端分别在：生产侧 warning-service（`WarningNotifier`）、
    // 消费侧 message-service（`NotificationMessageListener`）。

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(RabbitMQConstants.DLX_EXCHANGE, true, false);
    }

    // ============ 队列配置 ============
    
    @Bean
    public Queue userImportQueue() {
        return QueueBuilder.durable(RabbitMQConstants.QUEUE_USER_IMPORT)
                .withArgument("x-dead-letter-exchange", RabbitMQConstants.DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", RabbitMQConstants.DLQ_USER_IMPORT)
                .build();
    }

    @Bean
    public Queue scoreImportQueue() {
        return QueueBuilder.durable(RabbitMQConstants.QUEUE_SCORE_IMPORT)
                .withArgument("x-dead-letter-exchange", RabbitMQConstants.DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", RabbitMQConstants.DLQ_SCORE_IMPORT)
                .build();
    }

    @Bean
    public Queue exportQueue() {
        return QueueBuilder.durable(RabbitMQConstants.QUEUE_EXPORT)
                .withArgument("x-dead-letter-exchange", RabbitMQConstants.DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", RabbitMQConstants.DLQ_EXPORT)
                .build();
    }

    @Bean
    public Queue userImportDlq() {
        return QueueBuilder.durable(RabbitMQConstants.DLQ_USER_IMPORT).build();
    }

    @Bean
    public Queue scoreImportDlq() {
        return QueueBuilder.durable(RabbitMQConstants.DLQ_SCORE_IMPORT).build();
    }

    @Bean
    public Queue exportDlq() {
        return QueueBuilder.durable(RabbitMQConstants.DLQ_EXPORT).build();
    }

    // ============ 绑定配置 ============
    
    @Bean
    public Binding userImportBinding() {
        return BindingBuilder.bind(userImportQueue())
                .to(adminExchange())
                .with(RabbitMQConstants.ROUTING_KEY_USER_IMPORT);
    }

    @Bean
    public Binding scoreImportBinding() {
        return BindingBuilder.bind(scoreImportQueue())
                .to(adminExchange())
                .with(RabbitMQConstants.ROUTING_KEY_SCORE_IMPORT);
    }

    @Bean
    public Binding exportBinding() {
        return BindingBuilder.bind(exportQueue())
                .to(adminExchange())
                .with(RabbitMQConstants.ROUTING_KEY_EXPORT);
    }

    @Bean
    public Binding userImportDlqBinding() {
        return BindingBuilder.bind(userImportDlq())
                .to(dlxExchange())
                .with(RabbitMQConstants.DLQ_USER_IMPORT);
    }

    @Bean
    public Binding scoreImportDlqBinding() {
        return BindingBuilder.bind(scoreImportDlq())
                .to(dlxExchange())
                .with(RabbitMQConstants.DLQ_SCORE_IMPORT);
    }

    @Bean
    public Binding exportDlqBinding() {
        return BindingBuilder.bind(exportDlq())
                .to(dlxExchange())
                .with(RabbitMQConstants.DLQ_EXPORT);
    }
}