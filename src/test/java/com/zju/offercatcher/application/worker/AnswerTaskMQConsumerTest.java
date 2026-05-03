package com.zju.offercatcher.application.worker;

import com.zju.offercatcher.application.agent.AnswerSpecialistAgent;
import com.zju.offercatcher.domain.question.aggregates.Question;
import com.zju.offercatcher.domain.question.repositories.QuestionRepository;
import com.zju.offercatcher.domain.shared.enums.QuestionType;
import com.zju.offercatcher.infrastructure.messaging.MQTaskMessage;
import com.zju.offercatcher.infrastructure.messaging.RabbitMQProducer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@DisplayName("AnswerTaskMQConsumer 集成测试")
class AnswerTaskMQConsumerTest {

    private static final String USER_ID = "mq-test-user";

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("offercatcher.rabbitmq.enabled", () -> true);
        registry.add("offercatcher.rabbitmq.queue", () -> "QA");
        registry.add("offercatcher.rabbitmq.dlq", () -> "QD");
        registry.add("spring.rabbitmq.listener.simple.auto-startup", () -> true);
    }

    @BeforeAll
    static void checkRabbitMQ() {
        String host = System.getenv().getOrDefault("RABBITMQ_HOST", "localhost");
        assumeTrue(isRabbitMQAvailable(host),
                "跳过：RabbitMQ 不可用，请确保本地 Docker RabbitMQ 已启动");
    }

    private static boolean isRabbitMQAvailable(String host) {
        try (var s = new java.net.Socket(host, 5672)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @MockitoBean
    AnswerSpecialistAgent answerAgent;

    @Autowired
    QuestionRepository questionRepository;

    @Autowired
    RabbitMQProducer producer;

    @Test
    @DisplayName("消息发送到 QA 队列后被 Consumer 消费，生成答案并写入数据库")
    void consumeAndSaveAnswer() throws Exception {
        when(answerAgent.generateAnswer(any()))
                .thenReturn("这是模拟生成的答案");

        Question question = Question.createPrivate(USER_ID,
                "HashMap 的实现原理？", "阿里巴巴", "Java",
                QuestionType.KNOWLEDGE, List.of("HashMap"));
        Long qId = question.getId();
        assertThat(qId).isNotNull();
        questionRepository.save(question);

        MQTaskMessage task = new MQTaskMessage(qId, question.getQuestionText(),
                question.getCompany(), question.getPosition(), question.getCoreEntities());
        boolean published = producer.publishTask(task);
        assertThat(published).isTrue();

        String answer = awaitAnswer(qId, 20, 500);
        assertThat(answer).isEqualTo("这是模拟生成的答案");

        questionRepository.deleteById(qId, USER_ID);
    }

    @Test
    @DisplayName("已有答案的消息不重复处理（幂等性）")
    void idempotentSkipWhenAnswerExists() throws Exception {
        when(answerAgent.generateAnswer(any()))
                .thenReturn("不应该被调用");

        Question question = Question.createPrivate(USER_ID,
                "JVM 垃圾回收机制？", "阿里巴巴", "Java",
                QuestionType.KNOWLEDGE, List.of("JVM"));
        question.updateAnswer("已存在的答案");
        Long qId = question.getId();
        questionRepository.save(question);

        MQTaskMessage task = new MQTaskMessage(qId, question.getQuestionText(),
                question.getCompany(), question.getPosition(), question.getCoreEntities());
        boolean published = producer.publishTask(task);
        assertThat(published).isTrue();

        Thread.sleep(3000);
        Optional<Question> reloaded = questionRepository.findById(qId);
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getAnswer()).isEqualTo("已存在的答案");

        questionRepository.deleteById(qId, USER_ID);
    }

    @Test
    @DisplayName("不存在的 Question 消息被丢弃，不抛异常")
    void discardWhenQuestionNotFound() {
        MQTaskMessage task = new MQTaskMessage(99999L, "不存在的题目",
                "某公司", "某岗位", List.of());

        boolean published = producer.publishTask(task);
        assertThat(published).isTrue();
    }

    private String awaitAnswer(Long qId, int maxRetries, long intervalMs) throws InterruptedException {
        for (int i = 0; i < maxRetries; i++) {
            Optional<Question> reloaded = questionRepository.findById(qId);
            if (reloaded.isPresent() && reloaded.get().getAnswer() != null
                    && !reloaded.get().getAnswer().isBlank()) {
                return reloaded.get().getAnswer();
            }
            Thread.sleep(intervalMs);
        }
        return null;
    }
}
