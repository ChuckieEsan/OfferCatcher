package com.zju.offercatcher.infrastructure.persistence.postgres;

import com.zju.offercatcher.domain.shared.enums.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * InterviewSession JPA Repository
 */
@Repository
public interface InterviewSessionJpaRepository extends JpaRepository<InterviewSessionJpaEntity, Long> {

    @Query("SELECT s FROM InterviewSessionJpaEntity s WHERE s.sessionId = :sessionId")
    Optional<InterviewSessionJpaEntity> findBySessionId(@Param("sessionId") Long sessionId);

    @Query("SELECT s FROM InterviewSessionJpaEntity s WHERE s.userId = :userId ORDER BY s.createdAt DESC LIMIT :limit OFFSET :offset")
    List<InterviewSessionJpaEntity> findByUserIdPaginated(@Param("userId") String userId, @Param("limit") int limit, @Param("offset") int offset);

    @Query("SELECT s FROM InterviewSessionJpaEntity s WHERE s.userId = :userId AND s.status = :status ORDER BY s.createdAt DESC LIMIT :limit OFFSET :offset")
    List<InterviewSessionJpaEntity> findByUserIdAndStatusPaginated(@Param("userId") String userId, @Param("status") SessionStatus status, @Param("limit") int limit, @Param("offset") int offset);

    @Query("SELECT s FROM InterviewSessionJpaEntity s WHERE s.userId = :userId AND s.company = :company ORDER BY s.createdAt DESC LIMIT :limit OFFSET :offset")
    List<InterviewSessionJpaEntity> findByUserIdAndCompanyPaginated(@Param("userId") String userId, @Param("company") String company, @Param("limit") int limit, @Param("offset") int offset);

    @Query("SELECT COUNT(s) FROM InterviewSessionJpaEntity s WHERE s.userId = :userId")
    long countByUserId(@Param("userId") String userId);

    @Query("SELECT COUNT(s) FROM InterviewSessionJpaEntity s WHERE s.userId = :userId AND s.status = :status")
    long countByUserIdAndStatus(@Param("userId") String userId, @Param("status") SessionStatus status);

    @Modifying
    @Query("DELETE FROM InterviewSessionJpaEntity s WHERE s.sessionId = :sessionId AND s.userId = :userId")
    int deleteBySessionIdAndUserId(@Param("sessionId") Long sessionId, @Param("userId") String userId);
}
