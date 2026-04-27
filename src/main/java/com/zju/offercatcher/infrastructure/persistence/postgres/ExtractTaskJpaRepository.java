package com.zju.offercatcher.infrastructure.persistence.postgres;

import com.zju.offercatcher.domain.question.aggregates.ExtractTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExtractTaskJpaRepository extends JpaRepository<ExtractTaskJpaEntity, Long> {

    @Query("SELECT t FROM ExtractTaskJpaEntity t WHERE t.userId = :userId ORDER BY t.updatedAt DESC")
    List<ExtractTaskJpaEntity> findByUserId(@Param("userId") String userId);

    @Query("SELECT t FROM ExtractTaskJpaEntity t WHERE t.userId = :userId AND t.status = :status ORDER BY t.updatedAt DESC")
    List<ExtractTaskJpaEntity> findByUserIdAndStatus(@Param("userId") String userId,
                                                     @Param("status") ExtractTaskStatus status);

    @Query("SELECT t FROM ExtractTaskJpaEntity t WHERE t.taskId = :taskId AND t.userId = :userId")
    Optional<ExtractTaskJpaEntity> findByIdAndUserId(@Param("taskId") Long taskId,
                                                     @Param("userId") String userId);

    @Query("SELECT COUNT(t) FROM ExtractTaskJpaEntity t WHERE t.userId = :userId")
    int countByUserId(@Param("userId") String userId);

    @Query("SELECT COUNT(t) FROM ExtractTaskJpaEntity t WHERE t.userId = :userId AND t.status = :status")
    int countByUserIdAndStatus(@Param("userId") String userId,
                               @Param("status") ExtractTaskStatus status);

    @Query("SELECT t FROM ExtractTaskJpaEntity t WHERE t.status = 'PENDING' ORDER BY t.createdAt ASC LIMIT :limit")
    List<ExtractTaskJpaEntity> findPendingTasks(@Param("limit") int limit);

    @Modifying
    @Query("DELETE FROM ExtractTaskJpaEntity t WHERE t.taskId = :taskId AND t.userId = :userId")
    int deleteByIdAndUserId(@Param("taskId") Long taskId, @Param("userId") String userId);
}
