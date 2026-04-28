package com.zju.offercatcher.infrastructure.persistence.postgres;

import com.zju.offercatcher.domain.interview.aggregates.InterviewSession;
import com.zju.offercatcher.domain.interview.repositories.InterviewSessionRepository;
import com.zju.offercatcher.domain.shared.enums.SessionStatus;
import com.zju.offercatcher.domain.shared.exception.InterviewSessionNotFoundException;
import com.zju.offercatcher.domain.shared.exception.UnauthorizedOperationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * InterviewSession Repository 实现
 */
@Repository
public class InterviewSessionRepositoryImpl implements InterviewSessionRepository {

    private final InterviewSessionJpaRepository jpaRepository;

    public InterviewSessionRepositoryImpl(InterviewSessionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<InterviewSession> findByUserId(String userId, int page, int size) {
        int offset = (page - 1) * size;
        return jpaRepository.findByUserIdPaginated(userId, size, offset)
            .stream()
            .map(InterviewSessionJpaEntity::toDomain)
            .toList();
    }

    @Override
    public Optional<InterviewSession> findById(Long sessionId) {
        return jpaRepository.findBySessionId(sessionId)
            .map(InterviewSessionJpaEntity::toDomain);
    }

    @Override
    public List<InterviewSession> findByUserIdAndStatus(String userId, SessionStatus status, int page, int size) {
        int offset = (page - 1) * size;
        return jpaRepository.findByUserIdAndStatusPaginated(userId, status, size, offset)
            .stream()
            .map(InterviewSessionJpaEntity::toDomain)
            .toList();
    }

    @Override
    public List<InterviewSession> findByUserIdAndCompany(String userId, String company, int page, int size) {
        int offset = (page - 1) * size;
        return jpaRepository.findByUserIdAndCompanyPaginated(userId, company, size, offset)
            .stream()
            .map(InterviewSessionJpaEntity::toDomain)
            .toList();
    }

    @Override
    @Transactional
    public void save(InterviewSession session) {
        InterviewSessionJpaEntity entity = InterviewSessionJpaEntity.fromDomain(session);
        jpaRepository.save(entity);
    }

    @Override
    @Transactional
    public void deleteById(Long sessionId, String userId) {
        Optional<InterviewSessionJpaEntity> entity = jpaRepository.findBySessionId(sessionId);
        if (entity.isEmpty()) {
            throw new InterviewSessionNotFoundException(sessionId);
        }
        if (!entity.get().getUserId().equals(userId)) {
            throw new UnauthorizedOperationException(userId, sessionId.toString(), "delete interview session");
        }
        jpaRepository.delete(entity.get());
    }

    @Override
    public long countByUserId(String userId) {
        return jpaRepository.countByUserId(userId);
    }

    @Override
    public long countByUserIdAndStatus(String userId, SessionStatus status) {
        return jpaRepository.countByUserIdAndStatus(userId, status);
    }
}
