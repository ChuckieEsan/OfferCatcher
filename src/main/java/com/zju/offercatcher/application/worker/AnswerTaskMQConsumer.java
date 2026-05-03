package com.zju.offercatcher.application.worker;

import com.rabbitmq.client.Channel;
import com.zju.offercatcher.application.agent.AnswerSpecialistAgent;
import com.zju.offercatcher.domain.question.aggregates.Question;
import com.zju.offercatcher.domain.question.repositories.QuestionRepository;
import com.zju.offercatcher.infrastructure.messaging.MQMessageHelper;
import com.zju.offercatcher.infrastructure.messaging.MQTaskMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

/**
 * 答案生成 MQ Consumer
 * <p>
 * 消费 RabbitMQ 消息，调用 AnswerSpecialistAgent 异步生成标准答案。
 * 对应 Python: app/application/workers/answer_worker.py process_answer_task()
 */
@Component
@ConditionalOnProperty(name = "offercatcher.rabbitmq.enabled", havingValue = "true", matchIfMissing = true)
public class AnswerTaskMQConsumer {

    private static final Logger log = LoggerFactory.getLogger(AnswerTaskMQConsumer.class);

    private final QuestionRepository questionRepository;
    private final AnswerSpecialistAgent answerAgent;
    private final MQMessageHelper messageHelper;

    public AnswerTaskMQConsumer(QuestionRepository questionRepository,
                                AnswerSpecialistAgent answerAgent,
                                MQMessageHelper messageHelper) {
        this.questionRepository = questionRepository;
        this.answerAgent = answerAgent;
        this.messageHelper = messageHelper;
    }

    @RabbitListener(queues = "${offercatcher.rabbitmq.queue}")
    public void handle(MQTaskMessage task, Channel channel,
                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                       @Header(value = "x-retry-count", required = false, defaultValue = "0") int retryCount)
            throws IOException {

        Long questionId = task.questionId();
        try {
            log.info("Received answer task: qId={}, retry={}", questionId, retryCount);

            // Idempotency check
            Optional<Question> existing = questionRepository.findById(questionId);
            if (existing.isEmpty()) {
                log.warn("Question not found in PostgreSQL: {}, discarding", questionId);
                channel.basicAck(deliveryTag, false);
                return;
            }
            if (existing.get().getAnswer() != null && !existing.get().getAnswer().isBlank()) {
                log.info("Answer already exists for: {}, skipping", questionId);
                channel.basicAck(deliveryTag, false);
                return;
            }

            Question question = existing.get();
            String answer = answerAgent.generateAnswer(question);
            if (answer != null && !answer.isBlank()) {
                question.updateAnswer(answer);
                questionRepository.save(question);
                log.info("Answer generated and saved: {}", questionId);
            }
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("Failed to process answer task {}: {}", questionId, e.getMessage());
            messageHelper.republishToBack(channel, deliveryTag, task, retryCount);
        }
    }
}
