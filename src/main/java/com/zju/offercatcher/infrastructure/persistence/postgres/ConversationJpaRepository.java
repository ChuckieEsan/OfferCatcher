package com.zju.offercatcher.infrastructure.persistence.postgres;

import com.zju.offercatcher.domain.shared.enums.ConversationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Conversation JPA Repository
 */
@Repository
public interface ConversationJpaRepository extends JpaRepository<ConversationJpaEntity, Long> {

    @Query("SELECT c FROM ConversationJpaEntity c WHERE c.conversationId = :conversationId")
    Optional<ConversationJpaEntity> findByConversationId(@Param("conversationId") Long conversationId);

    @Query("SELECT c FROM ConversationJpaEntity c WHERE c.userId = :userId ORDER BY c.createdAt DESC LIMIT :limit OFFSET :offset")
    List<ConversationJpaEntity> findByUserIdPaginated(@Param("userId") String userId, @Param("limit") int limit, @Param("offset") int offset);

    @Query("SELECT c FROM ConversationJpaEntity c WHERE c.userId = :userId AND c.status = :status ORDER BY c.createdAt DESC LIMIT :limit OFFSET :offset")
    List<ConversationJpaEntity> findByUserIdAndStatusPaginated(@Param("userId") String userId, @Param("status") ConversationStatus status, @Param("limit") int limit, @Param("offset") int offset);

    @Query("SELECT COUNT(c) FROM ConversationJpaEntity c WHERE c.userId = :userId")
    long countByUserId(@Param("userId") String userId);

    @Query("SELECT COUNT(c) FROM ConversationJpaEntity c WHERE c.userId = :userId AND c.status = :status")
    long countByUserIdAndStatus(@Param("userId") String userId, @Param("status") ConversationStatus status);

    @Modifying
    @Query("DELETE FROM ConversationJpaEntity c WHERE c.conversationId = :conversationId AND c.userId = :userId")
    int deleteByConversationIdAndUserId(@Param("conversationId") Long conversationId, @Param("userId") String userId);
}
