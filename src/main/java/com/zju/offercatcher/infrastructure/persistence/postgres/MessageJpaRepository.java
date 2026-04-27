package com.zju.offercatcher.infrastructure.persistence.postgres;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Message JPA Repository
 */
@Repository
public interface MessageJpaRepository extends JpaRepository<MessageJpaEntity, Long> {

    @Query("SELECT m FROM MessageJpaEntity m WHERE m.conversationId = :conversationId ORDER BY m.createdAt ASC")
    List<MessageJpaEntity> findByConversationId(@Param("conversationId") Long conversationId);

    @Modifying
    @Query("DELETE FROM MessageJpaEntity m WHERE m.conversationId = :conversationId")
    int deleteByConversationId(@Param("conversationId") Long conversationId);

    @Query("SELECT COUNT(m) FROM MessageJpaEntity m WHERE m.conversationId = :conversationId")
    long countByConversationId(@Param("conversationId") Long conversationId);
}
