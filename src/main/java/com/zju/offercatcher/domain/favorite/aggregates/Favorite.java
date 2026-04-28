package com.zju.offercatcher.domain.favorite.aggregates;

import com.zju.offercatcher.domain.shared.exception.DomainException;
import com.zju.offercatcher.infrastructure.common.SnowflakeIdGenerator;

import java.time.LocalDateTime;

/**
 * 收藏聚合根
 *
 * 记录用户对题目的收藏行为，实现用户隔离。
 *
 * 设计原则：
 * - 通过 questionId 引用 Question 聚合（跨聚合引用）
 * - 不直接持有 Question 实体
 * - 收藏创建后不可修改（历史记录）
 * - 支持用户隔离（userId 字段）
 */
public class Favorite {

    private final Long favoriteId;
    private final String userId;
    private final Long questionId;
    private final LocalDateTime createdAt;

    /**
     * 创建收藏（工厂方法）
     *
     * @param userId 用户 ID
     * @param questionId 题目 ID (Snowflake BIGINT)
     * @return 新创建的 Favorite 聚合根
     */
    public static Favorite create(String userId, Long questionId) {
        validateUserId(userId);
        validateQuestionId(questionId);
        Long favoriteId = SnowflakeIdGenerator.generate();
        return new Favorite(favoriteId, userId, questionId, LocalDateTime.now());
    }

    /**
     * 创建收藏（指定 ID，用于重建）
     *
     * @param favoriteId 收藏 ID
     * @param userId 用户 ID
     * @param questionId 题目 ID (Snowflake BIGINT)
     * @return 新创建的 Favorite 聚合根
     */
    public static Favorite createWithId(Long favoriteId, String userId, Long questionId) {
        validateUserId(userId);
        validateQuestionId(questionId);
        if (favoriteId == null) {
            throw new DomainException("favoriteId 不能为空", "INVALID_FAVORITE_ID");
        }
        return new Favorite(favoriteId, userId, questionId, LocalDateTime.now());
    }

    /**
     * 从持久化存储重建收藏（用于 Repository 实现）
     */
    public static Favorite rebuild(Long favoriteId, String userId, Long questionId, LocalDateTime createdAt) {
        return new Favorite(favoriteId, userId, questionId, createdAt);
    }

    /**
     * 判断是否为用户所有
     */
    public boolean isOwnedBy(String requestingUserId) {
        return this.userId.equals(requestingUserId);
    }

    // ==================== Getter 方法 ====================

    public Long getFavoriteId() {
        return favoriteId;
    }

    public String getUserId() {
        return userId;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    // ==================== 构造函数 ====================

    private Favorite(Long favoriteId, String userId, Long questionId, LocalDateTime createdAt) {
        this.favoriteId = favoriteId;
        this.userId = userId;
        this.questionId = questionId;
        this.createdAt = createdAt;
    }

    // ==================== 校验方法 ====================

    private static void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new DomainException("userId 不能为空", "INVALID_USER_ID");
        }
    }

    private static void validateQuestionId(Long questionId) {
        if (questionId == null) {
            throw new DomainException("questionId 不能为空", "INVALID_QUESTION_ID");
        }
    }
}
