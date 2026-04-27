package com.zju.offercatcher.infrastructure.persistence.postgres;

import com.zju.offercatcher.domain.memory.entities.SessionSummary;
import com.zju.offercatcher.domain.shared.enums.MemoryLayer;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SessionSummary JPA 实体
 */
@Entity
@Table(name = "session_summaries", indexes = {
    @Index(name = "idx_summaries_user_created", columnList = "user_id, created_at"),
    @Index(name = "idx_summaries_user_layer", columnList = "user_id, memory_layer"),
    @Index(name = "idx_summaries_conversation", columnList = "conversation_id")
})
@Getter
@Setter
@NoArgsConstructor
public class SessionSummaryJpaEntity {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "summary", columnDefinition = "TEXT", nullable = false)
    private String summary;

    @Column(name = "embedding", columnDefinition = "TEXT")
    @Convert(converter = FloatArrayConverter.class)
    private float[] embedding;

    @Column(name = "importance_score")
    private double importanceScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "topics", columnDefinition = "jsonb")
    private List<String> topics;

    @Column(name = "memory_layer", nullable = false)
    @Enumerated(EnumType.STRING)
    private MemoryLayer memoryLayer;

    @Column(name = "access_count")
    private int accessCount;

    @Column(name = "feedback_score")
    private int feedbackScore;

    @Column(name = "last_accessed")
    private LocalDateTime lastAccessed;

    @Column(name = "decay_factor")
    private double decayFactor;

    @Column(name = "marked_for_deletion")
    private boolean markedForDeletion;

    @Column(name = "message_cursor")
    private Long messageCursor;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static SessionSummaryJpaEntity fromDomain(SessionSummary summary) {
        SessionSummaryJpaEntity entity = new SessionSummaryJpaEntity();
        entity.setId(summary.getId());
        entity.setConversationId(summary.getConversationId());
        entity.setUserId(summary.getUserId());
        entity.setSummary(summary.getSummary());
        entity.setEmbedding(summary.getEmbedding());
        entity.setImportanceScore(summary.getImportanceScore());
        entity.setTopics(new java.util.ArrayList<>(summary.getTopics()));
        entity.setMemoryLayer(summary.getMemoryLayer());
        entity.setAccessCount(summary.getAccessCount());
        entity.setFeedbackScore(summary.getFeedbackScore());
        entity.setLastAccessed(summary.getLastAccessed());
        entity.setDecayFactor(summary.getDecayFactor());
        entity.setMarkedForDeletion(summary.isMarkedForDeletion());
        entity.setMessageCursor(summary.getMessageCursor());
        entity.setCreatedAt(summary.getCreatedAt());
        return entity;
    }

    public SessionSummary toDomain() {
        return SessionSummary.rebuild(
            id, conversationId, userId, summary, embedding, importanceScore,
            topics, memoryLayer, accessCount, feedbackScore, lastAccessed,
            decayFactor, markedForDeletion, messageCursor, createdAt
        );
    }
}
