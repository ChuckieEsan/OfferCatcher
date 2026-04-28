package com.zju.offercatcher.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "offercatcher.rabbitmq")
public class RabbitMQProperties {

    private String host = "localhost";
    private int port = 5672;
    private String user = "guest";
    private String password = "guest";
    private String queue = "answer_tasks";
    private String dlq = "answer_tasks_dlq";
    private int maxRetries = 3;
    private int prefetchCount = 5;

}
