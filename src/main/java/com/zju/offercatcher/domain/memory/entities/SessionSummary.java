package com.zju.offercatcher.domain.memory.entities;

import com.zju.offercatcher.domain.shared.enums.MemoryLayer;
import com.zju.offercatcher.domain.shared.exception.DomainException;
import com.zju.offercatcher.infrastructure.common.SnowflakeIdGenerator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 会话摘要实体
 *
 * SessionSummary 是对话产生的记忆摘要，用于语义检索历史。
 * 一个 conversation 可以有多条 summary（每轮有价值的对话都可能产生一条）。
 *
 * 设计原则：
 * - 包含 embedding 向量（用于 Qdrant 检索）
 * - 支持衰减机制（STM 衰减，LTM 不衰减）
 * - 记录访问次数和反馈（用于重要性计算）
 * - 用户隔离（userId 字段）
 */
public class SessionSummary {

    private final Long id;
    private final Long conversationId;
    private final String userId;
    private String summary;
    private float[] embedding;
    private double importanceScore;
    private List<String> topics;
    private MemoryLayer memoryLayer;
    private int accessCount;
    private int feedbackScore;
    private LocalDateTime lastAccessed;
    private double decayFactor;
    private boolean markedForDeletion;
    private Long messageCursor;
    private LocalDateTime createdAt;

    /**
     * 创建会话摘要（工厂方法）
     */
    public static SessionSummary create(Long conversationId, String userId, String summary) {
        validateConversationId(conversationId);
        validateUserId(userId);
        validateSummary(summary);
        Long id = SnowflakeIdGenerator.generate();
        return new SessionSummary(id, conversationId, userId, summary, null,
            0.5, new ArrayList<>(), MemoryLayer.STM, 0, 0, null,
            1.0, false, null, LocalDateTime.now());
    }

    /**
     * 创建带向量的会话摘要
     */
    public static SessionSummary createWithEmbedding(Long conversationId, String userId,
                                                       String summary, float[] embedding,
                                                       double importanceScore, List<String> topics) {
        validateConversationId(conversationId);
        validateUserId(userId);
        validateSummary(summary);
        Long id = SnowflakeIdGenerator.generate();
        return new SessionSummary(id, conversationId, userId, summary, embedding,
            importanceScore, topics != null ? new ArrayList<>(topics) : new ArrayList<>(),
            MemoryLayer.STM, 0, 0, null, 1.0, false, null, LocalDateTime.now());
    }

    /**
     * 从持久化存储重建
     */
    public static SessionSummary rebuild(Long id, Long conversationId, String userId,
                                          String summary, float[] embedding, double importanceScore,
                                          List<String> topics, MemoryLayer memoryLayer,
                                          int accessCount, int feedbackScore, LocalDateTime lastAccessed,
                                          double decayFactor, boolean markedForDeletion,
                                          Long messageCursor, LocalDateTime createdAt) {
        return new SessionSummary(id, conversationId, userId, summary, embedding,
            importanceScore, topics != null ? new ArrayList<>(topics) : new ArrayList<>(),
            memoryLayer, accessCount, feedbackScore, lastAccessed, decayFactor,
            markedForDeletion, messageCursor, createdAt);
    }

    // ==================== 业务方法 ====================

    public void recordAccess() {
        this.accessCount++;
        this.lastAccessed = LocalDateTime.now();
    }

    public void addFeedback(boolean isPositive) {
        if (isPositive) {
            this.feedbackScore++;
            this.importanceScore = Math.min(this.importanceScore + 0.1, 1.0);
        } else {
            this.feedbackScore--;
            this.importanceScore = Math.max(this.importanceScore - 0.2, 0.0);
        }
    }

    public void upgradeToLtm() {
        this.memoryLayer = MemoryLayer.LTM;
        this.importanceScore = Math.max(this.importanceScore, 0.7);
        this.decayFactor = 1.0;
    }

    public void applyDecay(double decayRate) {
        if (memoryLayer == MemoryLayer.STM) {
            this.decayFactor *= (1 - decayRate);
            if (this.decayFactor < 0.1) {
                this.markedForDeletion = true;
            }
        }
    }

    public void updateEmbedding(float[] embedding) {
        this.embedding = embedding;
    }

    public void addTopic(String topic) {
        if (topic != null && !topic.isBlank() && !this.topics.contains(topic)) {
            this.topics.add(topic);
        }
    }

    public boolean isOwnedBy(String requestingUserId) {
        return this.userId.equals(requestingUserId);
    }

    public boolean isLongTerm() {
        return memoryLayer == MemoryLayer.LTM;
    }

    public boolean isShortTerm() {
        return memoryLayer == MemoryLayer.STM;
    }

    // ==================== Getter 方法 ====================

    public Long getId() {
        return id;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public String getUserId() {
        return userId;
    }

    public String getSummary() {
        return summary;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public double getImportanceScore() {
        return importanceScore;
    }

    public List<String> getTopics() {
        return Collections.unmodifiableList(topics);
    }

    public MemoryLayer getMemoryLayer() {
        return memoryLayer;
    }

    public int getAccessCount() {
        return accessCount;
    }

    public int getFeedbackScore() {
        return feedbackScore;
    }

    public LocalDateTime getLastAccessed() {
        return lastAccessed;
    }

    public double getDecayFactor() {
        return decayFactor;
    }

    public boolean isMarkedForDeletion() {
        return markedForDeletion;
    }

    public Long getMessageCursor() {
        return messageCursor;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    // ==================== 构造函数 ====================

    private SessionSummary(Long id, Long conversationId, String userId, String summary,
                            float[] embedding, double importanceScore, List<String> topics,
                            MemoryLayer memoryLayer, int accessCount, int feedbackScore,
                            LocalDateTime lastAccessed, double decayFactor, boolean markedForDeletion,
                            Long messageCursor, LocalDateTime createdAt) {
        this.id = id;
        this.conversationId = conversationId;
        this.userId = userId;
        this.summary = summary;
        this.embedding = embedding;
        this.importanceScore = importanceScore;
        this.topics = topics;
        this.memoryLayer = memoryLayer;
        this.accessCount = accessCount;
        this.feedbackScore = feedbackScore;
        this.lastAccessed = lastAccessed;
        this.decayFactor = decayFactor;
        this.markedForDeletion = markedForDeletion;
        this.messageCursor = messageCursor;
        this.createdAt = createdAt;
    }

    // ==================== 校验方法 ====================

    private static void validateConversationId(Long conversationId) {
        if (conversationId == null) {
            throw new DomainException("conversationId 不能为空", "INVALID_CONVERSATION_ID");
        }
    }

    private static void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new DomainException("userId 不能为空", "INVALID_USER_ID");
        }
    }

    private static void validateSummary(String summary) {
        if (summary == null || summary.isBlank()) {
            throw new DomainException("summary 不能为空", "INVALID_SUMMARY");
        }
    }
}
