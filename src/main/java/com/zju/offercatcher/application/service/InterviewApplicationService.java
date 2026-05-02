package com.zju.offercatcher.application.service;

import com.zju.offercatcher.domain.interview.aggregates.InterviewSession;
import com.zju.offercatcher.domain.interview.entities.InterviewQuestion;
import com.zju.offercatcher.domain.interview.repositories.InterviewSessionRepository;
import com.zju.offercatcher.domain.shared.enums.DifficultyLevel;
import com.zju.offercatcher.domain.shared.enums.SessionStatus;
import com.zju.offercatcher.domain.shared.exception.InterviewSessionNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 面试应用服务
 *
 * 编排面试会话的创建、进行、结束等用例。
 * 对应 Python: app/application/services/interview_service.py
 */
@Service
public class InterviewApplicationService {

    private static final Logger log = LoggerFactory.getLogger(InterviewApplicationService.class);

    private final InterviewSessionRepository sessionRepository;

    public InterviewApplicationService(InterviewSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Transactional
    public InterviewSession createSession(String userId, String company, String position,
                                           DifficultyLevel difficulty, int totalQuestions) {
        return createSession(userId, company, position, difficulty, totalQuestions, null);
    }

    @Transactional
    public InterviewSession createSession(String userId, String company, String position,
                                           DifficultyLevel difficulty, int totalQuestions,
                                           String jdContext) {
        InterviewSession session = InterviewSession.create(
            userId, company, position, difficulty, totalQuestions, jdContext);
        sessionRepository.save(session);
        log.info("Created interview session: {}, user={}, company={}, position={}, hasJd={}",
            session.getSessionId(), userId, company, position, jdContext != null);
        return session;
    }

    public Optional<InterviewSession> getSession(Long sessionId, String userId) {
        return sessionRepository.findById(sessionId)
            .filter(s -> s.isOwnedBy(userId));
    }

    public List<InterviewSession> listSessions(String userId, int limit, SessionStatus status) {
        if (status != null) {
            return sessionRepository.findByUserIdAndStatus(userId, status, 1, limit);
        }
        return sessionRepository.findByUserId(userId, 1, limit);
    }

    @Transactional
    public InterviewSession answerQuestion(Long sessionId, String userId, String answer,
                                            int score, String feedback) {
        InterviewSession session = getSessionOrThrow(sessionId, userId);
        session.answerCurrentQuestion(answer, score, feedback);
        sessionRepository.save(session);
        log.info("Answered question in session: {}, score={}", sessionId, score);
        return session;
    }

    @Transactional
    public InterviewSession skipQuestion(Long sessionId, String userId) {
        InterviewSession session = getSessionOrThrow(sessionId, userId);
        session.skipCurrentQuestion();
        sessionRepository.save(session);
        log.info("Skipped question in session: {}", sessionId);
        return session;
    }

    @Transactional
    public InterviewSession pauseSession(Long sessionId, String userId) {
        InterviewSession session = getSessionOrThrow(sessionId, userId);
        session.pause();
        sessionRepository.save(session);
        log.info("Paused session: {}", sessionId);
        return session;
    }

    @Transactional
    public InterviewSession resumeSession(Long sessionId, String userId) {
        InterviewSession session = getSessionOrThrow(sessionId, userId);
        session.resume();
        sessionRepository.save(session);
        log.info("Resumed session: {}", sessionId);
        return session;
    }

    @Transactional
    public InterviewSession completeSession(Long sessionId, String userId) {
        InterviewSession session = getSessionOrThrow(sessionId, userId);
        session.complete();
        sessionRepository.save(session);
        log.info("Completed session: {}", sessionId);
        return session;
    }

    @Transactional
    public boolean deleteSession(Long sessionId, String userId) {
        if (sessionRepository.findById(sessionId).isEmpty()) {
            log.warn("Session not found for deletion: {}", sessionId);
            return false;
        }
        sessionRepository.deleteById(sessionId, userId);
        log.info("Deleted session: {}", sessionId);
        return true;
    }

    private InterviewSession getSessionOrThrow(Long sessionId, String userId) {
        return sessionRepository.findById(sessionId)
            .filter(s -> s.isOwnedBy(userId))
            .orElseThrow(() -> new InterviewSessionNotFoundException(sessionId));
    }
}
