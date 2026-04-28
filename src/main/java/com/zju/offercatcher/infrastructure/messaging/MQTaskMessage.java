package com.zju.offercatcher.infrastructure.messaging;

import java.io.Serializable;
import java.util.List;

/**
 * RabbitMQ 任务消息
 *
 * 对应 Python: app/infrastructure/messaging/messages.py MQTaskMessage
 */
public record MQTaskMessage(
    Long questionId,
    String questionText,
    String company,
    String position,
    List<String> coreEntities
) implements Serializable {
}
