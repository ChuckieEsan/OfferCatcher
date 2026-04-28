package com.zju.offercatcher.application.worker;

import com.zju.offercatcher.application.agent.AnswerSpecialistAgent;
import com.zju.offercatcher.domain.question.aggregates.Question;
import com.zju.offercatcher.domain.question.repositories.QuestionRepository;
import com.zju.offercatcher.infrastructure.persistence.postgres.QuestionJpaEntity;
import com.zju.offercatcher.infrastructure.persistence.postgres.QuestionJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 答案生成 Worker。
 *
 * 轮询 PostgreSQL 查找尚未生成答案的题目，调用 AnswerSpecialistAgent 生成标准答案。
 * 对应 Python: app/application/workers/answer_worker.py
 */
@Component
@ConditionalOnProperty(name = "offercatcher.worker.answer.polling", havingValue = "true")
public class AnswerGenerationWorker {

    private static final Logger log = LoggerFactory.getLogger(AnswerGenerationWorker.class);
    private static final int BATCH_SIZE = 5;
    private static final long POLL_DELAY_MS = 10_000;

    private final QuestionJpaRepository questionJpaRepository;
    private final QuestionRepository questionRepository;
    private final AnswerSpecialistAgent answerAgent;

    public AnswerGenerationWorker(QuestionJpaRepository questionJpaRepository,
                                  QuestionRepository questionRepository,
                                  AnswerSpecialistAgent answerAgent) {
        this.questionJpaRepository = questionJpaRepository;
        this.questionRepository = questionRepository;
        this.answerAgent = answerAgent;
    }

    @Scheduled(fixedDelay = POLL_DELAY_MS)
    public void processUnansweredQuestions() {
        List<QuestionJpaEntity> unanswered = questionJpaRepository.findUnansweredQuestions(BATCH_SIZE);
        if (unanswered.isEmpty()) {
            return;
        }

        log.info("Answer generation worker found {} unanswered questions", unanswered.size());

        for (QuestionJpaEntity entity : unanswered) {
            try {
                Question question = entity.toDomain();
                Optional<Question> existing = questionRepository.findById(question.getId());
                if (existing.isPresent() && existing.get().getAnswer() != null
                    && !existing.get().getAnswer().isBlank()) {
                    continue; // 幂等性：答案已生成
                }

                String answer = answerAgent.generateAnswer(question);
                if (answer != null && !answer.isBlank()) {
                    question.updateAnswer(answer);
                    questionRepository.save(question);
                    log.info("Answer generated for question: {}", question.getQuestionHash());
                }
            } catch (Exception e) {
                log.error("Answer generation failed for question {}: {}", entity.getQuestionHash(), e.getMessage());
            }
        }
    }
}
