package com.zju.offercatcher.infrastructure.persistence.postgres;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Message JPA 实体
 */
@Entity
@Table(name = "messages", indexes = {
    @Index(name = "idx_messages_conversation_created", columnList = "conversation_id, created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class MessageJpaEntity {

    @Id
    @Column(name = "message_id")
    private Long messageId;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "role", nullable = false)
    @Enumerated(EnumType.STRING)
    private com.zju.offercatcher.domain.shared.enums.MessageRole role;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static MessageJpaEntity fromDomain(com.zju.offercatcher.domain.chat.entities.Message message, Long conversationId) {
        MessageJpaEntity entity = new MessageJpaEntity();
        entity.setMessageId(message.getMessageId());
        entity.setConversationId(conversationId);
        entity.setRole(message.getRole());
        entity.setContent(message.getContent());
        entity.setCreatedAt(message.getCreatedAt());
        return entity;
    }

    public com.zju.offercatcher.domain.chat.entities.Message toDomain() {
        return com.zju.offercatcher.domain.chat.entities.Message.rebuild(
            messageId, role, content, createdAt
        );
    }
}
