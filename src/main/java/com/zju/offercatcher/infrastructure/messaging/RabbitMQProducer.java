package com.zju.offercatcher.infrastructure.messaging;

import com.zju.offercatcher.infrastructure.config.RabbitMQProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * RabbitMQ 消息生产者
 *
 * 对应 Python: app/infrastructure/messaging/producer.py RabbitMQProducer
 */
@Service
@ConditionalOnProperty(name = "offercatcher.rabbitmq.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitMQProducer {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQProducer.class);

    private final RabbitTemplate rabbitTemplate;
    private final String queueName;
    private final String exchangeName;

    public RabbitMQProducer(RabbitTemplate rabbitTemplate, RabbitMQProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.queueName = properties.getQueue();
        this.exchangeName = properties.getQueue() + "_exchange";
    }

    public boolean publishTask(MQTaskMessage task) {
        try {
            rabbitTemplate.convertAndSend(exchangeName, queueName, task, msg -> {
                MessageProperties props = msg.getMessageProperties();
                props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                props.setMessageId(String.valueOf(task.questionId()));
                return msg;
            });
            log.info("Published task: questionId={}, company={}", task.questionId(), task.company());
            return true;
        } catch (Exception e) {
            log.error("Failed to publish task {}: {}", task.questionId(), e.getMessage());
            return false;
        }
    }

    public int publishTasks(java.util.List<MQTaskMessage> tasks) {
        int successCount = 0;
        for (MQTaskMessage task : tasks) {
            if (publishTask(task)) {
                successCount++;
            }
        }
        log.info("Published {}/{} tasks", successCount, tasks.size());
        return successCount;
    }
}
