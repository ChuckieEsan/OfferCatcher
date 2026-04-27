package com.zju.offercatcher.infrastructure.persistence.postgres;

import com.zju.offercatcher.domain.shared.enums.MemoryLayer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * SessionSummary JPA Repository
 */
@Repository
public interface SessionSummaryJpaRepository extends JpaRepository<SessionSummaryJpaEntity, Long> {

    @Query("SELECT s FROM SessionSummaryJpaEntity s WHERE s.userId = :userId ORDER BY s.importanceScore DESC, s.createdAt DESC LIMIT :limit OFFSET :offset")
    List<SessionSummaryJpaEntity> findByUserIdPaginated(@Param("userId") String userId, @Param("limit") int limit, @Param("offset") int offset);

    @Query("SELECT s FROM SessionSummaryJpaEntity s WHERE s.userId = :userId AND s.memoryLayer = :layer ORDER BY s.importanceScore DESC LIMIT :limit OFFSET :offset")
    List<SessionSummaryJpaEntity> findByUserIdAndMemoryLayerPaginated(@Param("userId") String userId, @Param("layer") MemoryLayer layer, @Param("limit") int limit, @Param("offset") int offset);

    @Query("SELECT s FROM SessionSummaryJpaEntity s WHERE s.conversationId = :conversationId ORDER BY s.createdAt ASC")
    List<SessionSummaryJpaEntity> findByConversationId(@Param("conversationId") Long conversationId);

    @Query("SELECT s FROM SessionSummaryJpaEntity s WHERE s.userId = :userId ORDER BY s.importanceScore DESC LIMIT :k")
    List<SessionSummaryJpaEntity> findTopKByImportance(@Param("userId") String userId, @Param("k") int k);

    @Query("SELECT s FROM SessionSummaryJpaEntity s WHERE s.userId = :userId AND s.markedForDeletion = true")
    List<SessionSummaryJpaEntity> findMarkedForDeletion(@Param("userId") String userId);

    @Query("SELECT COUNT(s) FROM SessionSummaryJpaEntity s WHERE s.userId = :userId")
    long countByUserId(@Param("userId") String userId);

    @Query("SELECT COUNT(s) FROM SessionSummaryJpaEntity s WHERE s.userId = :userId AND s.memoryLayer = :layer")
    long countByUserIdAndMemoryLayer(@Param("userId") String userId, @Param("layer") MemoryLayer layer);

    @Modifying
    @Query("DELETE FROM SessionSummaryJpaEntity s WHERE s.id = :id AND s.userId = :userId")
    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") String userId);

    @Modifying
    @Query("DELETE FROM SessionSummaryJpaEntity s WHERE s.userId = :userId AND s.markedForDeletion = true")
    int deleteMarkedForDeletion(@Param("userId") String userId);
}
