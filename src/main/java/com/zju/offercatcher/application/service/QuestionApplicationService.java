package com.zju.offercatcher.application.service;

import com.zju.offercatcher.application.agent.AnswerSpecialistAgent;
import com.zju.offercatcher.domain.question.aggregates.Question;
import com.zju.offercatcher.domain.question.repositories.QuestionRepository;
import com.zju.offercatcher.domain.shared.enums.MasteryLevel;
import com.zju.offercatcher.domain.shared.enums.QuestionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 题目应用服务
 *
 * 编排题目的 CRUD 用例。
 * 对应 Python: app/application/services/question_service.py
 */
@Service
public class QuestionApplicationService {

    private static final Logger log = LoggerFactory.getLogger(QuestionApplicationService.class);

    private final QuestionRepository questionRepository;
    private final CacheApplicationService cacheService;
    private final AnswerSpecialistAgent answerAgent;

    public QuestionApplicationService(QuestionRepository questionRepository,
                                       CacheApplicationService cacheService,
                                       AnswerSpecialistAgent answerAgent) {
        this.questionRepository = questionRepository;
        this.cacheService = cacheService;
        this.answerAgent = answerAgent;
    }

    @Transactional
    public Question createQuestion(String userId, String questionText, String company,
                                    String position, QuestionType questionType,
                                    List<String> coreEntities) {
        Question question = Question.createPrivate(userId, questionText, company, position,
            questionType, coreEntities);
        questionRepository.save(question);
        cacheService.invalidateQuestion(null);
        log.info("Created question: {}", question.getQuestionHash());
        return question;
    }

    public Optional<Question> getQuestion(Long id) {
        return questionRepository.findById(id);
    }

    @Transactional
    public Optional<Question> updateQuestion(Long id, String answer,
                                              MasteryLevel masteryLevel,
                                              String questionText,
                                              List<String> coreEntities) {
        Question question = questionRepository.findById(id).orElse(null);
        if (question == null) {
            log.warn("Question not found: {}", id);
            return Optional.empty();
        }

        cacheService.invalidateQuestion(id);

        if (answer != null) {
            question.updateAnswer(answer);
        }
        if (masteryLevel != null) {
            question.updateMastery(masteryLevel);
        }
        if (questionText != null || coreEntities != null) {
            question.updateContent(questionText, coreEntities);
        }

        questionRepository.save(question);
        cacheService.invalidateQuestionDelayed(id);
        log.info("Updated question: {}", id);
        return Optional.of(question);
    }

    @Transactional
    public boolean deleteQuestion(Long id, String userId) {
        if (questionRepository.findById(id).isEmpty()) {
            log.warn("Question not found for deletion: {}", id);
            return false;
        }
        questionRepository.deleteById(id, userId);
        cacheService.invalidateQuestion(id);
        log.info("Deleted question: {}", id);
        return true;
    }

    public List<Question> listQuestions(String userId, String company, String position,
                                         QuestionType questionType, MasteryLevel masteryLevel,
                                         String keyword, String clusterId,
                                         int page, int pageSize) {
        List<Question> questions;

        if (keyword != null && !keyword.isBlank()) {
            questions = questionRepository.findByKeyword(userId, keyword, page, pageSize);
        } else {
            questions = questionRepository.findByUserId(userId, page, pageSize);
        }

        if (company != null && !company.isBlank()) {
            questions = questions.stream()
                .filter(q -> company.equals(q.getCompany()))
                .toList();
        }
        if (position != null && !position.isBlank()) {
            questions = questions.stream()
                .filter(q -> position.equals(q.getPosition()))
                .toList();
        }
        if (questionType != null) {
            questions = questions.stream()
                .filter(q -> q.getQuestionType() == questionType)
                .toList();
        }
        if (masteryLevel != null) {
            questions = questions.stream()
                .filter(q -> q.getMasteryLevel() == masteryLevel)
                .toList();
        }
        if (clusterId != null && !clusterId.isBlank()) {
            questions = questions.stream()
                .filter(q -> q.getClusterIds().contains(clusterId))
                .toList();
        }
        return questions;
    }

    @Transactional
    public Optional<Question> regenerateAnswer(Long id) {
        Question question = questionRepository.findById(id).orElse(null);
        if (question == null) {
            log.warn("Question not found for regenerate: {}", id);
            return Optional.empty();
        }

        String answer = answerAgent.generateAnswer(question);
        if (answer != null && !answer.isBlank()) {
            question.updateAnswer(answer);
            questionRepository.save(question);
            cacheService.invalidateQuestion(id);
            log.info("Answer regenerated for: {}", id);
        }
        return Optional.of(question);
    }

    @Transactional
    public Optional<Question> publishQuestion(Long id, String userId) {
        Question question = questionRepository.findById(id).orElse(null);
        if (question == null) {
            log.warn("Question not found for publish: {}", id);
            return Optional.empty();
        }
        if (!question.isOwnedBy(userId)) {
            log.warn("User {} not authorized to publish {}", userId, id);
            return Optional.empty();
        }

        questionRepository.publishToPublic(id, userId);
        cacheService.invalidateQuestion(id);
        log.info("Published question to public: {}", id);
        return questionRepository.findById(id);
    }

    public Map<Long, String> getBatchAnswers(List<Long> ids) {
        Map<Long, String> answers = new HashMap<>();
        for (Long id : ids) {
            questionRepository.findById(id)
                .ifPresent(q -> answers.put(id, q.getAnswer()));
        }
        return answers;
    }
}
