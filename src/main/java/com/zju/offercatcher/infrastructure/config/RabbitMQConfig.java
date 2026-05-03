package com.zju.offercatcher.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "offercatcher.rabbitmq.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitMQConfig {

    private final RabbitMQProperties properties;

    public RabbitMQConfig(RabbitMQProperties properties) {
        this.properties = properties;
    }

    @Bean
    public Queue answerTaskQueue() {
        return QueueBuilder.durable(properties.getQueue()).build();
    }

    @Bean
    public Queue answerTaskDLQ() {
        return QueueBuilder.durable(properties.getDlq()).build();
    }

    @Bean
    public DirectExchange answerTaskExchange() {
        return new DirectExchange(properties.getQueue() + "_exchange");
    }

    @Bean
    public Binding answerTaskBinding(Queue answerTaskQueue, DirectExchange answerTaskExchange) {
        return BindingBuilder.bind(answerTaskQueue).to(answerTaskExchange).with(properties.getQueue());
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
