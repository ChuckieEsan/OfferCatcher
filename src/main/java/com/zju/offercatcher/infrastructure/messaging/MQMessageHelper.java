package com.zju.offercatcher.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.zju.offercatcher.infrastructure.config.RabbitMQProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * RabbitMQ 消息处理辅助类
 * <p>
 * 提供消息重试、降级和死信队列处理。
 * 对应 Python: app/infrastructure/messaging/message_helper.py MQMessageHelper
 */
@Component
@ConditionalOnProperty(name = "offercatcher.rabbitmq.enabled", havingValue = "true", matchIfMissing = true)
public class MQMessageHelper {

    private static final Logger log = LoggerFactory.getLogger(MQMessageHelper.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String queueName;
    private final int maxRetries;

    public MQMessageHelper(RabbitMQProperties properties) {
        this.queueName = properties.getQueue();
        this.maxRetries = properties.getMaxRetries();
    }

    /**
     * 将失败消息重新发布到队尾（带 x-retry-count 计数）
     */
    public boolean republishToBack(Channel channel, long deliveryTag, MQTaskMessage task,
                                   int retryCount) throws IOException {
        int newRetryCount = retryCount + 1;

        if (newRetryCount >= maxRetries) {
            log.warn("Max retries ({}) exceeded for {}, discarding", maxRetries, task.questionId());
            channel.basicAck(deliveryTag, false);
            return false;
        }

        byte[] body = objectMapper.writeValueAsBytes(task);

        var props = new com.rabbitmq.client.AMQP.BasicProperties.Builder()
                .deliveryMode(2) // persistent
                .headers(java.util.Map.of("x-retry-count", newRetryCount))
                .build();

        channel.basicPublish("", queueName, props, body);
        channel.basicAck(deliveryTag, false);
        log.info("Message republished to back: qId={}, retry={}", task.questionId(), newRetryCount);
        return true;
    }

    public int getRetryCount(com.rabbitmq.client.AMQP.BasicProperties properties) {
        if (properties == null || properties.getHeaders() == null) return 0;
        Object raw = properties.getHeaders().get("x-retry-count");
        if (raw instanceof Number n) return n.intValue();
        return 0;
    }
}
