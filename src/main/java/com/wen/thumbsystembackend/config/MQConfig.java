package com.wen.thumbsystembackend.config;

import org.springframework.amqp.rabbit.batch.BatchingStrategy;
import org.springframework.amqp.rabbit.batch.SimpleBatchingStrategy;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.BatchingRabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class MQConfig {
    /**
     * rabbitMQ的消息转换器
     * @return
     */
    @Bean
    public MessageConverter messageConverter() {
        Jackson2JsonMessageConverter jjmc = new Jackson2JsonMessageConverter();
        jjmc.setCreateMessageIds(true);
        return jjmc;
    }

    /**
     * 实现了rabbitMQ消息的批量发送
     * @param connectionFactory
     * @return
     */
    @Bean
    public BatchingRabbitTemplate batchingRabbitTemplate(ConnectionFactory connectionFactory) {
        // 1. 达到 1000 条消息时触发发送
        int batchSize = 1000;
        // 2. 达到 100KB 内存占用时触发发送
        int bufferLimit = 102400;
        // 3. 距离上一条消息超过 10 秒时强制触发发送（防止消息积压不发）
        long timeout = 10000;

        BatchingStrategy batchingStrategy = new SimpleBatchingStrategy(batchSize, bufferLimit, timeout);
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.initialize();

        BatchingRabbitTemplate template = new BatchingRabbitTemplate(connectionFactory,batchingStrategy, scheduler);
        template.setMessageConverter(new Jackson2JsonMessageConverter());
        return template;
    }

    /**
     * 消费者（接收端）批量处理工厂
     * 之后在配置消费者时除了bindings = @QueueBinding(..)之外 再加一个containerFactory = "batchQueueTaskListenerContainerFactory"
     * 配置监听器工厂
     * @param configurer
     * @param connectionFactory
     * @return
     */
    @Bean
    public SimpleRabbitListenerContainerFactory batchQueueTaskListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory) {

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);

        // 1. 开启批量消费模式（核心）
        factory.setBatchListener(true);
        // 2. 消费端每次从队列拉取消息的最大数量
        factory.setBatchSize(1000);
        // 3. 消费端等待凑齐批处理消息的最长等待时间 (ms)
        factory.setReceiveTimeout(10000L);
        // 4. 开启批处理消息自动解包功能（配合 BatchingRabbitTemplate 使用）
        factory.setDeBatchingEnabled(true);

        return factory;
    }

}
