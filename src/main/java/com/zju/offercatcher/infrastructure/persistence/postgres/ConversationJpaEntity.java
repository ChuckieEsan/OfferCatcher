package com.zju.offercatcher.infrastructure.persistence.postgres;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Conversation JPA 实体
 */
@Entity
@Table(name = "conversations", indexes = {
        @Index(name = "idx_conversations_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_conversations_user_status", columnList = "user_id, status")
})
@Getter
@Setter
@NoArgsConstructor
public class ConversationJpaEntity {

    @Id
    @Column(name = "conversation_id")
    private Long conversationId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private com.zju.offercatcher.domain.shared.enums.ConversationStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static ConversationJpaEntity fromDomain(com.zju.offercatcher.domain.chat.aggregates.Conversation conversation) {
        ConversationJpaEntity entity = new ConversationJpaEntity();
        entity.setConversationId(conversation.getConversationId());
        entity.setUserId(conversation.getUserId());
        entity.setTitle(conversation.getTitle());
        entity.setStatus(conversation.getStatus());
        entity.setCreatedAt(conversation.getCreatedAt());
        entity.setUpdatedAt(conversation.getUpdatedAt());
        return entity;
    }

    public com.zju.offercatcher.domain.chat.aggregates.Conversation toDomainWithoutMessages() {
        return com.zju.offercatcher.domain.chat.aggregates.Conversation.rebuild(
                conversationId, userId, title, status,
                java.util.Collections.emptyList(),
                createdAt, updatedAt
        );
    }
}
