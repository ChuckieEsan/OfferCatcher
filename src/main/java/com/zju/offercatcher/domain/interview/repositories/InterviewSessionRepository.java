package com.zju.offercatcher.domain.interview.repositories;

import com.zju.offercatcher.domain.interview.aggregates.InterviewSession;
import com.zju.offercatcher.domain.shared.enums.SessionStatus;

import java.util.List;
import java.util.Optional;

/**
 * 面试会话仓储接口
 */
public interface InterviewSessionRepository {

    List<InterviewSession> findByUserId(String userId, int page, int size);

    Optional<InterviewSession> findById(Long sessionId);

    List<InterviewSession> findByUserIdAndStatus(String userId, SessionStatus status, int page, int size);

    List<InterviewSession> findByUserIdAndCompany(String userId, String company, int page, int size);

    void save(InterviewSession session);

    /**
     * @throws com.zju.offercatcher.domain.shared.exception.UnauthorizedOperationException 如果用户不是所有者
     */
    void deleteById(Long sessionId, String userId);

    long countByUserId(String userId);

    long countByUserIdAndStatus(String userId, SessionStatus status);
}
